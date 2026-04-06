package agent.memory.prompt

import agent.memory.model.MemoryState
import llm.core.LanguageModel
import llm.core.model.ChatMessage

/**
 * Собирает effective и preview context для текущего layered memory state.
 */
interface MemoryContextService {
    /**
     * Строит фактический prompt для текущего runtime-состояния памяти.
     *
     * @param systemPrompt базовый системный prompt агента.
     * @param state текущее layered memory state.
     * @return итоговый список сообщений для модели.
     */
    fun effectiveConversation(systemPrompt: String, state: MemoryState): List<ChatMessage>

    /**
     * Строит preview prompt для переданного состояния, как если бы оно было отправлено в модель.
     *
     * @param systemPrompt базовый системный prompt агента.
     * @param state состояние памяти для preview.
     * @return итоговый preview-контекст.
     */
    fun previewConversation(systemPrompt: String, state: MemoryState): List<ChatMessage>

    /**
     * Считает локальные prompt tokens для указанного состояния памяти.
     *
     * @param languageModel модель, чей token counter нужно использовать.
     * @param systemPrompt базовый системный prompt агента.
     * @param state состояние памяти, которое нужно превратить в prompt.
     * @return локально рассчитанное число токенов или `null`, если token counter недоступен.
     */
    fun countPromptTokens(languageModel: LanguageModel, systemPrompt: String, state: MemoryState): Int?
}

/**
 * Реализация context service на базе активной short-term стратегии и layered prompt assembler.
 */
class DefaultMemoryContextService(
    private val memoryStrategyProvider: () -> agent.memory.core.MemoryStrategy,
    private val promptAssembler: LayeredMemoryPromptAssembler = LayeredMemoryPromptAssembler()
) : MemoryContextService {
    /**
     * Собирает effective prompt из system prompt, working/long-term memory и short-term context стратегии.
     *
     * @param systemPrompt базовый системный prompt агента.
     * @param state текущее layered memory state.
     * @return итоговый список сообщений для модели.
     */
    override fun effectiveConversation(systemPrompt: String, state: MemoryState): List<ChatMessage> =
        promptAssembler.assemble(
            systemPrompt = systemPrompt,
            activeUser = state.activeUser(),
            longTermMemory = state.longTerm,
            workingMemory = state.working,
            shortTermContext = memoryStrategyProvider().effectiveContext(state)
        )

    /**
     * Для preview использует тот же assembled prompt, но работает на заранее подготовленном preview state.
     *
     * @param systemPrompt базовый системный prompt агента.
     * @param state состояние памяти для preview.
     * @return итоговый preview-контекст.
     */
    override fun previewConversation(systemPrompt: String, state: MemoryState): List<ChatMessage> =
        effectiveConversation(systemPrompt, state)

    /**
     * Считает токены assembled prompt для переданного состояния памяти.
     *
     * @param languageModel модель, чей token counter нужно использовать.
     * @param systemPrompt базовый системный prompt агента.
     * @param state состояние памяти для assembled prompt.
     * @return локально рассчитанное число токенов или `null`, если token counter отсутствует.
     */
    override fun countPromptTokens(languageModel: LanguageModel, systemPrompt: String, state: MemoryState): Int? =
        languageModel.tokenCounter?.countMessages(
            effectiveConversation(systemPrompt, state)
        )
}
