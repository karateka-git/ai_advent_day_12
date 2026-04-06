package agent.memory.core

import agent.capability.AgentCapability
import agent.core.AgentTokenStats
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.layer.DefaultMemoryConfirmationPolicy
import agent.memory.layer.DurableMemoryCandidateApplier
import agent.memory.layer.MemoryCandidateApplier
import agent.memory.layer.MemoryCandidateValidator
import agent.memory.layer.MemoryConfirmationPolicy
import agent.memory.layer.MemoryLayerAllocator
import agent.memory.layer.MemoryLayerCategories
import agent.memory.layer.MemoryLayerWritePolicy
import agent.memory.layer.NoOpMemoryLayerAllocator
import agent.memory.layer.RuleBasedMemoryNoteMergePolicy
import agent.memory.layer.UserMessageOnlyMemoryLayerWritePolicy
import agent.memory.model.LongTermMemory
import agent.memory.model.ManagedMemoryNoteEdit
import agent.memory.model.ManagedMemoryNoteResult
import agent.memory.model.MemoryCandidateDraft
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
import agent.memory.model.MemorySnapshot
import agent.memory.model.MemoryState
import agent.memory.model.PendingMemoryActionResult
import agent.memory.model.PendingMemoryCandidate
import agent.memory.model.PendingMemoryEdit
import agent.memory.model.PendingMemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.UserAccount
import agent.memory.model.WorkingMemory
import agent.memory.persistence.JsonMemoryStateRepository
import agent.memory.persistence.MemoryStateRepository
import agent.memory.prompt.DefaultMemoryContextService
import agent.memory.prompt.LayeredMemoryPromptAssembler
import agent.memory.prompt.MemoryContextService
import agent.memory.strategy.MemoryStrategyType
import agent.memory.strategy.branching.BranchCoordinator
import agent.memory.strategy.branching.BranchingMemoryCapabilityAdapter
import agent.memory.strategy.nocompression.NoCompressionMemoryStrategy
import java.nio.file.Path
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class DefaultMemoryManager(
    private val languageModel: LanguageModel,
    private val systemPrompt: String,
    private val memoryStateRepository: MemoryStateRepository = JsonMemoryStateRepository.forLanguageModel(languageModel),
    private val memoryStrategy: MemoryStrategy = NoCompressionMemoryStrategy(),
    private val lifecycleListener: AgentLifecycleListener = NoOpAgentLifecycleListener,
    private val branchCoordinator: BranchCoordinator = BranchCoordinator(),
    private val memoryLayerAllocator: MemoryLayerAllocator = NoOpMemoryLayerAllocator(),
    private val memoryLayerWritePolicy: MemoryLayerWritePolicy = UserMessageOnlyMemoryLayerWritePolicy(),
    private val candidateValidator: MemoryCandidateValidator = MemoryCandidateValidator(),
    private val confirmationPolicy: MemoryConfirmationPolicy = DefaultMemoryConfirmationPolicy(),
    private val candidateApplier: MemoryCandidateApplier =
        DurableMemoryCandidateApplier(RuleBasedMemoryNoteMergePolicy()),
    private val clearPolicy: MemoryClearPolicy = TaskScopedMemoryClearPolicy(),
    private val compressionObserver: MemoryCompressionObserver = SummaryBasedMemoryCompressionObserver(),
    private val contextService: MemoryContextService = DefaultMemoryContextService(
        memoryStrategyProvider = { memoryStrategy },
        promptAssembler = LayeredMemoryPromptAssembler()
    )
) : MemoryManager {
    private var state = loadMemoryState()

    private val branchingCapability = BranchingMemoryCapabilityAdapter(
        branchCoordinator = branchCoordinator,
        enabled = { memoryStrategy.type == MemoryStrategyType.BRANCHING },
        stateProvider = { state },
        stateUpdater = { updatedState -> state = updatedState },
        persistState = ::saveState
    )

    override fun currentConversation(): List<ChatMessage> = state.shortTerm.rawMessages.toList()

    override fun previewTokenStats(userPrompt: String): AgentTokenStats {
        val effectiveConversation = contextService.effectiveConversation(systemPrompt, state)
        val historyTokens = languageModel.tokenCounter?.countMessages(effectiveConversation)
        val userPromptTokens = languageModel.tokenCounter?.countText(userPrompt)
        val promptTokensLocal = contextService.countPromptTokens(
            languageModel = languageModel,
            systemPrompt = systemPrompt,
            state = previewStateForUserPrompt(userPrompt)
        )

        return AgentTokenStats(
            historyTokens = historyTokens,
            promptTokensLocal = promptTokensLocal,
            userPromptTokens = userPromptTokens
        )
    }

    override fun appendUserMessage(userPrompt: String): List<ChatMessage> {
        val userMessage = ChatMessage(role = ChatRole.USER, content = userPrompt)
        val stateWithMessage = appendShortTermMessage(state, userMessage)
        val stateWithCandidates = processCandidatesIfAllowed(stateWithMessage, userMessage)
        state = refreshState(stateWithCandidates, notifyCompression = true)
        saveState()
        return contextService.effectiveConversation(systemPrompt, state)
    }

    override fun appendAssistantMessage(content: String) {
        val assistantMessage = ChatMessage(role = ChatRole.ASSISTANT, content = content)
        val stateWithMessage = appendShortTermMessage(state, assistantMessage)
        val stateWithCandidates = processCandidatesIfAllowed(stateWithMessage, assistantMessage)
        state =
            if (memoryStrategy.type == MemoryStrategyType.BRANCHING) {
                memoryStrategy.refreshState(stateWithCandidates, MemoryStateRefreshMode.REGULAR)
            } else {
                stateWithCandidates
            }
        saveState()
    }

    override fun clear() {
        state = clearPolicy.clear(
            state = state,
            systemMessage = createSystemMessage(),
            memoryStrategy = memoryStrategy
        )
        saveState()
    }

    override fun replaceContextFromFile(sourcePath: Path) {
        val importedState = memoryStateRepository.loadFrom(sourcePath)
        require(importedState.shortTerm.rawMessages.isNotEmpty()) {
            "Файл истории $sourcePath пустой или не содержит сообщений."
        }

        state = memoryStrategy.refreshState(importedState, MemoryStateRefreshMode.REGULAR)
        saveState()
    }

    override fun memoryState(): MemoryState = state

    override fun memorySnapshot(): MemorySnapshot =
        MemorySnapshot(
            state = state,
            shortTermStrategyType = memoryStrategy.type
        )

    override fun users(): List<UserAccount> = state.users

    override fun activeUser(): UserAccount = state.activeUser()

    override fun createUser(userId: String, displayName: String?): UserAccount {
        val normalizedId = normalizeUserId(userId)
        require(state.users.none { it.id == normalizedId }) {
            "Пользователь '$normalizedId' уже существует."
        }

        val user = UserAccount(
            id = normalizedId,
            displayName = displayName?.trim().takeUnless { it.isNullOrBlank() } ?: normalizedId
        )
        saveState(
            state.copy(
                users = state.users + user
            )
        )
        return user
    }

    override fun switchUser(userId: String): UserAccount {
        val normalizedId = normalizeUserId(userId)
        val user = state.users.firstOrNull { it.id == normalizedId }
            ?: error("Пользователь '$normalizedId' не найден.")
        saveState(state.copy(activeUserId = user.id))
        return user
    }

    override fun profileNotes(): List<MemoryNote> =
        state.longTerm.notes.filter { it.ownerType == MemoryOwnerType.USER && it.ownerId == state.activeUserId }

    override fun pendingMemory(): PendingMemoryState = state.pending

    override fun approvePendingMemory(candidateIds: List<String>): PendingMemoryActionResult {
        val (selected, remaining) = selectPendingCandidates(candidateIds)
        val updatedState = candidateApplier.apply(
            state = state.copy(pending = remaining),
            candidates = selected.map(::toDraft)
        )
        saveState(updatedState)
        return PendingMemoryActionResult(
            affectedIds = selected.map(PendingMemoryCandidate::id),
            pendingState = state.pending
        )
    }

    override fun rejectPendingMemory(candidateIds: List<String>): PendingMemoryActionResult {
        val (selected, remaining) = selectPendingCandidates(candidateIds)
        saveState(state.copy(pending = remaining))
        return PendingMemoryActionResult(
            affectedIds = selected.map(PendingMemoryCandidate::id),
            pendingState = state.pending
        )
    }

    override fun editPendingMemory(candidateId: String, edit: PendingMemoryEdit): PendingMemoryState {
        val existing = state.pending.candidates.firstOrNull { it.id == candidateId }
            ?: error("Pending-кандидат '$candidateId' не найден.")
        val updatedCandidate = applyPendingEdit(existing, edit)
        candidateValidator.validateEditedCandidate(toDraft(updatedCandidate))

        saveState(
            state.copy(
                pending = state.pending.copy(
                    candidates = state.pending.candidates.map { candidate ->
                        if (candidate.id == candidateId) updatedCandidate else candidate
                    }
                )
            )
        )
        return state.pending
    }

    override fun memoryCategories(layer: MemoryLayer): List<String> {
        require(layer != MemoryLayer.SHORT_TERM) {
            "Ручные категории доступны только для слоёв working и long."
        }
        return MemoryLayerCategories.definitionsFor(layer).map { it.id }
    }

    override fun addMemoryNote(layer: MemoryLayer, category: String, content: String): ManagedMemoryNoteResult {
        validateManagedNoteInput(layer, category, content)
        val note = MemoryNote(
            id = "n${state.nextNoteId}",
            category = category.trim(),
            content = content.trim()
        )
        val updatedState = when (layer) {
            MemoryLayer.WORKING -> state.copy(
                working = state.working.copy(notes = state.working.notes + note),
                nextNoteId = state.nextNoteId + 1
            )
            MemoryLayer.LONG_TERM -> state.copy(
                longTerm = state.longTerm.copy(notes = state.longTerm.notes + note),
                nextNoteId = state.nextNoteId + 1
            )
            MemoryLayer.SHORT_TERM -> error("Нельзя вручную добавлять заметки в short-term.")
        }
        saveState(updatedState)
        return ManagedMemoryNoteResult(note = note, state = state)
    }

    override fun editMemoryNote(layer: MemoryLayer, noteId: String, edit: ManagedMemoryNoteEdit): ManagedMemoryNoteResult {
        require(layer != MemoryLayer.SHORT_TERM) {
            "Нельзя вручную редактировать заметки short-term."
        }

        val existing = notesFor(layer).firstOrNull { it.id == noteId }
            ?: error("Заметка '$noteId' не найдена в слое ${layer.name.lowercase()}.")

        val updated = updateManagedNote(layer, existing, edit)
        val updatedState = updateNotes(
            layer = layer,
            notes = notesFor(layer).map { if (it.id == noteId) updated else it }
        )
        saveState(updatedState)
        return ManagedMemoryNoteResult(note = updated, state = state)
    }

    override fun deleteMemoryNote(layer: MemoryLayer, noteId: String): ManagedMemoryNoteResult {
        require(layer != MemoryLayer.SHORT_TERM) {
            "Нельзя вручную удалять заметки short-term."
        }

        val existing = notesFor(layer).firstOrNull { it.id == noteId }
            ?: error("Заметка '$noteId' не найдена в слое ${layer.name.lowercase()}.")
        val updatedState = updateNotes(
            layer = layer,
            notes = notesFor(layer).filterNot { it.id == noteId }
        )
        saveState(updatedState)
        return ManagedMemoryNoteResult(note = existing, state = state)
    }

    override fun addProfileNote(category: String, content: String): ManagedMemoryNoteResult {
        validateManagedNoteInput(MemoryLayer.LONG_TERM, category, content)
        val note = MemoryNote(
            id = "n${state.nextNoteId}",
            category = category.trim(),
            content = content.trim(),
            ownerType = MemoryOwnerType.USER,
            ownerId = state.activeUserId
        )
        saveState(
            state.copy(
                longTerm = state.longTerm.copy(notes = state.longTerm.notes + note),
                nextNoteId = state.nextNoteId + 1
            )
        )
        return ManagedMemoryNoteResult(note = note, state = state)
    }

    override fun editProfileNote(noteId: String, edit: ManagedMemoryNoteEdit): ManagedMemoryNoteResult {
        val existing = profileNotes().firstOrNull { it.id == noteId }
            ?: error("Профильная заметка '$noteId' не найдена у активного пользователя.")
        val updated = updateManagedNote(MemoryLayer.LONG_TERM, existing, edit)
        saveState(
            state.copy(
                longTerm = state.longTerm.copy(
                    notes = state.longTerm.notes.map { note -> if (note.id == noteId) updated else note }
                )
            )
        )
        return ManagedMemoryNoteResult(note = updated, state = state)
    }

    override fun deleteProfileNote(noteId: String): ManagedMemoryNoteResult {
        val existing = profileNotes().firstOrNull { it.id == noteId }
            ?: error("Профильная заметка '$noteId' не найдена у активного пользователя.")
        saveState(
            state.copy(
                longTerm = state.longTerm.copy(
                    notes = state.longTerm.notes.filterNot { it.id == noteId }
                )
            )
        )
        return ManagedMemoryNoteResult(note = existing, state = state)
    }

    override fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability? =
        branchingCapability
            .takeIf { memoryStrategy.type == MemoryStrategyType.BRANCHING && capabilityType.isInstance(it) }
            ?.let(capabilityType::cast)

    private fun loadMemoryState(): MemoryState {
        val savedState = memoryStateRepository.load()
        if (savedState.shortTerm.rawMessages.isNotEmpty()) {
            val normalizedState = normalizeUsers(savedState)
            val refreshedState = memoryStrategy.refreshState(normalizedState, MemoryStateRefreshMode.REGULAR)
            if (refreshedState != savedState) {
                memoryStateRepository.save(refreshedState)
            }
            return refreshedState
        }

        val initialState = memoryStrategy.refreshState(
            MemoryState(
                shortTerm = ShortTermMemory(
                    rawMessages = listOf(createSystemMessage())
                )
            ),
            MemoryStateRefreshMode.REGULAR
        )
        saveState(initialState)
        return initialState
    }

    private fun normalizeUsers(sourceState: MemoryState): MemoryState {
        if (sourceState.users.isNotEmpty() && sourceState.users.any { it.id == sourceState.activeUserId }) {
            return sourceState
        }
        return sourceState.copy(
            users = if (sourceState.users.isEmpty()) listOf(UserAccount(MemoryState.DEFAULT_USER_ID, "Default")) else sourceState.users,
            activeUserId = sourceState.users.firstOrNull()?.id ?: MemoryState.DEFAULT_USER_ID
        )
    }

    private fun saveState() {
        saveState(state)
    }

    private fun saveState(updatedState: MemoryState) {
        state = updatedState
        memoryStateRepository.save(state)
    }

    private fun createSystemMessage(): ChatMessage =
        ChatMessage(
            role = ChatRole.SYSTEM,
            content = systemPrompt
        )

    private fun previewStateForUserPrompt(userPrompt: String): MemoryState {
        val userMessage = ChatMessage(role = ChatRole.USER, content = userPrompt)
        return refreshState(
            appendShortTermMessage(state, userMessage),
            notifyCompression = false,
            mode = MemoryStateRefreshMode.PREVIEW
        )
    }

    private fun refreshState(
        updatedState: MemoryState,
        notifyCompression: Boolean,
        mode: MemoryStateRefreshMode = MemoryStateRefreshMode.REGULAR
    ): MemoryState {
        val refreshedState = memoryStrategy.refreshState(updatedState, mode)
        val compressionStats =
            if (notifyCompression) {
                compressionObserver.buildStats(
                    previousState = updatedState,
                    refreshedState = refreshedState,
                    countTokens = ::countPromptTokens
                )
            } else {
                null
            }
        if (compressionStats == null) {
            return refreshedState
        }

        lifecycleListener.onContextCompressionStarted()
        lifecycleListener.onContextCompressionFinished(compressionStats)

        return refreshedState
    }

    private fun countPromptTokens(state: MemoryState): Int? =
        contextService.countPromptTokens(
            languageModel = languageModel,
            systemPrompt = systemPrompt,
            state = state
        )

    private fun appendShortTermMessage(currentState: MemoryState, message: ChatMessage): MemoryState =
        currentState.copy(
            shortTerm = currentState.shortTerm.copy(
                rawMessages = currentState.shortTerm.rawMessages + message
            )
        )

    private fun processCandidatesIfAllowed(currentState: MemoryState, message: ChatMessage): MemoryState {
        if (!memoryLayerWritePolicy.shouldAllocate(message)) {
            return currentState
        }

        val extractedCandidates = memoryLayerAllocator.extractCandidates(currentState, message)
        val validatedCandidates = candidateValidator.validate(currentState, message, extractedCandidates)
        val confirmationDecision = confirmationPolicy.classify(message.role, validatedCandidates)
        val stateWithAutoApplied = candidateApplier.apply(currentState, confirmationDecision.autoApply)
        return appendPendingCandidates(stateWithAutoApplied, message, confirmationDecision.pending)
    }

    private fun appendPendingCandidates(
        currentState: MemoryState,
        message: ChatMessage,
        drafts: List<MemoryCandidateDraft>
    ): MemoryState {
        if (drafts.isEmpty()) {
            return currentState
        }

        val startId = currentState.pending.nextId
        val additions = drafts.mapIndexed { index, draft ->
            PendingMemoryCandidate(
                id = "p${startId + index}",
                targetLayer = draft.targetLayer,
                category = draft.category,
                content = draft.content,
                ownerType = draft.ownerType,
                ownerId = draft.ownerId,
                sourceRole = message.role,
                sourceMessage = message.content
            )
        }

        return currentState.copy(
            pending = currentState.pending.copy(
                candidates = mergePendingCandidates(currentState.pending.candidates, additions),
                nextId = startId + drafts.size
            )
        )
    }

    private fun mergePendingCandidates(
        existing: List<PendingMemoryCandidate>,
        additions: List<PendingMemoryCandidate>
    ): List<PendingMemoryCandidate> =
        (existing + additions).distinctBy { candidate ->
            listOf(
                candidate.targetLayer.name,
                candidate.category.lowercase(),
                candidate.content.lowercase(),
                candidate.ownerType.name,
                candidate.ownerId ?: "",
                candidate.sourceRole.name,
                candidate.sourceMessage.lowercase()
            ).joinToString("|")
        }

    private fun selectPendingCandidates(candidateIds: List<String>): Pair<List<PendingMemoryCandidate>, PendingMemoryState> {
        val selectedIds = candidateIds.toSet()
        val selectAll = selectedIds.isEmpty()
        val selected = state.pending.candidates.filter { selectAll || it.id in selectedIds }
        require(selected.isNotEmpty()) {
            "Нет pending-кандидатов для выбранных идентификаторов."
        }

        val remaining = state.pending.copy(
            candidates = state.pending.candidates.filterNot { candidate -> selectAll || candidate.id in selectedIds }
        )
        return selected to remaining
    }

    private fun applyPendingEdit(candidate: PendingMemoryCandidate, edit: PendingMemoryEdit): PendingMemoryCandidate =
        when (edit) {
            is PendingMemoryEdit.UpdateText -> candidate.copy(content = edit.content.trim())
            is PendingMemoryEdit.UpdateLayer -> candidate.copy(targetLayer = edit.targetLayer)
            is PendingMemoryEdit.UpdateCategory -> candidate.copy(category = edit.category.trim())
        }

    private fun toDraft(candidate: PendingMemoryCandidate): MemoryCandidateDraft =
        MemoryCandidateDraft(
            targetLayer = candidate.targetLayer,
            category = candidate.category,
            content = candidate.content,
            ownerType = candidate.ownerType,
            ownerId = candidate.ownerId
        )

    private fun validateManagedNoteInput(layer: MemoryLayer, category: String, content: String) {
        require(layer != MemoryLayer.SHORT_TERM) {
            "Нельзя вручную добавлять заметки в short-term."
        }
        require(content.isNotBlank()) {
            "Текст заметки не должен быть пустым."
        }
        validateManagedCategory(layer, category)
    }

    private fun validateManagedCategory(layer: MemoryLayer, category: String) {
        val normalizedCategory = category.trim()
        require(MemoryLayerCategories.isCategoryAllowed(layer, normalizedCategory)) {
            "Категория '$normalizedCategory' недоступна для слоя ${layer.name.lowercase()}."
        }
    }

    private fun updateManagedNote(layer: MemoryLayer, existing: MemoryNote, edit: ManagedMemoryNoteEdit): MemoryNote =
        when (edit) {
            is ManagedMemoryNoteEdit.UpdateText -> {
                require(edit.content.isNotBlank()) { "Текст заметки не должен быть пустым." }
                existing.copy(content = edit.content.trim())
            }
            is ManagedMemoryNoteEdit.UpdateCategory -> {
                validateManagedCategory(layer, edit.category)
                existing.copy(category = edit.category.trim())
            }
        }

    private fun notesFor(layer: MemoryLayer): List<MemoryNote> =
        when (layer) {
            MemoryLayer.WORKING -> state.working.notes
            MemoryLayer.LONG_TERM -> state.longTerm.notes
            MemoryLayer.SHORT_TERM -> emptyList()
        }

    private fun updateNotes(layer: MemoryLayer, notes: List<MemoryNote>): MemoryState =
        when (layer) {
            MemoryLayer.WORKING -> state.copy(working = WorkingMemory(notes = notes))
            MemoryLayer.LONG_TERM -> state.copy(longTerm = LongTermMemory(notes = notes))
            MemoryLayer.SHORT_TERM -> state
        }

    private fun normalizeUserId(rawValue: String): String =
        rawValue
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .ifBlank { error("Идентификатор пользователя не должен быть пустым.") }
}
