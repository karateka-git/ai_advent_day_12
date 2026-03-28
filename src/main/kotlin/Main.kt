import agent.core.Agent
import agent.impl.MrAgent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.http.HttpClient
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import llm.core.LanguageModel
import llm.core.LanguageModelFactory
import llm.core.model.ChatRole

private const val CONFIG_FILE = "config/app.properties"
private val CONTEXT_OVERFLOW_PRESET_FILE = Path.of("config/conversations/context_overflow_preset.json")
private const val MODELS_COMMAND = "models"
private const val USE_COMMAND = "use"
private const val LOAD_OVERFLOW_PRESET_COMMAND = "load overflow_preset"

private val consoleReader = BufferedReader(
    InputStreamReader(System.`in`, detectConsoleCharset())
)
private val systemConsole = System.console()
private val tokenStatsFormatter = ConsoleTokenStatsFormatter()

fun main() {
    val config = loadConfig()
    val httpClient = HttpClient.newHttpClient()
    var languageModel: LanguageModel = LanguageModelFactory.createDefault(
        config = config,
        httpClient = httpClient
    )
    var agent: Agent<String> = MrAgent(languageModel = languageModel)

    println("Чат готов. Введите 'exit' или 'quit', чтобы завершить работу.")
    println(
        "Для просмотра моделей введите '$MODELS_COMMAND'. " +
            "Для переключения модели введите '$USE_COMMAND <id>'. " +
            "Для загрузки пресета переполнения введите '$LOAD_OVERFLOW_PRESET_COMMAND'."
    )
    printCurrentModelInfo(agent)

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

        if (prompt.equals(LOAD_OVERFLOW_PRESET_COMMAND, ignoreCase = true)) {
            try {
                agent.replaceContextFromFile(CONTEXT_OVERFLOW_PRESET_FILE)
                println("История текущей модели заменена содержимым context_overflow_preset.json.")
            } catch (error: Exception) {
                println("Не удалось загрузить context_overflow_preset.json: ${error.message}")
            }
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
                agent = MrAgent(languageModel = languageModel)
                println("Текущая модель изменена.")
                printCurrentModelInfo(agent)
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

            val loading = LoadingIndicator()
            loading.start()

            val response = try {
                agent.ask(prompt)
            } finally {
                loading.stop()
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

private fun printCurrentModelInfo(agent: Agent<String>) {
    println("Агент: ${agent.info.name}")
    println("Описание: ${agent.info.description}")
    println("Модель: ${agent.info.model}")
}

private fun formatModels(config: Properties, currentModel: LanguageModel): String =
    buildString {
        appendLine("Доступные модели:")
        LanguageModelFactory.availableModels(config).forEach { option ->
            val marker = if (option.id == currentModelId(currentModel)) "*" else " "
            append(marker)
            append(" ")
            append(option.id)
            append(" — ")
            append(option.displayName)
            if (!option.isConfigured) {
                append(" (недоступна: ${option.unavailableReason})")
            }
            appendLine()
        }
    }.trimEnd()

private fun currentModelId(languageModel: LanguageModel): String =
    when (languageModel.info.name) {
        "TimewebLanguageModel" -> "timeweb"
        "HuggingFaceLanguageModel" -> "huggingface"
        else -> languageModel.info.name.lowercase()
    }

private fun detectConsoleCharset(): Charset {
    val nativeEncoding = System.getProperty("native.encoding")
    return if (nativeEncoding.isNullOrBlank()) {
        Charset.defaultCharset()
    } else {
        Charset.forName(nativeEncoding)
    }
}

private fun readConsoleLine(): String? = systemConsole?.readLine() ?: consoleReader.readLine()

private fun loadConfig(): Properties {
    val configPath = Path.of(CONFIG_FILE)
    require(Files.exists(configPath)) {
        "Файл конфигурации $CONFIG_FILE не найден. Создайте его на основе config/app.properties.example."
    }

    return Properties().apply {
        Files.newInputStream(configPath).use(::load)
    }
}

private class LoadingIndicator {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start() {
        running.set(true)
        thread = Thread {
            var step = 0
            while (running.get()) {
                val dots = ".".repeat(step % 4)
                val padding = " ".repeat(3 - dots.length)
                print("\rАссистент думает$dots$padding")
                Thread.sleep(350)
                step++
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        thread?.join(500)
        print("\r${" ".repeat(40)}\r")
    }
}
