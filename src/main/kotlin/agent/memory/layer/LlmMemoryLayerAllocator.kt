package agent.memory.layer

import agent.memory.model.MemoryCandidateDraft
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Извлекает кандидатов по слоям памяти с помощью отдельного LLM-вызова.
 */
class LlmMemoryLayerAllocator(
    private val extractor: LlmMemoryLayerAllocationExtractor,
    private val traceLogger: LlmMemoryLayerAllocatorTraceLogger? = null
) : MemoryLayerAllocator {
    /**
     * Извлекает кандидатов из нового сообщения.
     *
     * @param state текущее состояние памяти.
     * @param message новое сообщение, которое нужно проанализировать.
     * @return кандидаты для working и long-term слоёв.
     */
    override fun extractCandidates(state: MemoryState, message: ChatMessage): List<MemoryCandidateDraft> =
        if (message.role == ChatRole.SYSTEM) {
            emptyList()
        } else {
            extractor.extract(state, message).toCandidateDrafts()
                .also { candidates -> traceLogger?.logFinalCandidates(candidates) }
        }
}

/**
 * Контракт компонента, который анализирует сообщение через LLM и извлекает заметки.
 */
interface LlmMemoryLayerAllocationExtractor {
    /**
     * Извлекает кандидатов из одного сообщения.
     *
     * @param state текущее состояние памяти.
     * @param message новое сообщение пользователя или ассистента.
     * @return извлечённые working- и long-term заметки.
     */
    fun extract(state: MemoryState, message: ChatMessage): LlmMemoryLayerExtraction
}

/**
 * Результат извлечения заметок из одного сообщения.
 *
 * @property workingNotes заметки для рабочей памяти.
 * @property longTermNotes заметки для долговременной памяти.
 */
data class LlmMemoryLayerExtraction(
    val workingNotes: List<MemoryNote> = emptyList(),
    val longTermNotes: List<MemoryNote> = emptyList()
) {
    /**
     * Преобразует результат извлечения в черновики кандидатов.
     */
    fun toCandidateDrafts(): List<MemoryCandidateDraft> =
        workingNotes.map { note ->
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.WORKING,
                category = note.category,
                content = note.content
            )
        } + longTermNotes.map { note ->
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.LONG_TERM,
                category = note.category,
                content = note.content
            )
        }
}

/**
 * Реализация extractor'а, которая вызывает языковую модель и ожидает JSON-ответ.
 */
class LlmConversationMemoryLayerAllocationExtractor(
    private val languageModel: LanguageModel,
    private val promptBuilder: LlmMemoryLayerAllocatorPromptBuilder = LlmMemoryLayerAllocatorPromptBuilder(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val traceLogger: LlmMemoryLayerAllocatorTraceLogger? = null
) : LlmMemoryLayerAllocationExtractor {
    /**
     * Запрашивает у модели кандидатов для layered memory.
     *
     * @param state текущее состояние памяти.
     * @param message новое сообщение.
     * @return извлечённые working- и long-term заметки.
     */
    override fun extract(state: MemoryState, message: ChatMessage): LlmMemoryLayerExtraction {
        val systemPrompt = promptBuilder.buildSystemPrompt()
        val userPrompt = promptBuilder.buildUserPrompt(state, message)
        traceLogger?.logRequest(
            state = state,
            message = message,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )

        return try {
            val response = languageModel.complete(
                listOf(
                    ChatMessage(role = ChatRole.SYSTEM, content = systemPrompt),
                    ChatMessage(role = ChatRole.USER, content = userPrompt)
                )
            )
            traceLogger?.logRawResponse(response.content)

            val payload = extractJsonObject(response.content)
            val parsed = json.decodeFromString<LlmMemoryLayerAllocationPayload>(payload)
            LlmMemoryLayerExtraction(
                workingNotes = parsed.working.toMemoryNotes(MemoryLayerCategories.workingCategories),
                longTermNotes = parsed.longTerm.toMemoryNotes(MemoryLayerCategories.longTermCategories)
            ).also { extraction -> traceLogger?.logParsedExtraction(extraction) }
        } catch (error: Throwable) {
            traceLogger?.logFailure(error)
            throw error
        }
    }

    /**
     * Выделяет JSON-объект из ответа модели.
     *
     * @param rawContent сырой текст ответа модели.
     * @return строка с JSON-объектом.
     */
    fun extractJsonObject(rawContent: String): String {
        val fencedJson = Regex("```json\\s*(\\{[\\s\\S]*})\\s*```").find(rawContent)
        if (fencedJson != null) {
            return fencedJson.groupValues[1]
        }

        val fenced = Regex("```\\s*(\\{[\\s\\S]*})\\s*```").find(rawContent)
        if (fenced != null) {
            return fenced.groupValues[1]
        }

        val firstBrace = rawContent.indexOf('{')
        val lastBrace = rawContent.lastIndexOf('}')
        require(firstBrace >= 0 && lastBrace > firstBrace) {
            "LLM allocator не вернул JSON-объект."
        }

        return rawContent.substring(firstBrace, lastBrace + 1)
    }

    private fun List<LlmMemoryNotePayload>.toMemoryNotes(allowedCategories: Set<String>): List<MemoryNote> =
        mapNotNull { payload ->
            val category = payload.category.trim()
            val content = payload.content.trim()
            if (category in allowedCategories && content.isNotBlank()) {
                MemoryNote(id = "", category = category, content = content)
            } else {
                null
            }
        }
}

/**
 * Собирает prompt для LLM-распределителя памяти.
 */
class LlmMemoryLayerAllocatorPromptBuilder {
    /**
     * Создаёт системный prompt с правилами различения working и long-term памяти.
     */
    fun buildSystemPrompt(): String =
        """
        Ты анализируешь новое сообщение и предлагаешь, что стоит сохранить в layered memory ассистента.

        Сначала реши, нужно ли вообще что-то сохранять.
        Если в сообщении нет действительно полезных и устойчивых данных, верни пустые массивы.

        Working memory хранит только данные текущей задачи:
        ${MemoryLayerCategories.formatForPrompt(MemoryLayer.WORKING)}

        Long-term memory хранит только устойчивые данные, которые с высокой вероятностью пригодятся в будущих диалогах:
        ${MemoryLayerCategories.formatForPrompt(MemoryLayer.LONG_TERM)}

        Как различать слои:
        - Working memory: цель, ограничения, сроки, бюджет, интеграции, решения и открытые вопросы именно текущей задачи.
        - Long-term memory: постоянные предпочтения, устойчивые договорённости, повторно полезные знания о пользователе или проекте.

        Что считать устойчивыми long-term данными:
        - предпочтение по стилю общения, которое выглядит как общее правило, а не как одноразовая просьба;
        - постоянное пользовательское предпочтение;
        - архитектурную договорённость, которая должна сохраняться между задачами;
        - знание о проекте или пользователе, которое пригодится не только в текущем шаге.

        Что не нужно сохранять:
        - обычные вопросы, команды и служебные сообщения;
        - просьбы напомнить, повторить, перечислить или пересказать уже известное;
        - временные детали, если они относятся только к текущему ответу и не выглядят устойчивыми;
        - рассуждения, предположения и данные, которых нет в сообщении явно.

        Важные правила:
        - не выдумывай заметки;
        - не сохраняй сообщение целиком, если нужно сохранить только отдельный смысловой фрагмент;
        - можно вернуть записи сразу в оба слоя, если сообщение содержит и рабочие, и устойчивые данные;
        - пустой longTerm или пустой working — это нормальный результат.
        - сохраняй content на том же языке, что и исходное сообщение;
        - не переводи и не меняй язык заметки;
        - по возможности используй максимально близкую формулировку к сообщению пользователя.

        Примеры:
        - "Срок проекта две недели, интеграция только с Google Sheets" -> working.
        - "Отвечай кратко и на русском" -> longTerm, если это выглядит как общее предпочтение стиля общения.
        - "Напомни мой стиль общения" -> не сохранять.
        - "В этом проекте бизнес-логика не должна зависеть от CLI" -> longTerm как устойчивая архитектурная договорённость.

        Верни только валидный JSON:
        {
          "working": [{"category": "...", "content": "..."}],
          "longTerm": [{"category": "...", "content": "..."}]
        }
        """.trimIndent()

    /**
     * Создаёт пользовательский prompt с текущим состоянием durable memory и новым сообщением.
     */
    fun buildUserPrompt(state: MemoryState, message: ChatMessage): String =
        buildString {
            appendLine("Текущая working memory:")
            appendLine(formatNotes(state.working.notes))
            appendLine()
            appendLine("Текущая long-term memory:")
            appendLine(formatNotes(state.longTerm.notes))
            appendLine()
            appendLine("Нужно проанализировать только новое сообщение и извлечь из него новые кандидаты в память.")
            appendLine("Если сообщение не содержит новых полезных данных, верни пустые массивы.")
            appendLine()
            appendLine("Новое сообщение:")
            appendLine("${message.role.apiValue}: ${message.content}")
        }

    private fun formatNotes(notes: List<MemoryNote>): String =
        if (notes.isEmpty()) {
            "[]"
        } else {
            notes.joinToString(separator = "\n") { "- ${it.category}: ${it.content}" }
        }
}

@Serializable
private data class LlmMemoryLayerAllocationPayload(
    val working: List<LlmMemoryNotePayload> = emptyList(),
    val longTerm: List<LlmMemoryNotePayload> = emptyList()
)

@Serializable
private data class LlmMemoryNotePayload(
    val category: String = "",
    val content: String = ""
)
