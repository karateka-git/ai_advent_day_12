package agent.memory.layer

import agent.memory.model.MemoryCandidateDraft
import agent.memory.model.MemoryState
import llm.core.model.ChatMessage

/**
 * Явно извлекает кандидатов на сохранение в durable memory слои.
 */
interface MemoryLayerAllocator {
    /**
     * Извлекает из нового сообщения кандидатов для working и long-term памяти.
     *
     * @param state текущее layered memory state.
     * @param message новое сообщение, которое нужно проанализировать.
     * @return черновики заметок до валидации и confirmation policy.
     */
    fun extractCandidates(state: MemoryState, message: ChatMessage): List<MemoryCandidateDraft>
}

/**
 * Безопасный fallback allocator, который не извлекает заметки и всегда возвращает пустой список.
 *
 * Используется как деградация при недоступности или ошибке utility LLM allocator'а,
 * чтобы система не подменяла поведение упрощённой эвристикой.
 */
class NoOpMemoryLayerAllocator : MemoryLayerAllocator {
    override fun extractCandidates(state: MemoryState, message: ChatMessage): List<MemoryCandidateDraft> = emptyList()
}
