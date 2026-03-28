package agent.format

/**
 * Текстовый формат вывода, используемый в CLI.
 */
object TextResponseFormat : ResponseFormat<String> {
    override val formatInstruction: String =
        "Верни обычный текстовый ответ без JSON и без служебной разметки."

    override fun parse(rawResponse: String): String = rawResponse
}
