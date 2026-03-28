package agent.core

import llm.core.model.TokenUsage

data class AgentResponse<T>(
    val content: T,
    val tokenStats: AgentTokenStats
)

data class AgentTokenStats(
    val historyTokens: Int? = null,
    val promptTokensLocal: Int? = null,
    val userPromptTokens: Int? = null,
    val apiUsage: TokenUsage? = null
)
