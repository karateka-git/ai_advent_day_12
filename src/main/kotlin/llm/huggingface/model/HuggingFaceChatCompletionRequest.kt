package llm.huggingface.model

import kotlinx.serialization.Serializable
import llm.core.model.ChatMessage

@Serializable
data class HuggingFaceChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double
)
