package llm.timeweb.tokenizer

import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepSeekLocalTokenCounterTest {
    @Test
    fun `countText returns positive token count for non empty text`() {
        val counter = DeepSeekLocalTokenCounter()

        val tokenCount = counter.countText("Привет! Это проверка локального токенизатора.")

        assertTrue(tokenCount > 0)
    }

    @Test
    fun `countMessages equals sum of individual messages`() {
        val counter = DeepSeekLocalTokenCounter()
        val messages = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = "Ты помощник."),
            ChatMessage(role = ChatRole.USER, content = "Привет!"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "Здравствуйте.")
        )

        val total = counter.countMessages(messages)
        val expected = messages.sumOf(counter::countMessage)

        assertEquals(expected, total)
    }
}
