package agent.storage.mapper

import agent.storage.model.StoredMessage
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class ChatMessageConversationMapper : ConversationMapper {
    override fun toStoredMessage(message: ChatMessage): StoredMessage =
        StoredMessage(
            role = message.role.apiValue,
            content = message.content
        )

    override fun fromStoredMessage(message: StoredMessage): ChatMessage =
        ChatMessage(
            role = ChatRole.entries.firstOrNull { it.apiValue == message.role }
                ?: error("Неизвестная роль сообщения: ${message.role}"),
            content = message.content
        )
}
