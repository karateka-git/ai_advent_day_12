package llm.timeweb.model

import kotlinx.serialization.Serializable
import llm.core.model.ChatMessage

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double
)
