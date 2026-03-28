package agent.format

interface ResponseFormat<T> {
    val formatInstruction: String

    fun parse(rawResponse: String): T
}
