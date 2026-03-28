package llm.core.tokenizer

import llm.core.model.ChatMessage

interface TokenCounter {
    fun countText(text: String): Int

    fun countMessages(messages: List<ChatMessage>): Int =
        messages.sumOf(::countMessage)

    fun countMessage(message: ChatMessage): Int =
        countText("${message.role.apiValue}\n${message.content}")
}
