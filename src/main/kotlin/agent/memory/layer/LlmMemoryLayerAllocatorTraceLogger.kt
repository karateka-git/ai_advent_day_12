package agent.memory.layer

import agent.memory.model.MemoryCandidateDraft
import agent.memory.model.MemoryState
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import llm.core.model.ChatMessage

/**
 * Пишет отладочный trace работы LLM allocator в отдельный файл.
 *
 * Лог нужен для диагностики случаев, когда utility LLM:
 * - не возвращает кандидатов;
 * - возвращает неожиданный JSON;
 * - падает и приводит к fallback на rule-based allocator.
 */
class LlmMemoryLayerAllocatorTraceLogger(
    private val logPath: Path = Path.of("build", "logs", "llm-memory-layer-allocator.log")
) {
    /**
     * Логирует старт LLM-извлечения и входные данные.
     */
    fun logRequest(
        state: MemoryState,
        message: ChatMessage,
        systemPrompt: String,
        userPrompt: String
    ) {
        appendBlock(
            title = "LLM allocator request",
            lines = listOf(
                "message.role=${message.role.apiValue}",
                "message.content=${message.content}",
                "working.notes=${formatNotes(state.working.notes)}",
                "longTerm.notes=${formatNotes(state.longTerm.notes)}",
                "systemPrompt:",
                systemPrompt,
                "userPrompt:",
                userPrompt
            )
        )
    }

    /**
     * Логирует сырой ответ utility LLM.
     */
    fun logRawResponse(rawResponse: String) {
        appendBlock(
            title = "LLM allocator raw response",
            lines = listOf(rawResponse)
        )
    }

    /**
     * Логирует успешно распарсенный результат извлечения.
     */
    fun logParsedExtraction(extraction: LlmMemoryLayerExtraction) {
        appendBlock(
            title = "LLM allocator parsed extraction",
            lines = listOf(
                "working=${formatNotes(extraction.workingNotes)}",
                "longTerm=${formatNotes(extraction.longTermNotes)}"
            )
        )
    }

    /**
     * Логирует итоговых кандидатов, которые выйдут из allocator.
     */
    fun logFinalCandidates(candidates: List<MemoryCandidateDraft>) {
        appendBlock(
            title = "LLM allocator final candidates",
            lines = listOf(
                if (candidates.isEmpty()) {
                    "(empty)"
                } else {
                    candidates.joinToString(separator = "\n") { candidate ->
                        "- layer=${candidate.targetLayer.name.lowercase()} category=${candidate.category} content=${candidate.content}"
                    }
                }
            )
        )
    }

    /**
     * Логирует ошибку primary allocator и факт переключения на fallback.
     */
    fun logFallback(error: Throwable) {
        appendBlock(
            title = "LLM allocator fallback",
            lines = listOf(
                "error.type=${error::class.qualifiedName}",
                "error.message=${error.message ?: "(no message)"}"
            )
        )
    }

    /**
     * Логирует необработанную ошибку LLM allocator до перехода в fallback.
     */
    fun logFailure(error: Throwable) {
        appendBlock(
            title = "LLM allocator failure",
            lines = listOf(
                "error.type=${error::class.qualifiedName}",
                "error.message=${error.message ?: "(no message)"}"
            )
        )
    }

    private fun appendBlock(title: String, lines: List<String>) {
        val timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val text = buildString {
            appendLine("=== $title @ $timestamp ===")
            lines.forEach { line -> appendLine(line) }
            appendLine()
        }

        synchronized(this) {
            Files.createDirectories(logPath.parent)
            Files.writeString(
                logPath,
                text,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        }
    }

    private fun formatNotes(notes: List<agent.memory.model.MemoryNote>): String =
        if (notes.isEmpty()) {
            "[]"
        } else {
            notes.joinToString(separator = " | ") { note ->
                "{id=${note.id}, category=${note.category}, content=${note.content}}"
            }
        }
}
