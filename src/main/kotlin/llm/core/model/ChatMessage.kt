package llm.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: ChatRole,
    val content: String
)
