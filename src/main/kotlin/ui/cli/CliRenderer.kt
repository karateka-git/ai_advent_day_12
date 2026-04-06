package ui.cli

import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
import agent.memory.model.MemorySnapshot
import agent.memory.model.PendingMemoryState
import agent.memory.strategy.MemoryStrategyOption
import app.output.AppEvent
import app.output.AppEventSink
import app.output.HelpCommandDescriptor
import app.output.HelpCommandGroup
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * CLI-реализация presentation-слоя, которая отвечает за весь пользовательский вывод.
 */
class CliRenderer(
    private val loadingIndicator: LoadingIndicator = LoadingIndicator(),
    private val tokenStatsFormatter: ConsoleTokenStatsFormatter = ConsoleTokenStatsFormatter()
) : AppEventSink {
    override fun emit(event: AppEvent) {
        when (event) {
            AppEvent.SessionStarted -> {
                renderBorderedBlock(
                    title = "Старт",
                    lines = listOf(
                        "Чат готов.",
                        "Для списка команд введи '${CliCommands.HELP}'."
                    )
                )
            }

            is AppEvent.CommandsAvailable -> renderCommandsBlock(event.title, event.groups)

            is AppEvent.MemoryStrategySelectionRequested -> {
                renderBorderedBlock(
                    title = "Выбор стратегии памяти",
                    lines = buildList {
                        add("Выберите стратегию памяти для нового агента:")
                        event.options.forEachIndexed { index, option ->
                            add("${index + 1}. ${option.displayName} - ${option.description}")
                        }
                    }
                )
            }

            is AppEvent.MemoryStrategySelectionPromptRequested -> {
                print("Введите номер стратегии [1-${event.optionsCount}]: ")
            }

            is AppEvent.MemoryStrategySelected -> {
                renderBorderedBlock(
                    title = "Стратегия выбрана",
                    lines = listOf(
                        "Стратегия: ${event.option.displayName}",
                        "Как работает: ${event.option.description}"
                    )
                )
                printAdditionalCommands(event.option, header = "Дополнительные команды стратегии")
            }

            AppEvent.MemoryStrategySelectionRejected -> {
                printCommandResult("Некорректный выбор. Попробуйте ещё раз.")
            }

            is AppEvent.AgentInfoAvailable -> {
                renderBorderedBlock(
                    title = "Агент",
                    lines = listOf(
                        "Агент: ${event.info.name}",
                        "Описание: ${event.info.description}",
                        "Модель: ${event.info.model}",
                        "Стратегия памяти: ${event.strategy.displayName}",
                        "Поведение стратегии: ${event.strategy.description}"
                    )
                )
                printAdditionalCommands(event.strategy, header = "Команды стратегии")
            }

            is AppEvent.ModelsAvailable -> {
                renderBorderedBlock(
                    title = "Доступные модели",
                    lines = event.options.map { option ->
                        buildString {
                            val marker = if (option.id == event.currentModelId) "*" else " "
                            append(marker)
                            append(" ")
                            append(option.id)
                            append(" - ")
                            append(option.displayName)
                            if (!option.isConfigured) {
                                append(" (недоступна: ${option.unavailableReason})")
                            }
                        }
                    }
                )
            }

            is AppEvent.MemoryStateAvailable -> renderMemoryState(event.snapshot, event.selectedLayer)

            is AppEvent.UsersAvailable -> {
                renderBorderedBlock(
                    title = "Пользователи",
                    lines = event.users.map { user ->
                        val marker = if (user.id == event.activeUserId) "*" else " "
                        "$marker ${user.id} (${user.displayName})"
                    }
                )
            }

            is AppEvent.UserProfileAvailable -> {
                val lines = buildList {
                    add("Активный пользователь: ${event.user.displayName} (${event.user.id})")
                    if (event.notes.isEmpty()) {
                        add("(пусто)")
                    } else {
                        event.notes.forEach { add(formatManagedNote(it)) }
                    }
                }
                renderBorderedBlock(
                    title = "Профиль пользователя",
                    lines = lines
                )
            }

            is AppEvent.PendingMemoryAvailable -> {
                if (event.reason != null) {
                    printCommandResult(event.reason)
                } else {
                    renderPendingMemory(event.pending)
                }
            }

            is AppEvent.PendingMemoryActionCompleted -> printCommandResult(event.message)

            is AppEvent.PendingMemoryCommandsAvailable -> {
                renderCommandsBlock(
                    title = "Команды для pending-памяти",
                    groups = listOf(
                        HelpCommandGroup(
                            title = "Pending-память",
                            commands = event.commands
                        )
                    )
                )
            }

            is AppEvent.CheckpointCreated -> {
                renderBorderedBlock(
                    title = "Checkpoint создан",
                    lines = listOf(
                        "Имя: ${event.info.name}",
                        "Исходная ветка: ${event.info.sourceBranchName}"
                    )
                )
            }

            is AppEvent.BranchCreated -> {
                renderBorderedBlock(
                    title = "Ветка создана",
                    lines = listOf(
                        "Имя: ${event.info.name}",
                        "Источник: ${event.info.sourceCheckpointName ?: "без checkpoint"}"
                    )
                )
            }

            is AppEvent.BranchSwitched -> {
                renderBorderedBlock(
                    title = "Переключение ветки",
                    lines = listOf("Активная ветка: ${event.info.name}")
                )
            }

            is AppEvent.BranchStatusAvailable -> {
                renderBorderedBlock(
                    title = "Статус веток",
                    lines = buildList {
                        add("Активная ветка: ${event.status.activeBranchName}")
                        add("Последний checkpoint: ${event.status.latestCheckpointName ?: "нет"}")
                        add("Ветки:")
                        event.status.branches.forEach { branch ->
                            val marker = if (branch.isActive) "*" else " "
                            add("$marker ${branch.name} (checkpoint: ${branch.sourceCheckpointName ?: "нет"})")
                        }
                    }
                )
            }

            is AppEvent.UserInputPrompt -> {
                println()
                println("┌─ ${event.role.displayName}")
                print("│ ")
            }

            is AppEvent.AssistantResponseAvailable -> {
                val lines = buildList {
                    addAll(splitRenderableLines(event.content))
                    tokenStatsFormatter.formatResponse(event.tokenStats)?.let { stats ->
                        add("")
                        addAll(stats.lines())
                    }
                }
                renderBorderedBlock(
                    title = event.role.displayName,
                    lines = lines
                )
            }

            is AppEvent.TokenPreviewAvailable -> {
                tokenStatsFormatter.formatPreview(event.tokenStats)?.let { preview ->
                    renderBorderedBlock(
                        title = "Оценка перед запросом",
                        lines = preview.lines().drop(1)
                    )
                }
            }

            AppEvent.ContextCleared -> printCommandResult("Контекст очищен. Системное сообщение сохранено.")
            AppEvent.ModelChanged -> printCommandResult("Текущая модель изменена.")
            is AppEvent.ModelSwitchFailed -> printCommandResult("Не удалось переключить модель: ${event.details}")
            is AppEvent.CommandCompleted -> printCommandResult(event.message)
            is AppEvent.RequestFailed -> printCommandResult("Не удалось выполнить запрос: ${event.details}")
            AppEvent.SessionFinished -> printCommandResult("Чат завершён.")
            AppEvent.ModelWarmupStarted -> loadingIndicator.start("Подготовка модели")
            AppEvent.ModelWarmupFinished -> loadingIndicator.stop()
            AppEvent.ModelRequestStarted -> loadingIndicator.start("Ассистент думает")
            AppEvent.ModelRequestFinished -> loadingIndicator.stop()
            AppEvent.ContextCompressionStarted -> loadingIndicator.start("Сжимаем контекст")

            is AppEvent.ContextCompressionFinished -> {
                loadingIndicator.stop()
                val message =
                    if (
                        event.stats.tokensBefore != null &&
                        event.stats.tokensAfter != null &&
                        event.stats.savedTokens != null
                    ) {
                        "Контекст сжат: ${event.stats.tokensBefore} -> ${event.stats.tokensAfter} токенов, экономия ${event.stats.savedTokens}"
                    } else {
                        "Контекст сжат."
                    }
                printCommandResult(message)
            }
        }
    }

    private fun renderMemoryState(snapshot: MemorySnapshot, selectedLayer: MemoryLayer?) {
        val lines = buildList {
            memoryLayers(selectedLayer).forEachIndexed { index, layer ->
                if (index > 0) {
                    add("")
                }
                add("Уровень памяти: ${layerTitle(layer)}")
                val layerLines = linesForLayer(snapshot, layer)
                if (layerLines.isEmpty()) {
                    add("(пусто)")
                } else {
                    layerLines.forEach { add("- $it") }
                }
            }
        }

        renderBorderedBlock(
            title = "Память",
            lines = lines
        )
    }

    private fun renderPendingMemory(pending: PendingMemoryState) {
        val lines = buildList {
            add("Кандидаты в память:")
            if (pending.candidates.isEmpty()) {
                add("(пусто)")
            } else {
                pending.candidates.forEach { candidate ->
                    add("${candidate.id} [${layerTitle(candidate.targetLayer)}] ${noteCategoryTitle(candidate.category)}: ${candidate.content}")
                }
            }
            add("")
            add("Справка по командам: ${PendingMemoryCliCatalog.INFO}")
        }

        renderBorderedBlock(
            title = "Pending-память",
            lines = lines
        )
    }

    private fun renderCommandsBlock(title: String, groups: List<HelpCommandGroup>) {
        val lines = buildList {
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    add("")
                }
                add(group.title)
                group.commands.forEach { descriptor ->
                    add("  ${descriptor.command}")
                    add("    ${descriptor.description}")
                }
            }
        }

        renderBorderedBlock(
            title = title,
            lines = lines
        )
    }

    private fun printCommandResult(message: String) {
        renderBorderedBlock(
            title = "Результат команды",
            lines = splitRenderableLines(message)
        )
    }

    private fun renderBorderedBlock(title: String, lines: List<String>) {
        println()
        println("┌─ $title")
        lines.forEach { line ->
            if (line.isEmpty()) {
                println("│")
            } else {
                println("│ $line")
            }
        }
        println("└────────────────")
        println()
    }

    private fun splitRenderableLines(content: String): List<String> =
        content.lines().ifEmpty { listOf(content) }

    private fun memoryLayers(selectedLayer: MemoryLayer?): List<MemoryLayer> =
        selectedLayer?.let(::listOf) ?: listOf(MemoryLayer.SHORT_TERM, MemoryLayer.WORKING, MemoryLayer.LONG_TERM)

    private fun layerTitle(layer: MemoryLayer): String =
        when (layer) {
            MemoryLayer.SHORT_TERM -> "краткосрочная"
            MemoryLayer.WORKING -> "рабочая"
            MemoryLayer.LONG_TERM -> "долговременная"
        }

    private fun linesForLayer(snapshot: MemorySnapshot, layer: MemoryLayer): List<String> =
        when (layer) {
            MemoryLayer.SHORT_TERM -> buildShortTermLines(snapshot)
            MemoryLayer.WORKING -> snapshot.state.working.notes.map(::formatManagedNote)
            MemoryLayer.LONG_TERM -> snapshot.state.longTerm.notes.map(::formatManagedNote)
        }

    private fun buildShortTermLines(snapshot: MemorySnapshot): List<String> {
        val strategyLine = "Используемая стратегия: ${snapshot.shortTermStrategyType.id}"
        val strategyStateLine = snapshot.state.shortTerm.strategyState?.let { "Состояние стратегии: ${it.strategyType.id}" }
        return listOfNotNull(strategyLine, strategyStateLine) +
            snapshot.state.shortTerm.derivedMessages
                .filterNot { it.role == ChatRole.SYSTEM }
                .map(::formatMessageLine)
    }

    private fun formatManagedNote(note: MemoryNote): String =
        buildString {
            append(note.id)
            append(" [")
            append(noteCategoryTitle(note.category))
            append("]")
            if (note.ownerType == MemoryOwnerType.USER && note.ownerId != null) {
                append(" [user:")
                append(note.ownerId)
                append("]")
            }
            append(": ")
            append(note.content)
        }

    private fun formatMessageLine(message: ChatMessage): String =
        buildString {
            append(messageRoleTitle(message))
            append(": ")
            append(formatMultilineContent(message.content))
        }

    private fun formatMultilineContent(content: String): String {
        val lines = content.lines()
        if (lines.size <= 1) {
            return content
        }

        return buildString {
            append(lines.first())
            lines.drop(1).forEach { line ->
                append("\n  ")
                append(line)
            }
        }
    }

    private fun messageRoleTitle(message: ChatMessage): String =
        when (message.role) {
            ChatRole.SYSTEM -> "система"
            ChatRole.USER -> "пользователь"
            ChatRole.ASSISTANT -> "ассистент"
        }

    private fun noteCategoryTitle(category: String): String =
        when (category) {
            "goal" -> "цель"
            "constraint" -> "ограничение"
            "deadline" -> "срок"
            "budget" -> "бюджет"
            "integration" -> "интеграция"
            "decision" -> "решение"
            "open_question" -> "открытый вопрос"
            "communication_style" -> "стиль общения"
            "persistent_preference" -> "постоянное предпочтение"
            "architectural_agreement" -> "архитектурная договорённость"
            "reusable_knowledge" -> "повторно полезное знание"
            else -> category
        }

    private fun printAdditionalCommands(
        option: MemoryStrategyOption,
        header: String
    ) {
        if (option.additionalCommands.isEmpty()) {
            return
        }

        renderBorderedBlock(
            title = header,
            lines = option.additionalCommands.map { "${it.command} - ${it.description}" }
        )
    }
}
