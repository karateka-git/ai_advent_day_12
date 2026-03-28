import agent.core.Agent
import agent.impl.MrAgent
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.ConsoleAgentLifecycleListener
import agent.lifecycle.LoadingIndicator
import agent.memory.MemoryStrategyFactory
import agent.memory.MemoryStrategyOption
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.http.HttpClient
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import llm.core.LanguageModel
import llm.core.LanguageModelFactory
import llm.core.model.ChatRole

private const val CONFIG_FILE = "config/app.properties"
private const val MODELS_COMMAND = "models"
private const val USE_COMMAND = "use"

private val consoleReader = BufferedReader(
    InputStreamReader(System.`in`, detectConsoleCharset())
)
private val systemConsole = System.console()
private val tokenStatsFormatter = ConsoleTokenStatsFormatter()
private val loadingIndicator = LoadingIndicator()

/**
 * Точка входа CLI-приложения.
 *
 * Приложение загружает конфигурацию, выбирает LLM-провайдер, предлагает пользователю выбрать
 * стратегию памяти и затем запускает интерактивный цикл чата.
 */
fun main() {
    val config = loadConfig()
    val httpClient = HttpClient.newHttpClient()
    val lifecycleListener: AgentLifecycleListener = ConsoleAgentLifecycleListener(loadingIndicator)
    var languageModel: LanguageModel = LanguageModelFactory.createDefault(
        config = config,
        httpClient = httpClient
    )
    warmUpTokenCounter(languageModel, lifecycleListener)

    val selectedMemoryStrategyOption = selectMemoryStrategyOption()
    var agent: Agent<String> = createAgent(
        languageModel = languageModel,
        lifecycleListener = lifecycleListener,
        strategyId = selectedMemoryStrategyOption.id
    )

    println("Чат готов. Введите 'exit' или 'quit', чтобы завершить работу.")
    println(
        "Для просмотра моделей введите '$MODELS_COMMAND'. " +
            "Для переключения модели введите '$USE_COMMAND <id>'."
    )
    printCurrentAgentInfo(agent, selectedMemoryStrategyOption)

    while (true) {
        print("${ChatRole.USER.displayName}: ")
        val prompt = readConsoleLine()?.trim() ?: break

        if (prompt.isEmpty()) {
            continue
        }

        if (prompt.equals("exit", ignoreCase = true) || prompt.equals("quit", ignoreCase = true)) {
            println("Чат завершён.")
            break
        }

        if (prompt.equals("clear", ignoreCase = true)) {
            agent.clearContext()
            println("Контекст очищен. Системное сообщение сохранено.")
            continue
        }

        if (prompt.equals(MODELS_COMMAND, ignoreCase = true)) {
            println(formatModels(config, languageModel))
            continue
        }

        if (prompt.startsWith("$USE_COMMAND ", ignoreCase = true)) {
            val requestedModelId = prompt.substringAfter(' ').trim()
            try {
                languageModel = LanguageModelFactory.create(
                    modelId = requestedModelId,
                    config = config,
                    httpClient = httpClient
                )
                warmUpTokenCounter(languageModel, lifecycleListener)
                agent = createAgent(
                    languageModel = languageModel,
                    lifecycleListener = lifecycleListener,
                    strategyId = selectedMemoryStrategyOption.id
                )
                println("Текущая модель изменена.")
                printCurrentAgentInfo(agent, selectedMemoryStrategyOption)
            } catch (error: Exception) {
                println("Не удалось переключить модель: ${error.message}")
            }
            continue
        }

        try {
            tokenStatsFormatter.formatPreview(agent.previewTokenStats(prompt))?.let { preview ->
                println()
                println(preview)
                println()
            }

            val response = try {
                loadingIndicator.start("Ассистент думает")
                agent.ask(prompt)
            } finally {
                loadingIndicator.stop()
            }

            println()
            println("${ChatRole.ASSISTANT.displayName}: ${response.content}")
            tokenStatsFormatter.formatResponse(response.tokenStats)?.let {
                println()
                println(it)
            }
            println()
        } catch (error: Exception) {
            println("Не удалось выполнить запрос: ${error.message}")
        }
    }
}

/**
 * Создаёт новый экземпляр агента для выбранной модели и стратегии памяти.
 */
private fun createAgent(
    languageModel: LanguageModel,
    lifecycleListener: AgentLifecycleListener,
    strategyId: String
): Agent<String> =
    MrAgent(
        languageModel = languageModel,
        lifecycleListener = lifecycleListener,
        memoryStrategy = MemoryStrategyFactory.create(
            strategyId = strategyId,
            languageModel = languageModel,
            lifecycleListener = lifecycleListener
        )
    )

/**
 * Предлагает пользователю выбрать одну из доступных стратегий памяти перед стартом чата.
 */
private fun selectMemoryStrategyOption(): MemoryStrategyOption {
    val options = MemoryStrategyFactory.availableOptions()

    println("Выберите стратегию памяти перед стартом агента:")
    options.forEachIndexed { index, option ->
        println("${index + 1}. ${option.displayName} - ${option.description}")
    }

    while (true) {
        print("Введите номер стратегии [1-${options.size}]: ")
        val selection = readConsoleLine()?.trim().orEmpty()
        val index = selection.toIntOrNull()

        if (index != null && index in 1..options.size) {
            val option = options[index - 1]
            println("Выбрана стратегия: ${option.displayName}")
            return option
        }

        println("Некорректный выбор. Попробуйте ещё раз.")
    }
}

/**
 * Выводит текущую конфигурацию модели и памяти для активной сессии.
 */
private fun printCurrentAgentInfo(agent: Agent<String>, strategy: MemoryStrategyOption) {
    println("Агент: ${agent.info.name}")
    println("Описание: ${agent.info.description}")
    println("Модель: ${agent.info.model}")
    println("Стратегия памяти: ${strategy.displayName}")
}

/**
 * Форматирует список доступных языковых моделей и помечает активную.
 */
private fun formatModels(config: Properties, currentModel: LanguageModel): String =
    buildString {
        appendLine("Доступные модели:")
        LanguageModelFactory.availableModels(config).forEach { option ->
            val marker = if (option.id == currentModelId(currentModel)) "*" else " "
            append(marker)
            append(" ")
            append(option.id)
            append(" - ")
            append(option.displayName)
            if (!option.isConfigured) {
                append(" (недоступна: ${option.unavailableReason})")
            }
            appendLine()
        }
    }.trimEnd()

/**
 * Преобразует экземпляр модели времени выполнения обратно в идентификатор, используемый в CLI.
 */
private fun currentModelId(languageModel: LanguageModel): String =
    when (languageModel.info.name) {
        "TimewebLanguageModel" -> "timeweb"
        "HuggingFaceLanguageModel" -> "huggingface"
        else -> languageModel.info.name.lowercase()
    }

/**
 * Принудительно прогревает лениво создаваемый токенизатор перед стартом чата, чтобы первая
 * оценка токенов не была слишком долгой.
 */
private fun warmUpTokenCounter(
    languageModel: LanguageModel,
    lifecycleListener: AgentLifecycleListener
) {
    lifecycleListener.onModelWarmupStarted()
    try {
        languageModel.tokenCounter?.countText("")
    } finally {
        lifecycleListener.onModelWarmupFinished()
    }
}

/**
 * Определяет кодировку, используемую текущей консольной сессией.
 */
private fun detectConsoleCharset(): Charset {
    val nativeEncoding = System.getProperty("native.encoding")
    return if (nativeEncoding.isNullOrBlank()) {
        Charset.defaultCharset()
    } else {
        Charset.forName(nativeEncoding)
    }
}

/**
 * Читает одну строку из консоли, предпочитая нативный API консоли, если он доступен.
 */
private fun readConsoleLine(): String? = systemConsole?.readLine() ?: consoleReader.readLine()

/**
 * Загружает свойства приложения из локального файла конфигурации.
 */
private fun loadConfig(): Properties {
    val configPath = Path.of(CONFIG_FILE)
    require(Files.exists(configPath)) {
        "Файл конфигурации $CONFIG_FILE не найден. Создайте его на основе config/app.properties.example."
    }

    return Properties().apply {
        Files.newInputStream(configPath).use(::load)
    }
}
