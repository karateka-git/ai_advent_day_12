package agent.memory.layer

import agent.memory.model.MemoryCandidateDraft
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
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
    override fun extractCandidates(state: MemoryState, message: ChatMessage): List<MemoryCandidateDraft> =
        if (message.role == ChatRole.SYSTEM) {
            emptyList()
        } else {
            extractor.extract(state, message).toCandidateDrafts(state.activeUserId)
                .also { candidates -> traceLogger?.logFinalCandidates(candidates) }
        }
}

/**
 * Контракт компонента, который анализирует сообщение через LLM и извлекает заметки.
 */
interface LlmMemoryLayerAllocationExtractor {
    fun extract(state: MemoryState, message: ChatMessage): LlmMemoryLayerExtraction
}

/**
 * Результат извлечения заметок из одного сообщения.
 */
data class LlmMemoryLayerExtraction(
    val workingNotes: List<MemoryNote> = emptyList(),
    val longTermNotes: List<MemoryNote> = emptyList()
) {
    /**
     * Преобразует результат извлечения в черновики кандидатов.
     *
     * Для user-scoped long-term заметок подставляет `ownerId` активного пользователя.
     */
    fun toCandidateDrafts(activeUserId: String): List<MemoryCandidateDraft> =
        workingNotes.map { note ->
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.WORKING,
                category = note.category,
                content = note.content,
                ownerType = note.ownerType,
                ownerId = note.ownerId
            )
        } + longTermNotes.map { note ->
            MemoryCandidateDraft(
                targetLayer = MemoryLayer.LONG_TERM,
                category = note.category,
                content = note.content,
                ownerType = note.ownerType,
                ownerId = when (note.ownerType) {
                    MemoryOwnerType.GLOBAL -> null
                    MemoryOwnerType.USER -> note.ownerId ?: activeUserId
                }
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
                workingNotes = parsed.working.toMemoryNotes(MemoryLayerCategories.workingCategories, allowScope = false),
                longTermNotes = parsed.longTerm.toMemoryNotes(MemoryLayerCategories.longTermCategories, allowScope = true)
            ).also { extraction -> traceLogger?.logParsedExtraction(extraction) }
        } catch (error: Throwable) {
            traceLogger?.logFailure(error)
            throw error
        }
    }

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

    private fun List<LlmMemoryNotePayload>.toMemoryNotes(
        allowedCategories: Set<String>,
        allowScope: Boolean
    ): List<MemoryNote> =
        mapNotNull { payload ->
            val category = payload.category.trim()
            val content = payload.content.trim()
            if (category !in allowedCategories || content.isBlank()) {
                return@mapNotNull null
            }

            val ownerType = if (allowScope) payload.scope.toOwnerType() else MemoryOwnerType.GLOBAL
            MemoryNote(
                id = "",
                category = category,
                content = content,
                ownerType = ownerType,
                ownerId = null
            )
        }

    private fun String.toOwnerType(): MemoryOwnerType =
        when (trim().lowercase()) {
            "user" -> MemoryOwnerType.USER
            else -> MemoryOwnerType.GLOBAL
        }
}

/**
 * Собирает prompt для LLM-распределителя памяти.
 */
class LlmMemoryLayerAllocatorPromptBuilder {
    fun buildSystemPrompt(): String =
        """
        Ты анализируешь новое сообщение и предлагаешь, что стоит сохранить в layered memory ассистента.

        Сначала реши, нужно ли вообще что-то сохранять.
        Если в сообщении нет действительно полезных и устойчивых данных, верни пустые массивы.

        Working memory хранит только данные текущей задачи:
        ${MemoryLayerCategories.formatForPrompt(MemoryLayer.WORKING)}

        Long-term memory хранит только устойчивые данные, которые с высокой вероятностью пригодятся в будущих диалогах:
        ${MemoryLayerCategories.formatForPrompt(MemoryLayer.LONG_TERM)}

        В long-term у каждой заметки есть scope:
        - user: предпочтение или устойчивое знание именно о текущем активном пользователе;
        - global: общее знание о проекте, архитектуре или ассистенте, не привязанное к одному пользователю.

        Как различать scope в long-term:
        - communication_style и persistent_preference обычно относятся к scope=user, если сообщение описывает предпочтения конкретного пользователя;
        - architectural_agreement и reusable_knowledge обычно относятся к scope=global, если речь о проекте или общей договорённости.

        Уже сохранённая working memory и long-term memory даются только как справка о том, что сохранять НЕ НУЖНО повторно.
        Никогда не копируй, не перефразируй и не возвращай заметки из уже сохранённой памяти как новые кандидаты.
        Извлекать можно только то, что явно содержится в новом сообщении.

        Что не нужно сохранять:
        - обычные вопросы, команды и служебные сообщения;
        - временные детали текущего ответа;
        - уже сохранённые факты из working memory или long-term memory;
        - предположения, которых нет в сообщении явно.

        Пример:
        - если новое сообщение: "Привет!"
        - а в long-term memory уже есть предпочтения по стилю общения
        - нужно вернуть пустые массивы, а не повторять эти предпочтения

        Верни только валидный JSON:
        {
          "working": [{"category": "...", "content": "..."}],
          "longTerm": [{"category": "...", "content": "...", "scope": "user|global"}]
        }
        """.trimIndent()

    fun buildUserPrompt(state: MemoryState, message: ChatMessage): String =
        buildString {
            appendLine("Активный пользователь:")
            appendLine("- id: ${state.activeUserId}")
            appendLine("- displayName: ${state.activeUser().displayName}")
            appendLine()
            appendLine("Уже сохранённая working memory (не повторяй её в ответе):")
            appendLine(formatNotes(state.working.notes))
            appendLine()
            appendLine("Уже сохранённая long-term memory (не повторяй и не перефразируй её в ответе):")
            appendLine(formatNotes(state.longTerm.notes))
            appendLine()
            appendLine("Нужно проанализировать только новое сообщение и извлечь из него новые кандидаты в память.")
            appendLine("Если сообщение не содержит новых полезных данных, верни пустые массивы.")
            appendLine("Если содержание уже есть в сохранённой памяти, тоже верни пустые массивы.")
            appendLine()
            appendLine("Новое сообщение:")
            appendLine("${message.role.apiValue}: ${message.content}")
        }

    private fun formatNotes(notes: List<MemoryNote>): String =
        if (notes.isEmpty()) {
            "[]"
        } else {
            notes.joinToString(separator = "\n") { note ->
                buildString {
                    append("- ${note.category}: ${note.content}")
                    if (note.ownerType == MemoryOwnerType.USER) {
                        append(" [scope=user")
                        note.ownerId?.let { append(", ownerId=$it") }
                        append("]")
                    }
                }
            }
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
    val content: String = "",
    val scope: String = "global"
)
