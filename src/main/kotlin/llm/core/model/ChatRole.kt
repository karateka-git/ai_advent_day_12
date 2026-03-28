package llm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole(val apiValue: String, val displayName: String) {
    @SerialName("system")
    SYSTEM("system", "Система"),

    @SerialName("user")
    USER("user", "Пользователь"),

    @SerialName("assistant")
    ASSISTANT("assistant", "Ассистент")
}
