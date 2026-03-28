package agent.storage

import agent.storage.model.ConversationHistory
import agent.storage.model.ConversationMemoryState
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import llm.core.LanguageModel

private const val CONTEXT_DIRECTORY = "config/conversations"

/**
 * JSON-хранилище диалога, используемое слоем памяти.
 *
 * Хранилище читает текущий структурированный формат памяти и при необходимости использует
 * fallback на legacy-формат, содержащий только историю сообщений.
 */
class JsonConversationStore(
    private val storagePath: Path
) : ConversationStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun loadState(): ConversationMemoryState {
        if (!Files.exists(storagePath)) {
            return ConversationMemoryState()
        }

        val rawContent = Files.readString(storagePath)
        if (rawContent.isBlank()) {
            return ConversationMemoryState()
        }

        return try {
            json.decodeFromString<ConversationMemoryState>(rawContent)
        } catch (_: SerializationException) {
            ConversationMemoryState(
                messages = json.decodeFromString<ConversationHistory>(rawContent).messages
            )
        }
    }

    override fun saveState(state: ConversationMemoryState) {
        val parent = storagePath.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }

        Files.writeString(
            storagePath,
            json.encodeToString(state)
        )
    }

    companion object {
        /**
         * Создаёт хранилище, привязанное к файлу истории конкретной модели.
         */
        fun forLanguageModel(languageModel: LanguageModel): JsonConversationStore =
            JsonConversationStore(buildStoragePath(languageModel))

        /**
         * Строит путь к JSON-файлу, используемому для конкретной пары провайдер/модель.
         */
        internal fun buildStoragePath(languageModel: LanguageModel): Path {
            val providerPart = sanitizePathPart(languageModel.info.name)
            val modelPart = sanitizePathPart(languageModel.info.model)
            return Path.of(CONTEXT_DIRECTORY, "${providerPart}__${modelPart}.json")
        }

        /**
         * Преобразует имена провайдера и модели в безопасные сегменты пути.
         */
        private fun sanitizePathPart(value: String): String =
            value
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "default" }
    }
}
