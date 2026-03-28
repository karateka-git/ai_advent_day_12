package agent.storage.model

import kotlinx.serialization.Serializable

@Serializable
data class StoredMessage(
    val role: String,
    val content: String
)
