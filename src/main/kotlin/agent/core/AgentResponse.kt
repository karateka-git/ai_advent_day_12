package agent.core

import llm.core.model.TokenUsage

/**
 * Распарсенный ответ агента, возвращаемый на уровень приложения.
 */
data class AgentResponse<T>(
    val content: T,
    val tokenStats: AgentTokenStats
)

/**
 * Локально рассчитанная оценка токенов вместе с необязательной статистикой от провайдера.
 */
data class AgentTokenStats(
    val historyTokens: Int? = null,
    val promptTokensLocal: Int? = null,
    val userPromptTokens: Int? = null,
    val apiUsage: TokenUsage? = null
)
