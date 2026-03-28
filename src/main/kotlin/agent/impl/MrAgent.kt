package agent.impl

import agent.core.Agent
import agent.core.AgentInfo
import agent.core.AgentResponse
import agent.core.AgentTokenStats
import agent.format.ResponseFormat
import agent.format.TextResponseFormat
import agent.storage.JsonConversationStore
import agent.storage.mapper.ChatMessageConversationMapper
import java.nio.file.Path
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class MrAgent(
    private val languageModel: LanguageModel,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) : Agent<String> {
    private val conversationMapper = ChatMessageConversationMapper()
    private val conversationStore = JsonConversationStore.forLanguageModel(languageModel)

    override val responseFormat: ResponseFormat<String> = TextResponseFormat

    private val conversation = loadConversation().toMutableList()

    override val info = AgentInfo(
        name = "MrAgent",
        description = "CLI-агент для диалога с LLM через HTTP API.",
        model = languageModel.info.model
    )

    override fun previewTokenStats(userPrompt: String): AgentTokenStats {
        val historyTokens = languageModel.tokenCounter?.countMessages(conversation)
        val userPromptTokens = languageModel.tokenCounter?.countText(userPrompt)
        val promptTokensLocal = languageModel.tokenCounter?.countMessages(
            conversation + ChatMessage(role = ChatRole.USER, content = userPrompt)
        )

        return AgentTokenStats(
            historyTokens = historyTokens,
            promptTokensLocal = promptTokensLocal,
            userPromptTokens = userPromptTokens
        )
    }

    override fun ask(userPrompt: String): AgentResponse<String> {
        val preview = previewTokenStats(userPrompt)
        conversation += ChatMessage(role = ChatRole.USER, content = userPrompt)
        saveConversation()

        val modelResponse = languageModel.complete(conversation)

        conversation += ChatMessage(role = ChatRole.ASSISTANT, content = modelResponse.content)
        saveConversation()

        return AgentResponse(
            content = responseFormat.parse(modelResponse.content),
            tokenStats = AgentTokenStats(
                historyTokens = preview.historyTokens,
                promptTokensLocal = preview.promptTokensLocal,
                userPromptTokens = preview.userPromptTokens,
                apiUsage = modelResponse.usage
            )
        )
    }

    override fun clearContext() {
        conversation.clear()
        conversation += createSystemMessage()
        saveConversation()
    }

    override fun replaceContextFromFile(sourcePath: Path) {
        val importedMessages = JsonConversationStore(sourcePath).load()
            .map(conversationMapper::fromStoredMessage)

        require(importedMessages.isNotEmpty()) {
            "Файл истории $sourcePath пустой или не содержит сообщений."
        }

        conversation.clear()
        conversation += importedMessages
        saveConversation()
    }

    private fun loadConversation(): List<ChatMessage> {
        val savedConversation = conversationStore.load()
            .map(conversationMapper::fromStoredMessage)
        if (savedConversation.isNotEmpty()) {
            return savedConversation
        }

        val initialConversation = listOf(createSystemMessage())
        conversationStore.save(initialConversation.map(conversationMapper::toStoredMessage))
        return initialConversation
    }

    private fun saveConversation() {
        conversationStore.save(conversation.map(conversationMapper::toStoredMessage))
    }

    private fun createSystemMessage(): ChatMessage =
        ChatMessage(
            role = ChatRole.SYSTEM,
            content = buildSystemPrompt()
        )

    private fun buildSystemPrompt(): String =
        "$systemPrompt\n\nТребования к формату ответа:\n${responseFormat.formatInstruction}"

    companion object {
        private const val DEFAULT_SYSTEM_PROMPT =
            "Ты полезный ассистент. Отвечай кратко, если пользователь не просит подробнее."
    }
}
