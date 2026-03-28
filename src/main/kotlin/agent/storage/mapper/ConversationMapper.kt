package agent.storage.mapper

import agent.storage.model.StoredMessage
import llm.core.model.ChatMessage

interface ConversationMapper {
    fun toStoredMessage(message: ChatMessage): StoredMessage

    fun fromStoredMessage(message: StoredMessage): ChatMessage
}
