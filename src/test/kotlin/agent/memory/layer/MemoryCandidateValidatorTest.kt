package agent.memory.layer

import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryCandidateDraft
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
import agent.memory.model.MemoryState
import agent.memory.model.PendingMemoryCandidate
import agent.memory.model.PendingMemoryState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class MemoryCandidateValidatorTest {
    private val validator = MemoryCandidateValidator()

    @Test
    fun `keeps structurally valid candidates without duplicates`() {
        val state = MemoryState()
        val message = ChatMessage(ChatRole.USER, "Напомни мой стиль общения.")
        val candidates = listOf(
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.LONG_TERM,
                category = "communication_style",
                content = "Отвечать лаконично и дружелюбно"
            ),
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.WORKING,
                category = "deadline",
                content = "Реалистичный срок на ближайший релиз"
            )
        )

        assertEquals(candidates, validator.validate(state, message, candidates))
    }

    @Test
    fun `drops structurally invalid candidates`() {
        val state = MemoryState()
        val message = ChatMessage(ChatRole.USER, "Любое сообщение")
        val candidates = listOf(
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.SHORT_TERM,
                category = "goal",
                content = "Не должен пройти"
            ),
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.LONG_TERM,
                category = "unknown",
                content = "Неизвестная категория"
            ),
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.WORKING,
                category = "goal",
                content = "   "
            )
        )

        assertEquals(emptyList(), validator.validate(state, message, candidates))
    }

    @Test
    fun `drops candidates already stored in memory`() {
        val state = MemoryState(
            longTerm = LongTermMemory(
                notes = listOf(
                    MemoryNote(
                        id = "n1",
                        category = "communication_style",
                        content = "Общайся со мной будто я твой брат",
                        ownerType = MemoryOwnerType.USER,
                        ownerId = "default"
                    )
                )
            )
        )
        val message = ChatMessage(ChatRole.USER, "Привет!")
        val candidates = listOf(
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.LONG_TERM,
                category = "communication_style",
                content = "Общайся со мной будто я твой брат",
                ownerType = MemoryOwnerType.USER,
                ownerId = "default"
            )
        )

        assertEquals(emptyList(), validator.validate(state, message, candidates))
    }

    @Test
    fun `drops candidates already present in pending`() {
        val state = MemoryState(
            pending = PendingMemoryState(
                candidates = listOf(
                    PendingMemoryCandidate(
                        id = "p1",
                        targetLayer = MemoryLayer.LONG_TERM,
                        category = "communication_style",
                        content = "Отвечай кратко",
                        ownerType = MemoryOwnerType.USER,
                        ownerId = "default",
                        sourceRole = ChatRole.USER,
                        sourceMessage = "Отвечай кратко"
                    )
                )
            )
        )
        val message = ChatMessage(ChatRole.USER, "Привет!")
        val candidates = listOf(
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.LONG_TERM,
                category = "communication_style",
                content = "Отвечай кратко",
                ownerType = MemoryOwnerType.USER,
                ownerId = "default"
            )
        )

        assertEquals(emptyList(), validator.validate(state, message, candidates))
    }

    @Test
    fun `edited candidate is validated structurally only`() {
        validator.validateEditedCandidate(
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.LONG_TERM,
                category = "communication_style",
                content = "Отвечать более формально"
            )
        )
    }

    @Test
    fun `edited candidate fails on invalid layer`() {
        assertFailsWith<IllegalArgumentException> {
            validator.validateEditedCandidate(
                MemoryCandidateDraft(
                    targetLayer = MemoryLayer.SHORT_TERM,
                    category = "goal",
                    content = "Недопустимо"
                )
            )
        }
    }
}
