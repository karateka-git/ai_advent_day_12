package agent.memory.layer

import agent.memory.model.MemoryCandidateDraft
import agent.memory.model.MemoryState
import java.net.http.HttpClient
import java.util.Properties
import llm.core.model.ChatMessage
import llm.huggingface.HuggingFaceLanguageModel

/**
 * Создаёт распределитель памяти для durable memory-слоёв.
 *
 * Позволяет использовать отдельную более дешёвую модель для утилитных задач памяти
 * независимо от основной модели диалога.
 */
object MemoryLayerAllocatorFactory {
    /**
     * Создаёт allocator по runtime-конфигурации.
     *
     * Если настроен `HF_API_TOKEN`, для распределения памяти используется отдельный
     * `HuggingFaceLanguageModel`. Иначе возвращается rule-based fallback.
     */
    fun create(config: Properties, httpClient: HttpClient): MemoryLayerAllocator {
        val hfToken = config.getProperty("HF_API_TOKEN")?.takeIf { it.isNotBlank() }
        val traceLogger = LlmMemoryLayerAllocatorTraceLogger()
        return if (hfToken != null) {
            FallbackMemoryLayerAllocator(
                primary = LlmMemoryLayerAllocator(
                    extractor = LlmConversationMemoryLayerAllocationExtractor(
                        languageModel = HuggingFaceLanguageModel(
                            httpClient = httpClient,
                            userToken = hfToken
                        ),
                        traceLogger = traceLogger
                    ),
                    traceLogger = traceLogger
                ),
                fallback = RuleBasedMemoryLayerAllocator(),
                traceLogger = traceLogger
            )
        } else {
            RuleBasedMemoryLayerAllocator()
        }
    }
}

/**
 * Пытается использовать основной allocator, а при его ошибке мягко переключается на резервный.
 */
class FallbackMemoryLayerAllocator(
    private val primary: MemoryLayerAllocator,
    private val fallback: MemoryLayerAllocator,
    private val traceLogger: LlmMemoryLayerAllocatorTraceLogger? = null
) : MemoryLayerAllocator {
    override fun extractCandidates(state: MemoryState, message: ChatMessage): List<MemoryCandidateDraft> =
        runCatching { primary.extractCandidates(state, message) }
            .getOrElse { error ->
                traceLogger?.logFallback(error)
                fallback.extractCandidates(state, message)
            }
}
