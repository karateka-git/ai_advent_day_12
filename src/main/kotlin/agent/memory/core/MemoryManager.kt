package agent.memory.core

import agent.capability.AgentCapability
import agent.core.AgentTokenStats
import agent.memory.model.ManagedMemoryNoteEdit
import agent.memory.model.ManagedMemoryNoteResult
import agent.memory.model.PendingMemoryState
import agent.memory.model.PendingMemoryActionResult
import agent.memory.model.PendingMemoryEdit
import agent.memory.model.MemorySnapshot
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryState
import agent.memory.model.UserAccount
import java.nio.file.Path
import llm.core.model.ChatMessage

/**
 * Фасад для управления layered memory и эффективным prompt агента.
 */
interface MemoryManager {
    /**
     * Возвращает полный short-term диалог без prompt assembly.
     *
     * @return список сообщений short-term слоя.
     */
    fun currentConversation(): List<ChatMessage>

    /**
     * Оценивает локальный расход токенов для следующего пользовательского сообщения.
     *
     * @param userPrompt текст нового пользовательского сообщения.
     * @return локально рассчитанная статистика токенов.
     */
    fun previewTokenStats(userPrompt: String): AgentTokenStats

    /**
     * Добавляет пользовательское сообщение в short-term память, перераспределяет заметки по слоям
     * и возвращает эффективный prompt для модели.
     *
     * @param userPrompt текст нового пользовательского сообщения.
     * @return итоговый контекст, который должен быть отправлен в модель.
     */
    fun appendUserMessage(userPrompt: String): List<ChatMessage>

    /**
     * Сохраняет ответ ассистента и обновляет derived state активной short-term стратегии.
     *
     * @param content текст ответа ассистента.
     */
    fun appendAssistantMessage(content: String)

    /**
     * Очищает short-term память, сохраняя системное сообщение.
     */
    fun clear()

    /**
     * Заменяет текущее состояние памяти содержимым из указанного JSON-файла.
     *
     * @param sourcePath путь к persisted state.
     */
    fun replaceContextFromFile(sourcePath: Path)

    /**
     * Возвращает полный runtime-снимок layered memory.
     *
     * @return текущее состояние памяти.
     */
    fun memoryState(): MemoryState

    /**
     * Возвращает inspection-снимок памяти вместе с реально активной short-term стратегией.
     *
     * @return snapshot памяти для UI и отладочного вывода.
     */
    fun memorySnapshot(): MemorySnapshot

    /**
     * Возвращает все доступные пользовательские профили для текущего persisted state.
     */
    fun users(): List<UserAccount>

    /**
     * Возвращает активного пользователя, профиль которого автоматически попадает в prompt.
     */
    fun activeUser(): UserAccount

    /**
     * Создаёт нового пользователя для multi-user персонализации.
     *
     * @param userId стабильный идентификатор пользователя.
     * @param displayName отображаемое имя; если не задано, используется `userId`.
     * @return созданный пользователь.
     */
    fun createUser(userId: String, displayName: String? = null): UserAccount

    /**
     * Переключает активного пользователя текущей сессии.
     *
     * @param userId идентификатор профиля, который нужно активировать.
     * @return новый активный пользователь.
     */
    fun switchUser(userId: String): UserAccount

    /**
     * Возвращает user-scoped long-term заметки активного пользователя.
     */
    fun profileNotes(): List<MemoryNote>

    /**
     * Возвращает текущую очередь кандидатов, ожидающих подтверждения пользователя.
     */
    fun pendingMemory(): PendingMemoryState

    /**
     * Подтверждает часть pending-кандидатов или всю очередь целиком.
     *
     * @param candidateIds идентификаторы кандидатов; пустой список означает подтверждение всей очереди.
     * @return результат действия и обновлённая очередь pending-кандидатов.
     */
    fun approvePendingMemory(candidateIds: List<String> = emptyList()): PendingMemoryActionResult

    /**
     * Отклоняет часть pending-кандидатов или всю очередь целиком.
     *
     * @param candidateIds идентификаторы кандидатов; пустой список означает отклонение всей очереди.
     * @return результат действия и обновлённая очередь pending-кандидатов.
     */
    fun rejectPendingMemory(candidateIds: List<String> = emptyList()): PendingMemoryActionResult

    /**
     * Редактирует один pending-кандидат перед подтверждением.
     *
     * @param candidateId идентификатор изменяемого кандидата.
     * @param edit описание изменения.
     * @return обновлённая очередь pending-кандидатов и количество затронутых кандидатов.
     */
    fun editPendingMemory(candidateId: String, edit: PendingMemoryEdit): PendingMemoryState

    /**
     * Возвращает допустимые категории заметок для указанного durable memory слоя.
     */
    fun memoryCategories(layer: MemoryLayer): List<String>

    /**
     * Добавляет заметку в выбранный durable memory слой.
     */
    fun addMemoryNote(layer: MemoryLayer, category: String, content: String): ManagedMemoryNoteResult

    /**
     * Редактирует уже сохранённую durable-заметку.
     */
    fun editMemoryNote(layer: MemoryLayer, noteId: String, edit: ManagedMemoryNoteEdit): ManagedMemoryNoteResult

    /**
     * Удаляет заметку из выбранного durable memory слоя.
     */
    fun deleteMemoryNote(layer: MemoryLayer, noteId: String): ManagedMemoryNoteResult

    /**
     * Добавляет заметку в профиль активного пользователя.
     */
    fun addProfileNote(category: String, content: String): ManagedMemoryNoteResult

    /**
     * Редактирует существующую профильную заметку активного пользователя.
     */
    fun editProfileNote(noteId: String, edit: ManagedMemoryNoteEdit): ManagedMemoryNoteResult

    /**
     * Удаляет заметку из профиля активного пользователя.
     */
    fun deleteProfileNote(noteId: String): ManagedMemoryNoteResult

    /**
     * Возвращает capability активной стратегии памяти, если она поддерживается.
     *
     * @param capabilityType ожидаемый тип capability.
     * @return capability или `null`, если она недоступна.
     */
    fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability?
}
