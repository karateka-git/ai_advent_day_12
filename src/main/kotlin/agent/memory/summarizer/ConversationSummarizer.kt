package agent.memory.summarizer

import llm.core.model.ChatMessage

/**
 * Создаёт компактное текстовое summary для фрагмента диалога.
 */
interface ConversationSummarizer {
    /**
     * Сжимает переданные сообщения в один текст summary.
     */
    fun summarize(messages: List<ChatMessage>): String
}
