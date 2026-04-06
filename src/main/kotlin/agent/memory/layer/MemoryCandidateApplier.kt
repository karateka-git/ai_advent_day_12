package agent.memory.layer

import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryState
import agent.memory.model.WorkingMemory

/**
 * Применяет подтверждённые или авто-сохранённые кандидаты к durable memory слоям.
 */
interface MemoryCandidateApplier {
    /**
     * Обновляет рабочую и долговременную память новыми кандидатами.
     *
     * @param state текущее состояние памяти.
     * @param candidates кандидаты, которые уже прошли валидацию и готовы к сохранению.
     * @return новое состояние памяти с обновлёнными durable memory слоями.
     */
    fun apply(state: MemoryState, candidates: List<agent.memory.model.MemoryCandidateDraft>): MemoryState
}

/**
 * Применяет кандидатов к рабочей и долговременной памяти через merge policy заметок.
 */
class DurableMemoryCandidateApplier(
    private val noteMergePolicy: MemoryNoteMergePolicy = RuleBasedMemoryNoteMergePolicy()
) : MemoryCandidateApplier {
    override fun apply(state: MemoryState, candidates: List<agent.memory.model.MemoryCandidateDraft>): MemoryState {
        if (candidates.isEmpty()) {
            return state
        }

        val notesByDraftIndex = candidates.mapIndexed { index, candidate ->
            candidate to MemoryNote(
                id = "n${state.nextNoteId + index}",
                category = candidate.category,
                content = candidate.content,
                ownerType = candidate.ownerType,
                ownerId = candidate.ownerId
            )
        }
        val workingAdditions = candidates
            .filter { it.targetLayer == MemoryLayer.WORKING }
            .map { draft -> notesByDraftIndex.first { it.first === draft }.second }
        val longTermAdditions = candidates
            .filter { it.targetLayer == MemoryLayer.LONG_TERM }
            .map { draft -> notesByDraftIndex.first { it.first === draft }.second }

        return state.copy(
            working = WorkingMemory(
                notes = noteMergePolicy.merge(state.working.notes, workingAdditions)
            ),
            longTerm = LongTermMemory(
                notes = noteMergePolicy.merge(state.longTerm.notes, longTermAdditions)
            ),
            nextNoteId = state.nextNoteId + candidates.size
        )
    }
}
