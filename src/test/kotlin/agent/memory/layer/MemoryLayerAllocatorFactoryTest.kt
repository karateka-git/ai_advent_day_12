package agent.memory.layer

import agent.memory.model.MemoryState
import java.net.http.HttpClient
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import java.util.Properties

class MemoryLayerAllocatorFactoryTest {
    @Test
    fun `returns no-op allocator when hugging face token is absent`() {
        val allocator = MemoryLayerAllocatorFactory.create(
            config = Properties(),
            httpClient = HttpClient.newHttpClient()
        )

        assertIs<NoOpMemoryLayerAllocator>(allocator)
    }

    @Test
    fun `returns fallback allocator when hugging face token is configured`() {
        val allocator = MemoryLayerAllocatorFactory.create(
            config = Properties().apply {
                setProperty("HF_API_TOKEN", "hf-test-token")
            },
            httpClient = HttpClient.newHttpClient()
        )

        assertIs<FallbackMemoryLayerAllocator>(allocator)
    }

    @Test
    fun `fallback allocator uses no-op allocator when primary fails and logs fallback`() {
        val logPath = Files.createTempDirectory("allocator-log-test").resolve("allocator.log")
        val traceLogger = LlmMemoryLayerAllocatorTraceLogger(logPath)
        val allocator = FallbackMemoryLayerAllocator(
            primary = object : MemoryLayerAllocator {
                override fun extractCandidates(state: MemoryState, message: ChatMessage) =
                    error("primary failed")
            },
            fallback = NoOpMemoryLayerAllocator(),
            traceLogger = traceLogger
        )

        val candidates = allocator.extractCandidates(
            state = MemoryState(),
            message = ChatMessage(
                role = ChatRole.USER,
                content = "Цель задачи - сделать MVP"
            )
        )

        assertEquals(emptyList(), candidates)
        val logContent = Files.readString(logPath)
        assertTrue(logContent.contains("LLM allocator fallback"))
        assertTrue(logContent.contains("fallback=no-op"))
    }
}
