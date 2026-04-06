package agent.memory.persistence

import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
import agent.memory.model.MemoryState
import agent.memory.model.PendingMemoryCandidate
import agent.memory.model.PendingMemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.UserAccount
import agent.memory.model.WorkingMemory
import agent.storage.mapper.ChatMessageConversationMapper
import agent.storage.model.ConversationMemoryState
import agent.storage.model.StoredLongTermMemory
import agent.storage.model.StoredMemoryNote
import agent.storage.model.StoredPendingMemoryCandidate
import agent.storage.model.StoredPendingMemoryState
import agent.storage.model.StoredShortTermMemory
import agent.storage.model.StoredUserAccount
import agent.storage.model.StoredWorkingMemory
import llm.core.model.ChatRole

/**
 * Преобразует полное runtime-состояние памяти в persisted JSON-модель и обратно.
 */
class ConversationMemoryStateMapper(
    private val conversationMapper: ChatMessageConversationMapper = ChatMessageConversationMapper(),
    private val strategyStateMapper: StrategyStateMapper = StrategyStateMapper(conversationMapper)
) {
    /**
     * Преобразует persisted state в runtime state.
     */
    fun toRuntime(storedState: ConversationMemoryState): MemoryState =
        MemoryState(
            shortTerm = ShortTermMemory(
                rawMessages = storedState.shortTerm.rawMessages.map(conversationMapper::fromStoredMessage),
                derivedMessages = storedState.shortTerm.derivedMessages.map(conversationMapper::fromStoredMessage),
                strategyState = strategyStateMapper.toRuntime(storedState.shortTerm.strategyState)
            ),
            working = WorkingMemory(
                notes = storedState.working.notes.map(::toRuntimeNote)
            ),
            longTerm = LongTermMemory(
                notes = storedState.longTerm.notes.map(::toRuntimeNote)
            ),
            users = storedState.users.map(::toRuntimeUser),
            activeUserId = storedState.activeUserId,
            pending = PendingMemoryState(
                candidates = storedState.pending.candidates.map(::toRuntimeCandidate),
                nextId = storedState.pending.nextId
            ),
            nextNoteId = storedState.nextNoteId
        )

    /**
     * Преобразует runtime state в persisted state.
     */
    fun toStored(runtimeState: MemoryState): ConversationMemoryState =
        ConversationMemoryState(
            shortTerm = StoredShortTermMemory(
                rawMessages = runtimeState.shortTerm.rawMessages.map(conversationMapper::toStoredMessage),
                derivedMessages = runtimeState.shortTerm.derivedMessages.map(conversationMapper::toStoredMessage),
                strategyState = strategyStateMapper.toStored(runtimeState.shortTerm.strategyState)
            ),
            working = StoredWorkingMemory(
                notes = runtimeState.working.notes.map(::toStoredNote)
            ),
            longTerm = StoredLongTermMemory(
                notes = runtimeState.longTerm.notes.map(::toStoredNote)
            ),
            users = runtimeState.users.map(::toStoredUser),
            activeUserId = runtimeState.activeUserId,
            pending = StoredPendingMemoryState(
                candidates = runtimeState.pending.candidates.map(::toStoredCandidate),
                nextId = runtimeState.pending.nextId
            ),
            nextNoteId = runtimeState.nextNoteId
        )

    private fun toRuntimeNote(note: StoredMemoryNote): MemoryNote =
        MemoryNote(
            id = note.id,
            category = note.category,
            content = note.content,
            ownerType = MemoryOwnerType.valueOf(note.ownerType),
            ownerId = note.ownerId
        )

    private fun toStoredNote(note: MemoryNote): StoredMemoryNote =
        StoredMemoryNote(
            id = note.id,
            category = note.category,
            content = note.content,
            ownerType = note.ownerType.name,
            ownerId = note.ownerId
        )

    private fun toRuntimeUser(user: StoredUserAccount): UserAccount =
        UserAccount(
            id = user.id,
            displayName = user.displayName
        )

    private fun toStoredUser(user: UserAccount): StoredUserAccount =
        StoredUserAccount(
            id = user.id,
            displayName = user.displayName
        )

    private fun toRuntimeCandidate(candidate: StoredPendingMemoryCandidate): PendingMemoryCandidate =
        PendingMemoryCandidate(
            id = candidate.id,
            targetLayer = MemoryLayer.valueOf(candidate.targetLayer),
            category = candidate.category,
            content = candidate.content,
            ownerType = MemoryOwnerType.valueOf(candidate.ownerType),
            ownerId = candidate.ownerId,
            sourceRole = ChatRole.valueOf(candidate.sourceRole),
            sourceMessage = candidate.sourceMessage
        )

    private fun toStoredCandidate(candidate: PendingMemoryCandidate): StoredPendingMemoryCandidate =
        StoredPendingMemoryCandidate(
            id = candidate.id,
            targetLayer = candidate.targetLayer.name,
            category = candidate.category,
            content = candidate.content,
            ownerType = candidate.ownerType.name,
            ownerId = candidate.ownerId,
            sourceRole = candidate.sourceRole.name,
            sourceMessage = candidate.sourceMessage
        )
}
