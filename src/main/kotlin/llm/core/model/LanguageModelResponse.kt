package llm.core.model

data class LanguageModelResponse(
    val content: String,
    val usage: TokenUsage? = null
)
