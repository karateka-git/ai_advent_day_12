# ai_advent_day_9

Простой CLI-агент для работы с LLM по HTTP API.

## Что умеет проект

- принимает запрос пользователя из консоли;
- отправляет запрос в LLM через HTTP API;
- получает ответ и выводит его в CLI;
- инкапсулирует логику работы с моделью в отдельном агенте `MrAgent`;
- сохраняет историю диалога в JSON и восстанавливает её после перезапуска;
- работает с языковой моделью через общий интерфейс `LanguageModel`.

## Архитектура

В проекте есть общий контракт агента и отдельный слой языковой модели:

- `Agent<T>` — общий интерфейс агента;
- `ResponseFormat<T>` — описание ожидаемого формата ответа;
- `TextResponseFormat` — текстовый формат ответа;
- `MrAgent` — агент, который реализует `Agent<String>`;
- `LanguageModel` — общий интерфейс для разных LLM;
- `TimewebLanguageModel` — текущая реализация языковой модели для Timeweb API;
- `HuggingFaceLanguageModel` — реализация языковой модели для Hugging Face Inference API c моделью `Qwen/Qwen2.5-1.5B-Instruct`;
- `ConversationStore` — общий контракт хранения истории диалога;
- `JsonConversationStore` — JSON-реализация хранения контекста;
- `ConversationMapper` — прослойка между хранилищем и рабочими сообщениями агента;
- `ChatMessageConversationMapper` — маппер между `StoredMessage` и `ChatMessage`;
- `Main.kt` — CLI-обвязка, которая читает ввод пользователя, создаёт языковую модель и вызывает агента.

## Структура папок

- `src/main/kotlin/agent/core` — базовые контракты агента;
- `src/main/kotlin/agent/format` — форматы ответа агента;
- `src/main/kotlin/agent/impl` — реализации агентов;
- `src/main/kotlin/agent/storage` — хранение контекста;
- `src/main/kotlin/agent/storage/mapper` — мапперы между storage-моделями и сообщениями агента;
- `src/main/kotlin/agent/storage/model` — модели для хранения истории;
- `src/main/kotlin/llm/core` — общие контракты и модели языковой модели;
- `src/main/kotlin/llm/huggingface` — реализация `HuggingFaceLanguageModel`;
- `src/main/kotlin/llm/timeweb` — реализация `TimewebLanguageModel`;
- `src/main/kotlin/llm/timeweb/model` — DTO для Timeweb API;
- `src/test/kotlin` — тесты.

## Настройка

1. Скопируйте `config/app.properties.example` в `config/app.properties`.
2. Для `timeweb` заполните `AGENT_ID` и `TIMEWEB_USER_TOKEN`.
3. Для `huggingface` заполните `HF_API_TOKEN`.

### Провайдеры

- по умолчанию приложение выбирает первую доступную модель;
- список доступных моделей можно посмотреть командой `models`;
- переключить текущую модель можно командой `use <id>`;
- сейчас доступны `timeweb` и `huggingface`;
- параметры модели, провайдера и базового URL зашиты в коде реализации `HuggingFaceLanguageModel`.

## Сборка

```powershell
.\gradlew.bat build
```

## Запуск

Рекомендуемый запуск для Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat installDist
.\build\install\ai_advent_day_9\bin\ai_advent_day_9.bat
```

## Команды в чате

- вводите сообщения в консоли, чтобы продолжить диалог;
- введите `clear`, чтобы очистить контекст и начать новый диалог, сохранив системное сообщение;
- введите `models`, чтобы посмотреть список доступных моделей;
- введите `use <id>`, чтобы переключить текущую модель;
- введите `load overflow_preset`, чтобы заменить историю текущей модели содержимым `config/conversations/context_overflow_preset.json`;
- введите `exit` или `quit`, чтобы завершить работу.

## Токены

- перед отправкой запроса CLI показывает локальную оценку токенов для текущего сообщения, истории и полного запроса;
- после ответа модели CLI показывает фактические токены ответа, полученные от API, если провайдер их возвращает.

## Сохранение контекста

- история диалога сохраняется в JSON-файл;
- для каждой модели используется свой отдельный файл в `config/conversations/`;
- в `config/conversations/context_overflow_preset.json` лежит пресет для тестирования переполнения окна контекста;
- store работает со своими моделями `StoredMessage`;
- агент работает с `ChatMessage`;
- переход между ними выполняется через mapper-слой.

## IDE

Для просмотра и навигации по коду удобнее всего открыть проект в `IntelliJ IDEA Community Edition`.
