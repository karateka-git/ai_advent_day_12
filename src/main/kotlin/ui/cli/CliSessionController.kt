package ui.cli

import agent.capability.capability
import agent.core.Agent
import agent.lifecycle.AgentLifecycleListener
import agent.memory.layer.MemoryLayerCategories
import agent.memory.model.ManagedMemoryNoteEdit
import agent.memory.model.MemoryLayer
import agent.memory.model.PendingMemoryEdit
import agent.memory.strategy.MemoryStrategyOption
import agent.memory.strategy.MemoryStrategyType
import agent.memory.strategy.branching.BranchingCapability
import app.output.AppEvent
import app.output.AppEventSink
import app.output.HelpCommandDescriptor
import app.output.HelpCommandGroup
import java.net.http.HttpClient
import java.util.Properties
import llm.core.LanguageModel
import llm.core.LanguageModelFactory
import llm.core.model.ChatRole
import llm.core.model.LanguageModelOption

/**
 * Исполняет CLI-команды и обычные пользовательские prompt'ы в рамках одной сессии.
 */
class CliSessionController(
    initialState: CliSessionState,
    private val config: Properties,
    private val httpClient: HttpClient,
    private val lifecycleListener: AgentLifecycleListener,
    private val appEventSink: AppEventSink,
    private val commandParser: CliCommandParser = CliCommandParser(),
    private val createLanguageModel: (String, Properties, HttpClient) -> LanguageModel,
    private val availableModelsProvider: (Properties) -> List<LanguageModelOption> = LanguageModelFactory::availableModels,
    private val createAgent: (LanguageModel, AgentLifecycleListener, MemoryStrategyType) -> Agent<String>,
    private val selectMemoryStrategy: () -> MemoryStrategyOption,
    private val warmUpLanguageModel: (LanguageModel, AgentLifecycleListener) -> Unit
) {
    /**
     * Текущее состояние CLI-сессии, включая активного агента и выбранную модель.
     */
    var state: CliSessionState = initialState
        private set

    private var noteDraft: MemoryNoteDraft? = null

    /**
     * Обрабатывает одну строку пользовательского ввода.
     *
     * @param input сырой ввод пользователя из CLI.
     * @return результат шага сессии: продолжать работу или завершать её.
     */
    fun handle(input: String): CliSessionControllerResult {
        val activeDraft = noteDraft
        if (activeDraft != null) {
            return handleDraftInput(activeDraft, input)
        }

        return when (val command = commandParser.parse(input)) {
            CliCommand.Empty -> CliSessionControllerResult.Continue
            CliCommand.CancelDraft -> {
                appEventSink.emit(AppEvent.CommandCompleted("Нет активного пошагового добавления заметки."))
                CliSessionControllerResult.Continue
            }
            CliCommand.ShowHelp -> {
                appEventSink.emit(
                    AppEvent.CommandsAvailable(
                        title = "Доступные команды",
                        groups = GeneralCliCatalog.helpGroups
                    )
                )
                CliSessionControllerResult.Continue
            }

            CliCommand.Exit -> {
                appEventSink.emit(AppEvent.SessionFinished)
                CliSessionControllerResult.ExitRequested
            }

            is CliCommand.InvalidCommand -> {
                appEventSink.emit(AppEvent.RequestFailed(formatInvalidCommandReason(command.reason)))
                CliSessionControllerResult.Continue
            }

            CliCommand.Clear -> {
                state.agent.clearContext()
                appEventSink.emit(AppEvent.ContextCleared)
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowModels -> {
                appEventSink.emit(
                    AppEvent.ModelsAvailable(
                        options = availableModelsProvider(config),
                        currentModelId = state.modelId
                    )
                )
                CliSessionControllerResult.Continue
            }

            is CliCommand.ShowMemory -> {
                appEventSink.emit(
                    AppEvent.MemoryStateAvailable(
                        snapshot = state.agent.inspectMemory(),
                        selectedLayer = command.layer
                    )
                )
                CliSessionControllerResult.Continue
            }

            is CliCommand.ShowMemoryCategories -> {
                appEventSink.emit(
                    AppEvent.CommandsAvailable(
                        title = "Категории памяти",
                        groups = memoryCategoryGroups(command.layer)
                    )
                )
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowPendingMemory -> {
                appEventSink.emit(AppEvent.PendingMemoryAvailable(pending = state.agent.inspectPendingMemory()))
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowPendingMemoryCommands -> {
                appEventSink.emit(
                    AppEvent.PendingMemoryCommandsAvailable(
                        commands = PendingMemoryCliCatalog.helpCommands
                    )
                )
                CliSessionControllerResult.Continue
            }

            is CliCommand.ApprovePendingMemory -> {
                runCatching { state.agent.approvePendingMemory(command.ids) }
                    .onSuccess { result ->
                        appEventSink.emit(
                            AppEvent.PendingMemoryActionCompleted(
                                message =
                                    if (command.ids.isEmpty()) {
                                        "Подтверждены все pending-кандидаты: ${result.affectedIds.size}"
                                    } else {
                                        "Подтверждены pending-кандидаты: ${result.affectedIds.joinToString(", ")}"
                                    },
                                pending = result.pendingState
                            )
                        )
                    }
                    .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
                CliSessionControllerResult.Continue
            }

            is CliCommand.RejectPendingMemory -> {
                runCatching { state.agent.rejectPendingMemory(command.ids) }
                    .onSuccess { result ->
                        appEventSink.emit(
                            AppEvent.PendingMemoryActionCompleted(
                                message =
                                    if (command.ids.isEmpty()) {
                                        "Отклонены все pending-кандидаты: ${result.affectedIds.size}"
                                    } else {
                                        "Отклонены pending-кандидаты: ${result.affectedIds.joinToString(", ")}"
                                    },
                                pending = result.pendingState
                            )
                        )
                    }
                    .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
                CliSessionControllerResult.Continue
            }

            is CliCommand.EditPendingMemory -> {
                runCatching {
                    state.agent.editPendingMemory(command.id, parsePendingEdit(command.field, command.value))
                }.onSuccess { updatedPending ->
                    appEventSink.emit(
                        AppEvent.PendingMemoryActionCompleted(
                            message = "Pending-кандидат ${command.id} обновлён.",
                            pending = updatedPending
                        )
                    )
                }.onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
                CliSessionControllerResult.Continue
            }

            CliCommand.StartMemoryNoteDraft -> {
                noteDraft = MemoryNoteDraft.Memory()
                appEventSink.emit(
                    AppEvent.CommandCompleted(
                        "Пошаговое добавление заметки запущено. Выбери слой: working или long. Для отмены введи ${CliCommands.CANCEL}."
                    )
                )
                CliSessionControllerResult.Continue
            }

            is CliCommand.AddMemoryNote -> {
                runCatching { state.agent.addMemoryNote(command.layer, command.category, command.content) }
                    .onSuccess { result ->
                        appEventSink.emit(AppEvent.CommandCompleted("Добавлена заметка ${result.note.id} в слой ${layerLabel(command.layer)}."))
                    }
                    .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
                CliSessionControllerResult.Continue
            }

            is CliCommand.EditMemoryNote -> {
                runCatching { state.agent.editMemoryNote(command.layer, command.id, command.edit) }
                    .onSuccess { result ->
                        appEventSink.emit(AppEvent.CommandCompleted("Заметка ${result.note.id} в слое ${layerLabel(command.layer)} обновлена."))
                    }
                    .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
                CliSessionControllerResult.Continue
            }

            is CliCommand.DeleteMemoryNote -> {
                runCatching { state.agent.deleteMemoryNote(command.layer, command.id) }
                    .onSuccess { result ->
                        appEventSink.emit(AppEvent.CommandCompleted("Заметка ${result.note.id} удалена из слоя ${layerLabel(command.layer)}."))
                    }
                    .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowUsers -> {
                appEventSink.emit(
                    AppEvent.UsersAvailable(
                        users = state.agent.users(),
                        activeUserId = state.agent.activeUser().id
                    )
                )
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowActiveUser -> {
                val activeUser = state.agent.activeUser()
                appEventSink.emit(AppEvent.CommandCompleted("Активный пользователь: ${activeUser.displayName} (${activeUser.id})."))
                CliSessionControllerResult.Continue
            }

            is CliCommand.CreateUser -> {
                runCatching { state.agent.createUser(command.id, command.displayName) }
                    .onSuccess { user ->
                        appEventSink.emit(AppEvent.CommandCompleted("Пользователь ${user.displayName} (${user.id}) создан."))
                    }
                    .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
                CliSessionControllerResult.Continue
            }

            is CliCommand.SwitchUser -> {
                runCatching { state.agent.switchUser(command.id) }
                    .onSuccess { user ->
                        appEventSink.emit(AppEvent.CommandCompleted("Активный пользователь переключён на ${user.displayName} (${user.id})."))
                    }
                    .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowProfile -> {
                appEventSink.emit(
                    AppEvent.UserProfileAvailable(
                        user = state.agent.activeUser(),
                        notes = state.agent.inspectProfile()
                    )
                )
                CliSessionControllerResult.Continue
            }

            CliCommand.StartProfileNoteDraft -> {
                noteDraft = MemoryNoteDraft.Profile()
                appEventSink.emit(
                    AppEvent.CommandCompleted(
                        "Пошаговое добавление профильной заметки запущено для пользователя ${state.agent.activeUser().displayName}.\nВведи категорию из списка:\n${formattedCategoryList(MemoryLayer.LONG_TERM)}\nДля отмены введи ${CliCommands.CANCEL}."
                    )
                )
                CliSessionControllerResult.Continue
            }

            is CliCommand.AddProfileNote -> {
                runCatching { state.agent.addProfileNote(command.category, command.content) }
                    .onSuccess { result ->
                        appEventSink.emit(AppEvent.CommandCompleted("Профильная заметка ${result.note.id} добавлена."))
                    }
                    .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
                CliSessionControllerResult.Continue
            }

            is CliCommand.EditProfileNote -> {
                runCatching { state.agent.editProfileNote(command.id, command.edit) }
                    .onSuccess { result ->
                        appEventSink.emit(AppEvent.CommandCompleted("Профильная заметка ${result.note.id} обновлена."))
                    }
                    .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
                CliSessionControllerResult.Continue
            }

            is CliCommand.DeleteProfileNote -> {
                runCatching { state.agent.deleteProfileNote(command.id) }
                    .onSuccess { result ->
                        appEventSink.emit(AppEvent.CommandCompleted("Профильная заметка ${result.note.id} удалена."))
                    }
                    .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
                CliSessionControllerResult.Continue
            }

            is CliCommand.CreateCheckpoint -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(AppEvent.CheckpointCreated(branchingCapability.createCheckpoint(command.name)))
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowBranches -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(AppEvent.BranchStatusAvailable(branchingCapability.branchStatus()))
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.CreateBranch -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(AppEvent.BranchCreated(branchingCapability.createBranch(command.name)))
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.SwitchBranch -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(AppEvent.BranchSwitched(branchingCapability.switchBranch(command.name)))
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.SwitchModel -> {
                switchModel(command.modelId)
                CliSessionControllerResult.Continue
            }

            is CliCommand.UserPrompt -> {
                try {
                    val pendingBefore = state.agent.inspectPendingMemory().candidates.size
                    appEventSink.emit(AppEvent.TokenPreviewAvailable(state.agent.previewTokenStats(command.value)))
                    appEventSink.emit(AppEvent.ModelRequestStarted)
                    val response = try {
                        state.agent.ask(command.value)
                    } finally {
                        appEventSink.emit(AppEvent.ModelRequestFinished)
                    }
                    appEventSink.emit(
                        AppEvent.AssistantResponseAvailable(
                            role = ChatRole.ASSISTANT,
                            content = response.content,
                            tokenStats = response.tokenStats
                        )
                    )
                    val pendingAfter = state.agent.inspectPendingMemory()
                    if (pendingAfter.candidates.size > pendingBefore) {
                        appEventSink.emit(
                            AppEvent.PendingMemoryAvailable(
                                pending = pendingAfter,
                                reason = "Есть кандидаты на сохранение в память. Посмотри их командой ${PendingMemoryCliCatalog.SHOW}."
                            )
                        )
                    }
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }
        }
    }

    private fun switchModel(requestedModelId: String) {
        try {
            val languageModel = createLanguageModel(requestedModelId, config, httpClient)
            warmUpLanguageModel(languageModel, lifecycleListener)
            val selectedStrategy = selectMemoryStrategy()
            val agent = createAgent(languageModel, lifecycleListener, selectedStrategy.type)
            state = state.copy(
                modelId = requestedModelId,
                languageModel = languageModel,
                agent = agent,
                memoryStrategyOption = selectedStrategy
            )
            appEventSink.emit(AppEvent.ModelChanged)
            appEventSink.emit(
                AppEvent.AgentInfoAvailable(
                    info = agent.info,
                    strategy = selectedStrategy
                )
            )
        } catch (error: Exception) {
            appEventSink.emit(AppEvent.ModelSwitchFailed(error.message))
        }
    }

    private fun parsePendingEdit(field: String, value: String): PendingMemoryEdit =
        when (field.lowercase()) {
            "text" -> PendingMemoryEdit.UpdateText(value)
            "layer" -> PendingMemoryEdit.UpdateLayer(parseEditableLayer(value))
            "category" -> PendingMemoryEdit.UpdateCategory(value)
            else -> error("Поддерживаются только поля text, layer и category.")
        }

    private fun handleDraftInput(
        draft: MemoryNoteDraft,
        input: String
    ): CliSessionControllerResult {
        val normalizedInput = input.trim()
        if (normalizedInput.equals(CliCommands.CANCEL, ignoreCase = true) || normalizedInput.equals("cancel", ignoreCase = true)) {
            noteDraft = null
            appEventSink.emit(AppEvent.CommandCompleted("Пошаговое добавление заметки отменено."))
            return CliSessionControllerResult.Continue
        }
        if (normalizedInput.startsWith(CliCommands.PREFIX)) {
            appEventSink.emit(
                AppEvent.RequestFailed(
                    "Сейчас активно пошаговое добавление заметки. Заверши его или отмени командой ${CliCommands.CANCEL}."
                )
            )
            return CliSessionControllerResult.Continue
        }

        return when (draft) {
            is MemoryNoteDraft.Memory -> handleMemoryDraftInput(draft, normalizedInput)
            is MemoryNoteDraft.Profile -> handleProfileDraftInput(draft, normalizedInput)
        }
    }

    private fun handleMemoryDraftInput(
        draft: MemoryNoteDraft.Memory,
        input: String
    ): CliSessionControllerResult {
        if (draft.layer == null) {
            return runCatching { parseEditableLayer(input) }
                .onSuccess { layer ->
                    noteDraft = draft.copy(layer = layer)
                    appEventSink.emit(
                        AppEvent.CommandCompleted(
                            "Слой выбран: ${layerCommandValue(layer)}.\nВведи категорию из списка:\n${formattedCategoryList(layer)}"
                        )
                    )
                }
                .onFailure {
                    appEventSink.emit(AppEvent.RequestFailed("Нужно выбрать слой working или long. Для отмены введи ${CliCommands.CANCEL}."))
                }
                .let { CliSessionControllerResult.Continue }
        }

        if (draft.category == null) {
            val category = input.trim()
            val allowedCategories = state.agent.memoryCategories(draft.layer)
            return if (allowedCategories.contains(category)) {
                noteDraft = draft.copy(category = category)
                appEventSink.emit(AppEvent.CommandCompleted("Категория выбрана: $category. Теперь введи текст заметки."))
                CliSessionControllerResult.Continue
            } else {
                appEventSink.emit(
                    AppEvent.RequestFailed(
                        "Категория '$category' недоступна для слоя ${layerCommandValue(draft.layer)}.\nДоступные категории:\n${formattedCategoryList(draft.layer)}"
                    )
                )
                CliSessionControllerResult.Continue
            }
        }

        if (draft.content == null) {
            if (input.isBlank()) {
                appEventSink.emit(AppEvent.RequestFailed("Текст заметки не должен быть пустым."))
                return CliSessionControllerResult.Continue
            }
            noteDraft = draft.copy(content = input)
            appEventSink.emit(
                AppEvent.CommandCompleted(
                    "Черновик заметки готов.\nСлой: ${layerCommandValue(draft.layer)}\nКатегория: ${draft.category}\nТекст: $input\nВведи confirm для сохранения или ${CliCommands.CANCEL} для отмены."
                )
            )
            return CliSessionControllerResult.Continue
        }

        return handleMemoryDraftConfirmation(draft, input)
    }

    private fun handleProfileDraftInput(
        draft: MemoryNoteDraft.Profile,
        input: String
    ): CliSessionControllerResult {
        if (draft.category == null) {
            val category = input.trim()
            val allowedCategories = state.agent.memoryCategories(MemoryLayer.LONG_TERM)
            return if (allowedCategories.contains(category)) {
                noteDraft = draft.copy(category = category)
                appEventSink.emit(AppEvent.CommandCompleted("Категория выбрана: $category. Теперь введи текст профильной заметки."))
                CliSessionControllerResult.Continue
            } else {
                appEventSink.emit(
                    AppEvent.RequestFailed(
                        "Категория '$category' недоступна для профиля.\nДоступные категории:\n${formattedCategoryList(MemoryLayer.LONG_TERM)}"
                    )
                )
                CliSessionControllerResult.Continue
            }
        }

        if (draft.content == null) {
            if (input.isBlank()) {
                appEventSink.emit(AppEvent.RequestFailed("Текст заметки не должен быть пустым."))
                return CliSessionControllerResult.Continue
            }
            noteDraft = draft.copy(content = input)
            val activeUser = state.agent.activeUser()
            appEventSink.emit(
                AppEvent.CommandCompleted(
                    "Черновик профильной заметки готов.\nПользователь: ${activeUser.displayName} (${activeUser.id})\nКатегория: ${draft.category}\nТекст: $input\nВведи confirm для сохранения или ${CliCommands.CANCEL} для отмены."
                )
            )
            return CliSessionControllerResult.Continue
        }

        return handleProfileDraftConfirmation(draft, input)
    }

    private fun handleMemoryDraftConfirmation(
        draft: MemoryNoteDraft.Memory,
        input: String
    ): CliSessionControllerResult {
        if (!input.equals("confirm", ignoreCase = true)) {
            appEventSink.emit(AppEvent.RequestFailed("Для сохранения введи confirm или ${CliCommands.CANCEL} для отмены."))
            return CliSessionControllerResult.Continue
        }

        runCatching { state.agent.addMemoryNote(draft.layer!!, draft.category!!, draft.content!!) }
            .onSuccess { result ->
                noteDraft = null
                appEventSink.emit(AppEvent.CommandCompleted("Добавлена заметка ${result.note.id} в слой ${layerLabel(draft.layer!!)}."))
            }
            .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
        return CliSessionControllerResult.Continue
    }

    private fun handleProfileDraftConfirmation(
        draft: MemoryNoteDraft.Profile,
        input: String
    ): CliSessionControllerResult {
        if (!input.equals("confirm", ignoreCase = true)) {
            appEventSink.emit(AppEvent.RequestFailed("Для сохранения введи confirm или ${CliCommands.CANCEL} для отмены."))
            return CliSessionControllerResult.Continue
        }

        runCatching { state.agent.addProfileNote(draft.category!!, draft.content!!) }
            .onSuccess { result ->
                noteDraft = null
                appEventSink.emit(AppEvent.CommandCompleted("Профильная заметка ${result.note.id} добавлена."))
            }
            .onFailure { appEventSink.emit(AppEvent.RequestFailed(it.message)) }
        return CliSessionControllerResult.Continue
    }

    private fun parseEditableLayer(value: String): MemoryLayer =
        when (value.trim().lowercase()) {
            "working", "work", "рабочая" -> MemoryLayer.WORKING
            "long", "long-term", "долговременная" -> MemoryLayer.LONG_TERM
            else -> error("Для pending-кандидата поддерживаются только слои working и long.")
        }

    /**
     * Формирует многострочный список допустимых категорий слоя с краткими описаниями.
     *
     * @param layer слой памяти, для которого нужно показать доступные категории.
     * @return строки списка в формате `- id: description`.
     */
    private fun formattedCategoryList(layer: MemoryLayer): String =
        MemoryLayerCategories.definitionsFor(layer).joinToString(separator = "\n") { definition ->
            "- ${definition.id}: ${definition.description}"
        }

    private fun memoryCategoryGroups(layer: MemoryLayer?): List<HelpCommandGroup> {
        val layers = layer?.let(::listOf) ?: listOf(MemoryLayer.WORKING, MemoryLayer.LONG_TERM)
        return layers.map { selectedLayer ->
            HelpCommandGroup(
                title = "Категории слоя ${layerLabel(selectedLayer)}",
                commands = state.agent.memoryCategories(selectedLayer).map { category ->
                    HelpCommandDescriptor("/memory add ${layerCommandValue(selectedLayer)} $category <текст>", category)
                }
            )
        }
    }

    private fun layerCommandValue(layer: MemoryLayer): String =
        when (layer) {
            MemoryLayer.WORKING -> "working"
            MemoryLayer.LONG_TERM -> "long"
            MemoryLayer.SHORT_TERM -> "short"
        }

    private fun layerLabel(layer: MemoryLayer): String =
        when (layer) {
            MemoryLayer.WORKING -> "рабочая память"
            MemoryLayer.LONG_TERM -> "долговременная память"
            MemoryLayer.SHORT_TERM -> "краткосрочная память"
        }

    private fun formatInvalidCommandReason(reason: InvalidCliCommandReason): String =
        when (reason) {
            is InvalidCliCommandReason.UnknownCommand ->
                "Неизвестная команда: ${reason.command}. Для списка команд используйте ${CliCommands.HELP}."
            is InvalidCliCommandReason.Usage -> "Используйте: ${reason.usage}."
            is InvalidCliCommandReason.PendingEditUnsupportedField -> {
                val supportedFields = reason.allowedFields.joinToString(", ")
                "Поле редактирования должно быть одним из: $supportedFields."
            }
        }
}

/**
 * Результат обработки одного шага CLI-сессии.
 */
sealed interface CliSessionControllerResult {
    data object Continue : CliSessionControllerResult
    data object ExitRequested : CliSessionControllerResult
}

private sealed interface MemoryNoteDraft {
    data class Memory(
        val layer: MemoryLayer? = null,
        val category: String? = null,
        val content: String? = null
    ) : MemoryNoteDraft

    data class Profile(
        val category: String? = null,
        val content: String? = null
    ) : MemoryNoteDraft
}
