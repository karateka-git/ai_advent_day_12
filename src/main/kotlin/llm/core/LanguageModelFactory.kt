package llm.core

import java.net.http.HttpClient
import java.util.Properties
import llm.huggingface.HuggingFaceLanguageModel
import llm.core.model.LanguageModelOption
import llm.timeweb.TimewebLanguageModel

private const val TIMEWEB_ID = "timeweb"
private const val HUGGING_FACE_ID = "huggingface"

object LanguageModelFactory {
    fun createDefault(config: Properties, httpClient: HttpClient): LanguageModel =
        create(
            modelId = availableModels(config).firstOrNull { it.isConfigured }?.id
                ?: error("Не найдена ни одна доступная модель. Проверьте токены в config/app.properties."),
            config = config,
            httpClient = httpClient
        )

    fun create(modelId: String, config: Properties, httpClient: HttpClient): LanguageModel {
        return when (modelId.lowercase()) {
            TIMEWEB_ID -> TimewebLanguageModel(
                httpClient = httpClient,
                agentId = config.getRequired("AGENT_ID"),
                userToken = config.getRequired("TIMEWEB_USER_TOKEN")
            )

            HUGGING_FACE_ID -> HuggingFaceLanguageModel(
                httpClient = httpClient,
                userToken = config.getRequired("HF_API_TOKEN")
            )

            else -> error(
                "Неизвестная модель: $modelId. " +
                    "Поддерживаются: timeweb, huggingface."
            )
        }
    }

    fun availableModels(config: Properties): List<LanguageModelOption> = listOf(
        LanguageModelOption(
            id = TIMEWEB_ID,
            displayName = "Timeweb / DeepSeek V3.2",
            isConfigured = config.hasValue("AGENT_ID") && config.hasValue("TIMEWEB_USER_TOKEN"),
            unavailableReason = "нужны AGENT_ID и TIMEWEB_USER_TOKEN"
        ),
        LanguageModelOption(
            id = HUGGING_FACE_ID,
            displayName = "Hugging Face / Qwen2.5-1.5B-Instruct",
            isConfigured = config.hasValue("HF_API_TOKEN"),
            unavailableReason = "нужен HF_API_TOKEN"
        )
    )
}

private fun Properties.getRequired(key: String): String =
    getProperty(key)?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("В config/app.properties отсутствует обязательное свойство '$key'.")

private fun Properties.hasValue(key: String): Boolean =
    !getProperty(key).isNullOrBlank()
