package llm.core

import llm.core.model.ChatMessage
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse
import llm.core.tokenizer.TokenCounter

interface LanguageModel {
    val info: LanguageModelInfo
    val tokenCounter: TokenCounter?

    fun complete(messages: List<ChatMessage>): LanguageModelResponse
}
