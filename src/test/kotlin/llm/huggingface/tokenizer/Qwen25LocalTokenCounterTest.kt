package llm.huggingface.tokenizer

import kotlin.test.Test
import kotlin.test.assertTrue

class Qwen25LocalTokenCounterTest {
    @Test
    fun `countText returns positive token count for non empty text`() {
        val counter = Qwen25LocalTokenCounter()

        val tokenCount = counter.countText("Привет! Это проверка локального токенизатора Qwen2.5.")

        assertTrue(tokenCount > 0)
    }
}
