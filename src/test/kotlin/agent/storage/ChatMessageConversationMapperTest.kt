package agent.storage

import agent.storage.mapper.ChatMessageConversationMapper
import agent.storage.model.StoredMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatRole

class ChatMessageConversationMapperTest {
    private val mapper = ChatMessageConversationMapper()

    @Test
    fun `fromStoredMessage maps user role and content`() {
        val message = mapper.fromStoredMessage(
            StoredMessage(
                role = "user",
                content = "Привет!"
            )
        )

        assertEquals(ChatRole.USER, message.role)
        assertEquals("Привет!", message.content)
    }
}
