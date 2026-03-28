package agent.lifecycle

/**
 * Реализация lifecycle-listener, которая игнорирует все коллбеки.
 */
object NoOpAgentLifecycleListener : AgentLifecycleListener {
    override fun onModelWarmupStarted() = Unit

    override fun onModelWarmupFinished() = Unit

    override fun onContextCompressionStarted() = Unit

    override fun onContextCompressionFinished(stats: ContextCompressionStats) = Unit
}
