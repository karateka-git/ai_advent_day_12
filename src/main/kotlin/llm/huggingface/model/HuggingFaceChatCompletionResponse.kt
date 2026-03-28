package llm.huggingface.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import llm.core.model.ChatMessage

@Serializable
data class HuggingFaceChatCompletionResponse(
    val choices: List<HuggingFaceChoice> = emptyList(),
    val usage: HuggingFaceUsage? = null
)

@Serializable
data class HuggingFaceChoice(
    val message: ChatMessage
)

@Serializable
data class HuggingFaceUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)
