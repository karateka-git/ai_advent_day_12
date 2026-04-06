package agent.storage.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Persisted-снимок layered memory, который сериализуется в JSON-файл модели.
 */
@Serializable
data class ConversationMemoryState(
    val shortTerm: StoredShortTermMemory = StoredShortTermMemory(),
    val working: StoredWorkingMemory = StoredWorkingMemory(),
    val longTerm: StoredLongTermMemory = StoredLongTermMemory(),
    val users: List<StoredUserAccount> = listOf(StoredUserAccount(id = "default", displayName = "Default")),
    val activeUserId: String = "default",
    val pending: StoredPendingMemoryState = StoredPendingMemoryState(),
    val nextNoteId: Long = 1
)

/**
 * Persisted-форма заметки рабочего или долговременного слоя памяти.
 */
@Serializable
data class StoredMemoryNote(
    val id: String = "",
    val category: String,
    val content: String,
    val ownerType: String = "GLOBAL",
    val ownerId: String? = null
) {
    constructor(category: String, content: String) : this(
        id = "",
        category = category,
        content = content,
        ownerType = "GLOBAL",
        ownerId = null
    )
}

@Serializable
data class StoredUserAccount(
    val id: String,
    val displayName: String
)

/**
 * Persisted short-term слой.
 */
@Serializable
data class StoredShortTermMemory(
    val rawMessages: List<StoredMessage> = emptyList(),
    val derivedMessages: List<StoredMessage> = emptyList(),
    val strategyState: StoredStrategyState? = null
)

/**
 * Persisted рабочая память.
 */
@Serializable
data class StoredWorkingMemory(
    val notes: List<StoredMemoryNote> = emptyList()
)

/**
 * Persisted долговременная память.
 */
@Serializable
data class StoredLongTermMemory(
    val notes: List<StoredMemoryNote> = emptyList()
)

/**
 * Persisted очередь кандидатов на сохранение в durable memory.
 */
@Serializable
data class StoredPendingMemoryState(
    val candidates: List<StoredPendingMemoryCandidate> = emptyList(),
    val nextId: Long = 1
)

/**
 * Persisted кандидат на сохранение в память.
 */
@Serializable
data class StoredPendingMemoryCandidate(
    val id: String,
    val targetLayer: String,
    val category: String,
    val content: String,
    val ownerType: String = "GLOBAL",
    val ownerId: String? = null,
    val sourceRole: String,
    val sourceMessage: String
)

/**
 * Базовый контракт для strategy-specific persisted state.
 */
@Serializable
sealed interface StoredStrategyState

/**
 * Persisted state стратегии rolling summary.
 */
@Serializable
@SerialName("summary_compression")
data class StoredSummaryStrategyState(
    val summary: StoredSummary? = null,
    val coveredMessagesCount: Int = 0
) : StoredStrategyState

/**
 * Persisted state стратегии Sticky Facts.
 */
@Serializable
@SerialName("sticky_facts")
data class StoredStickyFactsStrategyState(
    val facts: Map<String, String> = emptyMap(),
    val coveredMessagesCount: Int = 0
) : StoredStrategyState

/**
 * Persisted state стратегии Branching.
 */
@Serializable
@SerialName("branching")
data class StoredBranchingStrategyState(
    val activeBranchName: String? = null,
    val latestCheckpointName: String? = null,
    val checkpoints: List<StoredBranchCheckpoint> = emptyList(),
    val branches: List<StoredBranchConversation> = emptyList()
) : StoredStrategyState

/**
 * Persisted checkpoint branch-aware short-term состояния.
 */
@Serializable
data class StoredBranchCheckpoint(
    val name: String,
    val messages: List<StoredMessage> = emptyList()
)

/**
 * Persisted ветка short-term диалога для branching-стратегии.
 */
@Serializable
data class StoredBranchConversation(
    val name: String,
    val sourceCheckpointName: String? = null,
    val messages: List<StoredMessage> = emptyList()
)
