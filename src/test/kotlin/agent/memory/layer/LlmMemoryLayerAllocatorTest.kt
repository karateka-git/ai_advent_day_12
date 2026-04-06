package agent.memory.layer

import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
import agent.memory.model.MemoryState
import agent.memory.model.LongTermMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse

class LlmMemoryLayerAllocatorTest {
    @Test
    fun `system prompt describes long-term scope`() {
        val prompt = LlmMemoryLayerAllocatorPromptBuilder().buildSystemPrompt()

        assertTrue(prompt.contains("scope"))
        assertTrue(prompt.contains("user"))
        assertTrue(prompt.contains("global"))
        assertTrue(prompt.contains("Никогда не копируй"))
        assertTrue(prompt.contains("Привет!"))
    }

    @Test
    fun `user prompt marks existing memory as already saved and non-repeatable`() {
        val prompt = LlmMemoryLayerAllocatorPromptBuilder().buildUserPrompt(
            state = MemoryState(
                longTerm = LongTermMemory(
                    notes = listOf(
                        MemoryNote(
                            id = "n1",
                            category = "communication_style",
                            content = "Отвечай кратко",
                            ownerType = MemoryOwnerType.USER,
                            ownerId = "default"
                        )
                    )
                )
            ),
            message = ChatMessage(role = ChatRole.USER, content = "Привет!")
        )

        assertTrue(prompt.contains("Уже сохранённая long-term memory"))
        assertTrue(prompt.contains("не повторяй"))
        assertTrue(prompt.contains("Если содержание уже есть в сохранённой памяти, тоже верни пустые массивы."))
        assertTrue(prompt.contains("Привет!"))
    }

    @Test
    fun `extractor parses json response into owner-aware memory notes`() {
        val extractor = LlmConversationMemoryLayerAllocationExtractor(
            languageModel = FakeLanguageModel(
                """
                {
                  "working": [
                    { "category": "goal", "content": "Telegram-бот для записи на урок" }
                  ],
                  "longTerm": [
                    { "category": "communication_style", "content": "Отвечать кратко и на русском", "scope": "user" },
                    { "category": "architectural_agreement", "content": "Используем Kotlin CLI", "scope": "global" }
                  ]
                }
                """.trimIndent()
            )
        )

        val extraction = extractor.extract(
            state = MemoryState(),
            message = ChatMessage(role = ChatRole.USER, content = "Цель проекта и стиль общения")
        )

        assertEquals(
            listOf(MemoryNote(category = "goal", content = "Telegram-бот для записи на урок")),
            extraction.workingNotes
        )
        assertEquals(
            listOf(
                MemoryNote(
                    id = "",
                    category = "communication_style",
                    content = "Отвечать кратко и на русском",
                    ownerType = MemoryOwnerType.USER,
                    ownerId = null
                ),
                MemoryNote(
                    id = "",
                    category = "architectural_agreement",
                    content = "Используем Kotlin CLI",
                    ownerType = MemoryOwnerType.GLOBAL,
                    ownerId = null
                )
            ),
            extraction.longTermNotes
        )
    }

    @Test
    fun `allocator returns owner-aware candidates and fills active user id for profile notes`() {
        val allocator = LlmMemoryLayerAllocator(
            extractor = object : LlmMemoryLayerAllocationExtractor {
                override fun extract(state: MemoryState, message: ChatMessage): LlmMemoryLayerExtraction =
                    LlmMemoryLayerExtraction(
                        workingNotes = listOf(MemoryNote(category = "goal", content = "Подготовить production-версию")),
                        longTermNotes = listOf(
                            MemoryNote(
                                id = "",
                                category = "communication_style",
                                content = "Отвечать подробно",
                                ownerType = MemoryOwnerType.USER,
                                ownerId = null
                            )
                        )
                    )
            }
        )

        val candidates = allocator.extractCandidates(
            state = MemoryState(activeUserId = "anna"),
            message = ChatMessage(role = ChatRole.USER, content = "Обновляю цель и стиль общения")
        )

        assertEquals(
            listOf(MemoryLayer.WORKING, MemoryLayer.LONG_TERM),
            candidates.map { it.targetLayer }
        )
        assertEquals(
            listOf("goal", "communication_style"),
            candidates.map { it.category }
        )
        assertEquals(MemoryOwnerType.GLOBAL, candidates[0].ownerType)
        assertEquals(null, candidates[0].ownerId)
        assertEquals(MemoryOwnerType.USER, candidates[1].ownerType)
        assertEquals("anna", candidates[1].ownerId)
    }
}

private class FakeLanguageModel(
    private val responseContent: String
) : LanguageModel {
    override val info: LanguageModelInfo = LanguageModelInfo(
        name = "FakeLanguageModel",
        model = "fake-model"
    )

    override val tokenCounter = null

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse =
        LanguageModelResponse(content = responseContent)
}
