package llm.huggingface.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import llm.huggingface.model.HuggingFaceChatCompletionResponse
import llm.huggingface.model.HuggingFaceChoice
import llm.huggingface.model.HuggingFaceUsage

class HuggingFaceChatCompletionResponseMapperTest {
    private val mapper = HuggingFaceChatCompletionResponseMapper()

    @Test
    fun `toLanguageModelResponse maps content and usage`() {
        val response = HuggingFaceChatCompletionResponse(
            choices = listOf(
                HuggingFaceChoice(
                    message = ChatMessage(
                        role = ChatRole.ASSISTANT,
                        content = "Привет!"
                    )
                )
            ),
            usage = HuggingFaceUsage(
                promptTokens = 10,
                completionTokens = 5,
                totalTokens = 15
            )
        )

        val mapped = mapper.toLanguageModelResponse(response)

        assertEquals("Привет!", mapped.content)
        assertEquals(10, mapped.usage?.promptTokens)
        assertEquals(5, mapped.usage?.completionTokens)
        assertEquals(15, mapped.usage?.totalTokens)
    }
}
