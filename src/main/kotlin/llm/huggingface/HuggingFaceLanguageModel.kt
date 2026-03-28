package llm.huggingface

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.tokenizer.TokenCounter
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse
import llm.huggingface.mapper.HuggingFaceChatCompletionResponseMapper
import llm.huggingface.model.HuggingFaceChatCompletionRequest
import llm.huggingface.model.HuggingFaceChatCompletionResponse
import llm.huggingface.tokenizer.Qwen25LocalTokenCounter

private const val MODEL = "Qwen/Qwen2.5-1.5B-Instruct"
private const val PROVIDER = "featherless-ai"
private const val API_BASE_URL = "https://router.huggingface.co/v1"
private const val TEMPERATURE = 0.7

class HuggingFaceLanguageModel(
    private val httpClient: HttpClient,
    private val userToken: String
) : LanguageModel {
    private val json = Json { ignoreUnknownKeys = true }
    private val responseMapper = HuggingFaceChatCompletionResponseMapper()

    override val info = LanguageModelInfo(
        name = "HuggingFaceLanguageModel",
        model = MODEL
    )
    override val tokenCounter: TokenCounter = Qwen25LocalTokenCounter()

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse {
        val requestBody = json.encodeToString(
            HuggingFaceChatCompletionRequest(
                model = "$MODEL:$PROVIDER",
                messages = messages,
                temperature = TEMPERATURE
            )
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$API_BASE_URL/chat/completions"))
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Authorization", "Bearer $userToken")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))

        if (response.statusCode() !in 200..299) {
            error("Hugging Face API вернул статус ${response.statusCode()}: ${response.body()}")
        }

        val completion = json.decodeFromString<HuggingFaceChatCompletionResponse>(response.body())
        return responseMapper.toLanguageModelResponse(completion)
    }
}
