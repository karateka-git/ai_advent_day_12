package llm.core

import java.net.http.HttpClient
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageModelFactoryTest {
    @Test
    fun `availableModels marks configured and unconfigured models`() {
        val config = Properties().apply {
            setProperty("AGENT_ID", "agent")
            setProperty("TIMEWEB_USER_TOKEN", "token")
        }

        val models = LanguageModelFactory.availableModels(config)

        assertEquals(true, models.first { it.id == "timeweb" }.isConfigured)
        assertEquals(false, models.first { it.id == "huggingface" }.isConfigured)
    }

    @Test
    fun `createDefault chooses first configured model`() {
        val config = Properties().apply {
            setProperty("AGENT_ID", "agent")
            setProperty("TIMEWEB_USER_TOKEN", "token")
            setProperty("HF_API_TOKEN", "token")
        }

        val model = LanguageModelFactory.createDefault(config, HttpClient.newHttpClient())

        assertEquals("TimewebLanguageModel", model.info.name)
    }
}
