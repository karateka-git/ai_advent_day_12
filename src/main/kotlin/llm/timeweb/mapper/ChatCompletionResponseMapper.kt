package llm.timeweb.mapper

import llm.core.model.LanguageModelResponse
import llm.core.model.TokenUsage
import llm.timeweb.model.ChatCompletionResponse

class ChatCompletionResponseMapper {
    fun toLanguageModelResponse(completion: ChatCompletionResponse): LanguageModelResponse {
        val content = completion.choices.firstOrNull()?.message?.content
            ?: error("Ответ API не содержит choices[0].message.content")
        val usage = completion.usage?.let {
            TokenUsage(
                promptTokens = it.promptTokens,
                completionTokens = it.completionTokens,
                totalTokens = it.totalTokens
            )
        }

        return LanguageModelResponse(
            content = content,
            usage = usage
        )
    }
}
