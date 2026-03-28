package agent.storage

import agent.storage.model.ConversationHistory
import agent.storage.model.StoredMessage
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import llm.core.LanguageModel

private const val CONTEXT_DIRECTORY = "config/conversations"

class JsonConversationStore(
    private val storagePath: Path
) : ConversationStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun load(): List<StoredMessage> {
        if (!Files.exists(storagePath)) {
            return emptyList()
        }

        val rawContent = Files.readString(storagePath)
        if (rawContent.isBlank()) {
            return emptyList()
        }

        return json.decodeFromString<ConversationHistory>(rawContent).messages
    }

    override fun save(messages: List<StoredMessage>) {
        val parent = storagePath.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }

        Files.writeString(
            storagePath,
            json.encodeToString(ConversationHistory(messages))
        )
    }

    companion object {
        fun forLanguageModel(languageModel: LanguageModel): JsonConversationStore =
            JsonConversationStore(buildStoragePath(languageModel))

        internal fun buildStoragePath(languageModel: LanguageModel): Path {
            val providerPart = sanitizePathPart(languageModel.info.name)
            val modelPart = sanitizePathPart(languageModel.info.model)
            return Path.of(CONTEXT_DIRECTORY, "${providerPart}__${modelPart}.json")
        }

        private fun sanitizePathPart(value: String): String =
            value
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "default" }
    }
}
