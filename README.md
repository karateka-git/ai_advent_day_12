# ai_advent_day_9

CLI-агент для диалога с LLM по HTTP API с поддержкой хранения истории, выбора стратегии памяти и сжатия старого контекста через summary.

## Что умеет проект

- запускать интерактивный чат в консоли;
- переключать LLM-провайдер между `timeweb` и `huggingface`;
- сохранять историю диалога по моделям в JSON;
- выбирать стратегию памяти перед стартом чата;
- работать без сжатия истории или со сжатием через rolling summary;
- считать локальную оценку токенов до запроса;
- показывать экономию токенов после сжатия контекста.

## Быстрый старт

1. Скопируйте `config/app.properties.example` в `config/app.properties`.
2. Заполните токены для нужного провайдера.
3. Соберите и запустите проект:

```powershell
.\gradlew.bat build
.\gradlew.bat installDist
.\build\install\ai_advent_day_9\bin\ai_advent_day_9.bat
```

## Конфигурация

### Timeweb

- `AGENT_ID`
- `TIMEWEB_USER_TOKEN`

### Hugging Face

- `HF_API_TOKEN`

Если токены заданы сразу для нескольких провайдеров, по умолчанию будет выбрана первая доступная модель из списка, который строит [LanguageModelFactory.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/llm/core/LanguageModelFactory.kt).

## Команды в чате

- `clear` — очищает контекст, оставляя системное сообщение.
- `models` — показывает доступные модели и их статус.
- `use <id>` — переключает текущую модель.
- `exit` / `quit` — завершает приложение.

## Стратегии памяти

Перед стартом чата CLI предлагает выбрать одну из стратегий памяти:

- `Без сжатия`
  Агент отправляет в модель всю сохранённую историю как есть.
- `Сжатие через summary`
  Старые сообщения сворачиваются в rolling summary, а последние сообщения остаются без изменений.

Создание стратегий централизовано в [MemoryStrategyFactory.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/memory/MemoryStrategyFactory.kt).

## Пользовательский сценарий

Ниже показано, как пользовательское действие проходит через основные части системы.

```mermaid
sequenceDiagram
    participant U as "User"
    participant M as "Main.kt"
    participant A as "MrAgent"
    participant MM as "DefaultMemoryManager"
    participant MS as "MemoryStrategy"
    participant LM as "LanguageModel"
    participant S as "JsonConversationStore"

    U->>M: "Запуск приложения"
    M->>LM: createDefault(...)
    M->>U: "Выберите стратегию памяти"
    U->>M: "summary_compression"
    M->>A: createAgent(...)

    U->>M: "Текст запроса"
    M->>A: previewTokenStats(prompt)
    A->>MM: previewTokenStats(prompt)
    MM->>MS: effectiveContext(...)
    M->>A: ask(prompt)
    A->>MM: appendUserMessage(prompt)
    MM->>MS: refreshState(...)
    MM->>S: saveState(...)
    A->>LM: complete(effectiveContext)
    LM-->>A: model response
    A->>MM: appendAssistantMessage(response)
    MM->>S: saveState(...)
    A-->>M: AgentResponse
    M-->>U: "Ответ + статистика токенов"
```

## Как используются программные части

### Точка входа

- [Main.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/Main.kt)
  Управляет CLI-циклом, выбором модели, выбором стратегии памяти и отображением статусов пользователю.

### Агент

- [MrAgent.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/impl/MrAgent.kt)
  Оркестратор одного хода диалога. Получает prompt, просит memory manager подготовить контекст, отправляет запрос в LLM и сохраняет ответ.

### Память и сжатие

- [DefaultMemoryManager.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/memory/DefaultMemoryManager.kt)
  Хранит текущее состояние памяти, сохраняет его в storage и считает токены до и после компрессии.
- [MemoryStrategy.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/memory/MemoryStrategy.kt)
  Контракт, определяющий, как формируется effective context.
- [NoCompressionMemoryStrategy.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/memory/NoCompressionMemoryStrategy.kt)
  Отдаёт всю историю без изменений.
- [SummaryCompressionMemoryStrategy.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/memory/SummaryCompressionMemoryStrategy.kt)
  Сворачивает старые сообщения в rolling summary.
- [LlmConversationSummarizer.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/memory/summarizer/LlmConversationSummarizer.kt)
  Использует LLM для построения summary старой части диалога.

### Lifecycle и CLI-статусы

- [AgentLifecycleListener.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/lifecycle/AgentLifecycleListener.kt)
  Контракт для событий прогрева модели и сжатия контекста.
- [ConsoleAgentLifecycleListener.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/lifecycle/ConsoleAgentLifecycleListener.kt)
  Показывает загрузочные статусы и выводит экономию токенов после сжатия.
- [LoadingIndicator.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/lifecycle/LoadingIndicator.kt)
  Общий индикатор загрузки с поддержкой вложенных статусов.

### Хранилище

- [JsonConversationStore.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/storage/JsonConversationStore.kt)
  Читает и записывает состояние памяти в `config/conversations/`.
- [ConversationMemoryState.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/storage/model/ConversationMemoryState.kt)
  JSON-модель состояния памяти на диске.

### LLM-провайдеры

- [LanguageModelFactory.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/llm/core/LanguageModelFactory.kt)
  Создаёт провайдера по id и конфигу.
- [TimewebLanguageModel.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/llm/timeweb/TimewebLanguageModel.kt)
  Реализация для Timeweb.
- [HuggingFaceLanguageModel.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/llm/huggingface/HuggingFaceLanguageModel.kt)
  Реализация для Hugging Face.

### Токены

- [TokenCounter.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/llm/core/tokenizer/TokenCounter.kt)
  Общий контракт локального подсчёта токенов.
- [ConsoleTokenStatsFormatter.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/ConsoleTokenStatsFormatter.kt)
  Форматирует предпросмотр и фактическую статистику токенов для CLI.

## Как читать проект

Если хочешь быстро понять поток управления, удобнее идти в таком порядке:

1. [Main.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/Main.kt)
2. [MrAgent.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/impl/MrAgent.kt)
3. [DefaultMemoryManager.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/memory/DefaultMemoryManager.kt)
4. [MemoryStrategyFactory.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/memory/MemoryStrategyFactory.kt)
5. [SummaryCompressionMemoryStrategy.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/memory/SummaryCompressionMemoryStrategy.kt)
6. [LlmConversationSummarizer.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/memory/summarizer/LlmConversationSummarizer.kt)
7. [JsonConversationStore.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/agent/storage/JsonConversationStore.kt)
8. [LanguageModelFactory.kt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_9/src/main/kotlin/llm/core/LanguageModelFactory.kt)

## Токены и компрессия

Во время работы CLI показывает:

- оценку токенов перед запросом;
- индикатор долгих операций;
- сообщение о сжатии контекста;
- итог после компрессии:
  `Контекст сжат: X -> Y токенов, экономия Z`

Это позволяет сравнивать режимы `Без сжатия` и `Сжатие через summary` на одном и том же пользовательском сценарии.

## Тесты

Запуск тестов:

```powershell
.\gradlew.bat test
```

Основные проверяемые области:

- фабрика моделей;
- storage и мапперы;
- стратегии памяти;
- summarizer;
- форматирование токеновой статистики;
- поведение memory manager при компрессии.

## IDE

Для навигации по коду и запуска тестов удобнее всего открыть проект в IntelliJ IDEA Community Edition.
