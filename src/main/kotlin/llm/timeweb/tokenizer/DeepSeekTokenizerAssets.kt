package llm.timeweb.tokenizer

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val TOKENIZER_RESOURCES_PATH = "tokenizers/deepseek-v3.2"

object DeepSeekTokenizerAssets {
    val directory: Path by lazy {
        val tempDirectory = Files.createTempDirectory("deepseek-v3.2-tokenizer")
        copyResource("tokenizer.json", tempDirectory.resolve("tokenizer.json"))
        copyResource("tokenizer_config.json", tempDirectory.resolve("tokenizer_config.json"))
        tempDirectory.toFile().deleteOnExit()
        tempDirectory
    }

    private fun copyResource(resourceName: String, targetPath: Path) {
        val resourcePath = "$TOKENIZER_RESOURCES_PATH/$resourceName"
        val resourceStream = checkNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Не найден resource $resourcePath с локальными assets токенизатора."
        }

        resourceStream.use { input ->
            Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }

        targetPath.toFile().deleteOnExit()
    }
}
