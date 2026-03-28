package agent.core

/**
 * Описывает текущую конфигурацию агента, которая показывается пользователю.
 */
data class AgentInfo(
    val name: String,
    val description: String,
    val model: String
)
