package agent.storage.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationHistory(
    val messages: List<StoredMessage>
)
