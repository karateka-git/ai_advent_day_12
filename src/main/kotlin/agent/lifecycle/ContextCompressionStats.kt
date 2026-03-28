package agent.lifecycle

/**
 * Количество токенов, собранное до и после сжатия памяти.
 */
data class ContextCompressionStats(
    val tokensBefore: Int?,
    val tokensAfter: Int?
) {
    /**
     * Разница между старым и новым количеством токенов, если оба значения известны.
     */
    val savedTokens: Int?
        get() =
            if (tokensBefore == null || tokensAfter == null) {
                null
            } else {
                tokensBefore - tokensAfter
            }
}
