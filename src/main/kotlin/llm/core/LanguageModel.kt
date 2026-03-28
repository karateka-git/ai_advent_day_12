package llm.core

import llm.core.model.ChatMessage
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse
import llm.core.tokenizer.TokenCounter

/**
 * Общая абстракция над конкретными LLM-провайдерами.
 */
interface LanguageModel {
    /**
     * Статические метаданные провайдера и модели, отображаемые в приложении.
     */
    val info: LanguageModelInfo

    /**
     * Необязательный локальный счётчик токенов для оценки prompt.
     */
    val tokenCounter: TokenCounter?

    /**
     * Отправляет переданные сообщения prompt в модель и возвращает сырой ответ провайдера.
     */
    fun complete(messages: List<ChatMessage>): LanguageModelResponse
}
