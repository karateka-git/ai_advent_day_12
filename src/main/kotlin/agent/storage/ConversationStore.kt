package agent.storage

import agent.storage.model.StoredMessage

interface ConversationStore {
    fun load(): List<StoredMessage>

    fun save(messages: List<StoredMessage>)
}
