package agent.memory.model

import llm.core.model.ChatRole

/**
 * Черновая заметка, которую allocator предлагает сохранить в одном из durable memory слоёв.
 *
 * @property targetLayer целевой слой памяти: рабочий или долговременный.
 * @property category категория заметки внутри выбранного слоя.
 * @property content текст заметки.
 */
data class MemoryCandidateDraft(
    val targetLayer: MemoryLayer,
    val category: String,
    val content: String,
    val ownerType: MemoryOwnerType = MemoryOwnerType.GLOBAL,
    val ownerId: String? = null
)

/**
 * Кандидат на сохранение, ожидающий решения пользователя.
 *
 * @property id стабильный идентификатор кандидата в пределах persisted state.
 * @property targetLayer целевой слой памяти.
 * @property category категория заметки.
 * @property content текст заметки.
 * @property sourceRole роль источника, из которого получен кандидат.
 * @property sourceMessage исходное сообщение, из которого был извлечён кандидат.
 */
data class PendingMemoryCandidate(
    val id: String,
    val targetLayer: MemoryLayer,
    val category: String,
    val content: String,
    val ownerType: MemoryOwnerType = MemoryOwnerType.GLOBAL,
    val ownerId: String? = null,
    val sourceRole: ChatRole,
    val sourceMessage: String
)

/**
 * Persisted очередь кандидатов, ожидающих подтверждения или правки.
 *
 * @property candidates текущие pending-кандидаты.
 * @property nextId следующий пользовательский идентификатор кандидата.
 */
data class PendingMemoryState(
    val candidates: List<PendingMemoryCandidate> = emptyList(),
    val nextId: Long = 1
)

/**
 * Результат применения или отклонения pending-кандидатов.
 *
 * @property affectedIds идентификаторы кандидатов, над которыми выполнено действие.
 * @property pendingState оставшееся pending-состояние после операции.
 */
data class PendingMemoryActionResult(
    val affectedIds: List<String>,
    val pendingState: PendingMemoryState
)

/**
 * Изменение одного pending-кандидата перед подтверждением.
 */
sealed interface PendingMemoryEdit {
    /**
     * Заменяет текст заметки.
     */
    data class UpdateText(
        val content: String
    ) : PendingMemoryEdit

    /**
     * Переносит заметку в другой слой памяти.
     */
    data class UpdateLayer(
        val targetLayer: MemoryLayer
    ) : PendingMemoryEdit

    /**
     * Меняет категорию заметки внутри слоя памяти.
     */
    data class UpdateCategory(
        val category: String
    ) : PendingMemoryEdit
}
