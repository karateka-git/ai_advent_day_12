package llm.huggingface.mapper

import llm.core.model.LanguageModelResponse
import llm.core.model.TokenUsage
import llm.huggingface.model.HuggingFaceChatCompletionResponse

class HuggingFaceChatCompletionResponseMapper {
    fun toLanguageModelResponse(completion: HuggingFaceChatCompletionResponse): LanguageModelResponse {
        val content = completion.choices.firstOrNull()?.message?.content
            ?: error("Ответ Hugging Face API не содержит choices[0].message.content")
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
