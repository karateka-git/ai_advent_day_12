package agent.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse

class JsonConversationStorePathTest {
    @Test
    fun `buildStoragePath uses provider and model specific file`() {
        val languageModel = FakeLanguageModel(
            info = LanguageModelInfo(
                name = "HuggingFaceLanguageModel",
                model = "Qwen/Qwen2.5-1.5B-Instruct"
            )
        )

        val path = JsonConversationStore.buildStoragePath(languageModel)

        assertEquals(
            "config\\conversations\\huggingfacelanguagemodel__qwen_qwen2_5_1_5b_instruct.json",
            path.toString()
        )
    }
}

private class FakeLanguageModel(
    override val info: LanguageModelInfo
) : LanguageModel {
    override val tokenCounter = null

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse =
        error("Не должен вызываться в этом тесте.")
}
