package agent.memory.layer

import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleBasedMemoryNoteMergePolicyTest {
    private val mergePolicy = RuleBasedMemoryNoteMergePolicy()

    @Test
    fun `keeps multiple notes in the same category`() {
        val existing = listOf(
            MemoryNote(id = "n1", category = "communication_style", content = "Отвечай кратко")
        )
        val additions = listOf(
            MemoryNote(id = "n2", category = "communication_style", content = "Сначала вывод, потом детали")
        )

        val merged = mergePolicy.merge(existing, additions)

        assertEquals(
            listOf(
                MemoryNote(id = "n1", category = "communication_style", content = "Отвечай кратко"),
                MemoryNote(id = "n2", category = "communication_style", content = "Сначала вывод, потом детали")
            ),
            merged
        )
    }

    @Test
    fun `skips exact duplicates inside the same owner scope`() {
        val existing = listOf(
            MemoryNote(
                id = "n1",
                category = "communication_style",
                content = "Отвечай кратко",
                ownerType = MemoryOwnerType.USER,
                ownerId = "default"
            )
        )
        val additions = listOf(
            MemoryNote(
                id = "n2",
                category = "communication_style",
                content = "отвечай кратко",
                ownerType = MemoryOwnerType.USER,
                ownerId = "default"
            )
        )

        val merged = mergePolicy.merge(existing, additions)

        assertEquals(existing, merged)
    }
}
