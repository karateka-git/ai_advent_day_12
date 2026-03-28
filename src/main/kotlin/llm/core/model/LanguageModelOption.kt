package llm.core.model

data class LanguageModelOption(
    val id: String,
    val displayName: String,
    val isConfigured: Boolean,
    val unavailableReason: String? = null
)
