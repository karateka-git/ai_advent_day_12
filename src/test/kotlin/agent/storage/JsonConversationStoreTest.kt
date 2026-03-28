package agent.storage

import agent.storage.model.StoredMessage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonConversationStoreTest {
    @Test
    fun `load returns empty list when file does not exist`() {
        val tempDir = Files.createTempDirectory("conversation-store-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))

        assertEquals(emptyList(), store.load())
    }

    @Test
    fun `save and load preserve messages`() {
        val tempDir = Files.createTempDirectory("conversation-store-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val messages = listOf(
            StoredMessage(role = "system", content = "Ты помощник."),
            StoredMessage(role = "user", content = "Привет"),
            StoredMessage(role = "assistant", content = "Здравствуйте")
        )

        store.save(messages)

        assertEquals(messages, store.load())
    }
}
