package app.output

import agent.core.AgentInfo
import agent.core.AgentTokenStats
import agent.core.BranchCheckpointInfo
import agent.core.BranchInfo
import agent.core.BranchingStatus
import agent.lifecycle.ContextCompressionStats
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemorySnapshot
import agent.memory.model.PendingMemoryState
import agent.memory.model.UserAccount
import agent.memory.strategy.MemoryStrategyOption
import llm.core.model.ChatRole
import llm.core.model.LanguageModelOption

/**
 * Описание одной пользовательской CLI-команды для справки и help-экранов.
 *
 * @property command полная форма команды.
 * @property description короткое пояснение назначения команды.
 */
data class HelpCommandDescriptor(
    val command: String,
    val description: String
)

/**
 * Группа CLI-команд для более читаемого help-экрана.
 *
 * @property title название смыслового блока.
 * @property commands команды, входящие в этот блок.
 */
data class HelpCommandGroup(
    val title: String,
    val commands: List<HelpCommandDescriptor>
)

/**
 * Типизированные события application-слоя, которые описывают, что должно быть показано
 * пользователю, не привязывая приложение к конкретному UI.
 */
sealed interface AppEvent {
    data object SessionStarted : AppEvent

    /**
     * Список доступных CLI-команд, который UI может показать отдельным help-блоком.
     */
    data class CommandsAvailable(
        val title: String,
        val groups: List<HelpCommandGroup>
    ) : AppEvent

    data class MemoryStrategySelectionRequested(
        val options: List<MemoryStrategyOption>
    ) : AppEvent

    data class MemoryStrategySelectionPromptRequested(
        val optionsCount: Int
    ) : AppEvent

    data class MemoryStrategySelected(
        val option: MemoryStrategyOption
    ) : AppEvent

    data object MemoryStrategySelectionRejected : AppEvent

    data class AgentInfoAvailable(
        val info: AgentInfo,
        val strategy: MemoryStrategyOption
    ) : AppEvent

    data class ModelsAvailable(
        val options: List<LanguageModelOption>,
        val currentModelId: String
    ) : AppEvent

    /**
     * Снимок layered memory для пользовательской инспекции.
     *
     * @property snapshot текущее состояние памяти вместе с активной стратегией.
     * @property selectedLayer конкретный слой для просмотра; `null` означает полный вывод.
     */
    data class MemoryStateAvailable(
        val snapshot: MemorySnapshot,
        val selectedLayer: MemoryLayer?
    ) : AppEvent

    data class UsersAvailable(
        val users: List<UserAccount>,
        val activeUserId: String
    ) : AppEvent

    data class UserProfileAvailable(
        val user: UserAccount,
        val notes: List<MemoryNote>
    ) : AppEvent

    /**
     * Текущая очередь pending-кандидатов.
     *
     * @property pending кандидаты, ожидающие решения пользователя.
     * @property reason необязательное пояснение, почему этот список был показан.
     */
    data class PendingMemoryAvailable(
        val pending: PendingMemoryState,
        val reason: String? = null
    ) : AppEvent

    /**
     * Результат команды, которая изменила pending-кандидаты.
     *
     * @property message человекочитаемый итог действия.
     * @property pending обновлённая очередь pending-кандидатов.
     */
    data class PendingMemoryActionCompleted(
        val message: String,
        val pending: PendingMemoryState
    ) : AppEvent

    /**
     * Справка по командам работы с pending-памятью.
     *
     * @property commands доступные команды и их пояснения.
     */
    data class PendingMemoryCommandsAvailable(
        val commands: List<HelpCommandDescriptor>
    ) : AppEvent

    data class CheckpointCreated(
        val info: BranchCheckpointInfo
    ) : AppEvent

    data class BranchCreated(
        val info: BranchInfo
    ) : AppEvent

    data class BranchSwitched(
        val info: BranchInfo
    ) : AppEvent

    data class BranchStatusAvailable(
        val status: BranchingStatus
    ) : AppEvent

    data class UserInputPrompt(
        val role: ChatRole
    ) : AppEvent

    data class AssistantResponseAvailable(
        val role: ChatRole,
        val content: String,
        val tokenStats: AgentTokenStats
    ) : AppEvent

    data class TokenPreviewAvailable(
        val tokenStats: AgentTokenStats
    ) : AppEvent

    data object ContextCleared : AppEvent

    data object ModelChanged : AppEvent

    data class ModelSwitchFailed(
        val details: String?
    ) : AppEvent

    data class RequestFailed(
        val details: String?
    ) : AppEvent

    data class CommandCompleted(
        val message: String
    ) : AppEvent

    data object SessionFinished : AppEvent

    data object ModelWarmupStarted : AppEvent

    data object ModelWarmupFinished : AppEvent

    data object ModelRequestStarted : AppEvent

    data object ModelRequestFinished : AppEvent

    data object ContextCompressionStarted : AppEvent

    data class ContextCompressionFinished(
        val stats: ContextCompressionStats
    ) : AppEvent
}
