package agent.memory.layer

import agent.memory.model.MemoryCandidateDraft
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryOwnerType
import agent.memory.model.MemoryState
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Выполняет структурную и базовую дедупликационную проверку кандидатов
 * перед автосохранением или отправкой в pending.
 */
class MemoryCandidateValidator {
    /**
     * Возвращает только допустимых кандидатов, которых ещё нет в durable memory
     * и в текущей pending-очереди.
     *
     * @param state текущее состояние памяти ассистента.
     * @param message исходное сообщение, из которого были извлечены кандидаты.
     * @param candidates черновики заметок до валидации.
     * @return кандидаты, которые можно передавать дальше в confirmation flow.
     */
    fun validate(
        state: MemoryState,
        message: ChatMessage,
        candidates: List<MemoryCandidateDraft>
    ): List<MemoryCandidateDraft> {
        if (message.role == ChatRole.SYSTEM) {
            return emptyList()
        }

        return candidates
            .filter(::isLayerSupported)
            .filter(::hasAllowedCategory)
            .filter(::hasMeaningfulContent)
            .filter(::hasConsistentOwner)
            .filterNot { existsInMemory(state, it) }
            .filterNot { existsInPending(state, it) }
            .distinctBy(::draftKey)
    }

    /**
     * Проверяет, что после ручной правки pending-кандидат остаётся структурно допустимым.
     *
     * @param candidate кандидат после пользовательского редактирования.
     */
    fun validateEditedCandidate(candidate: MemoryCandidateDraft) {
        require(isLayerSupported(candidate)) {
            "Pending-кандидат нельзя сохранить в краткосрочную память."
        }
        require(hasAllowedCategory(candidate)) {
            "Категория ${candidate.category} не подходит для слоя ${candidate.targetLayer.name.lowercase()}."
        }
        require(hasMeaningfulContent(candidate)) {
            "Текст кандидата не должен быть пустым."
        }
        require(hasConsistentOwner(candidate)) {
            "Некорректно задан владелец заметки."
        }
    }

    private fun isLayerSupported(candidate: MemoryCandidateDraft): Boolean =
        candidate.targetLayer != MemoryLayer.SHORT_TERM

    private fun hasAllowedCategory(candidate: MemoryCandidateDraft): Boolean =
        MemoryLayerCategories.isCategoryAllowed(candidate.targetLayer, candidate.category)

    private fun hasMeaningfulContent(candidate: MemoryCandidateDraft): Boolean =
        candidate.content.trim().isNotEmpty()

    private fun hasConsistentOwner(candidate: MemoryCandidateDraft): Boolean =
        when (candidate.ownerType) {
            MemoryOwnerType.GLOBAL -> candidate.ownerId == null
            MemoryOwnerType.USER -> !candidate.ownerId.isNullOrBlank()
        }

    private fun existsInMemory(state: MemoryState, candidate: MemoryCandidateDraft): Boolean =
        notesFor(state, candidate.targetLayer).any { note ->
            note.category == candidate.category &&
                note.ownerType == candidate.ownerType &&
                note.ownerId == candidate.ownerId &&
                note.content.equals(candidate.content, ignoreCase = true)
        }

    private fun existsInPending(state: MemoryState, candidate: MemoryCandidateDraft): Boolean =
        state.pending.candidates.any { pending ->
            pending.targetLayer == candidate.targetLayer &&
                pending.category == candidate.category &&
                pending.ownerType == candidate.ownerType &&
                pending.ownerId == candidate.ownerId &&
                pending.content.equals(candidate.content, ignoreCase = true)
        }

    private fun notesFor(state: MemoryState, layer: MemoryLayer) =
        when (layer) {
            MemoryLayer.WORKING -> state.working.notes
            MemoryLayer.LONG_TERM -> state.longTerm.notes
            MemoryLayer.SHORT_TERM -> emptyList()
        }

    private fun draftKey(candidate: MemoryCandidateDraft): String =
        listOf(
            candidate.targetLayer.name,
            candidate.category.lowercase(),
            candidate.ownerType.name,
            candidate.ownerId ?: "",
            candidate.content.trim().lowercase()
        ).joinToString("|")
}
