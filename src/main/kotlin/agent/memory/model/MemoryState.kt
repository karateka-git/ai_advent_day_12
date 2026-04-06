package agent.memory.model

import llm.core.model.ChatMessage

/**
 * Тип слоя памяти ассистента.
 */
enum class MemoryLayer {
    SHORT_TERM,
    WORKING,
    LONG_TERM
}

enum class MemoryOwnerType {
    GLOBAL,
    USER
}

data class UserAccount(
    val id: String,
    val displayName: String
)

/**
 * Краткая единица явно сохранённой рабочей или долговременной памяти.
 *
 * @property id устойчивый идентификатор заметки внутри persisted state.
 * @property category доменная категория заметки, например `goal` или `communication_style`.
 * @property content нормализованное текстовое содержимое заметки.
 */
data class MemoryNote(
    val id: String,
    val category: String,
    val content: String,
    val ownerType: MemoryOwnerType = MemoryOwnerType.GLOBAL,
    val ownerId: String? = null
) {
    constructor(category: String, content: String) : this(
        id = "",
        category = category,
        content = content,
        ownerType = MemoryOwnerType.GLOBAL,
        ownerId = null
    )
}

/**
 * Краткосрочная память: сырой журнал текущей сессии и его представление,
 * вычисленное активной short-term стратегией.
 */
data class ShortTermMemory(
    val rawMessages: List<ChatMessage> = emptyList(),
    val derivedMessages: List<ChatMessage> = emptyList(),
    val strategyState: StrategyState? = null
)

/**
 * Рабочая память: данные текущей задачи.
 */
data class WorkingMemory(
    val notes: List<MemoryNote> = emptyList()
)

/**
 * Долговременная память: устойчивые предпочтения, договорённости и знания.
 */
data class LongTermMemory(
    val notes: List<MemoryNote> = emptyList()
)

/**
 * Полный in-memory снимок памяти ассистента с явным разделением по слоям.
 *
 * @property nextNoteId следующий числовой идентификатор для новой durable-заметки.
 */
data class MemoryState(
    val shortTerm: ShortTermMemory = ShortTermMemory(),
    val working: WorkingMemory = WorkingMemory(),
    val longTerm: LongTermMemory = LongTermMemory(),
    val users: List<UserAccount> = listOf(UserAccount(id = DEFAULT_USER_ID, displayName = "Default")),
    val activeUserId: String = DEFAULT_USER_ID,
    val pending: PendingMemoryState = PendingMemoryState(),
    val nextNoteId: Long = 1
) {
    fun activeUser(): UserAccount =
        users.firstOrNull { it.id == activeUserId } ?: users.first()

    companion object {
        const val DEFAULT_USER_ID = "default"
    }
}
