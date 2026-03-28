package llm.huggingface.tokenizer

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val TOKENIZER_RESOURCES_PATH = "tokenizers/qwen2.5-1.5b-instruct"

object Qwen25TokenizerAssets {
    val directory: Path by lazy {
        val tempDirectory = Files.createTempDirectory("qwen2.5-1.5b-instruct-tokenizer")
        copyResource("tokenizer.json", tempDirectory.resolve("tokenizer.json"))
        copyResource("tokenizer_config.json", tempDirectory.resolve("tokenizer_config.json"))
        copyResource("vocab.json", tempDirectory.resolve("vocab.json"))
        copyResource("merges.txt", tempDirectory.resolve("merges.txt"))
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
