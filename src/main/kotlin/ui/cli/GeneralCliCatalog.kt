package ui.cli

import app.output.HelpCommandDescriptor
import app.output.HelpCommandGroup

/**
 * Каталог основных CLI-команд приложения, используемый help-экраном.
 */
object GeneralCliCatalog {
    val helpGroups: List<HelpCommandGroup> = listOf(
        HelpCommandGroup(
            title = "Общие команды",
            commands = listOf(
                HelpCommandDescriptor(CliCommands.HELP, "Показать общий список команд."),
                HelpCommandDescriptor(CliCommands.MODELS, "Показать доступные модели."),
                HelpCommandDescriptor("${CliCommands.USE} <model_id>", "Переключить модель и выбрать стратегию памяти."),
                HelpCommandDescriptor(CliCommands.USERS, "Показать список пользователей и активный профиль."),
                HelpCommandDescriptor(CliCommands.USER, "Показать активного пользователя."),
                HelpCommandDescriptor("${CliCommands.USER} create <id> [display_name]", "Создать нового пользователя."),
                HelpCommandDescriptor("${CliCommands.USER} use <id>", "Переключить активного пользователя."),
                HelpCommandDescriptor(CliCommands.CANCEL, "Отменить активное пошаговое добавление заметки."),
                HelpCommandDescriptor(CliCommands.CLEAR, "Очистить текущий контекст диалога."),
                HelpCommandDescriptor(CliCommands.EXIT, "Завершить работу."),
                HelpCommandDescriptor(CliCommands.QUIT, "Завершить работу.")
            )
        ),
        HelpCommandGroup(
            title = "Память",
            commands = listOf(
                HelpCommandDescriptor(CliCommands.MEMORY, "Показать все слои памяти."),
                HelpCommandDescriptor("${CliCommands.MEMORY} short", "Показать краткосрочную память."),
                HelpCommandDescriptor("${CliCommands.MEMORY} working", "Показать рабочую память."),
                HelpCommandDescriptor("${CliCommands.MEMORY} long", "Показать долговременную память."),
                HelpCommandDescriptor("${CliCommands.MEMORY} categories", "Показать доступные категории для ручной записи в память."),
                HelpCommandDescriptor("${CliCommands.MEMORY} add", "Запустить пошаговое добавление записи в память."),
                HelpCommandDescriptor("${CliCommands.MEMORY} add <working|long> <category> <текст>", "Добавить запись в рабочую или долговременную память."),
                HelpCommandDescriptor("${CliCommands.MEMORY} note edit <working|long> <id> text|category <значение>", "Изменить сохранённую запись памяти."),
                HelpCommandDescriptor("${CliCommands.MEMORY} note delete <working|long> <id>", "Удалить сохранённую запись памяти."),
                HelpCommandDescriptor(PendingMemoryCliCatalog.SHOW, "Показать pending-кандидаты на сохранение."),
                HelpCommandDescriptor(PendingMemoryCliCatalog.INFO, "Показать справку по командам для pending-памяти."),
                HelpCommandDescriptor(CliCommands.PROFILE, "Показать профиль активного пользователя."),
                HelpCommandDescriptor("${CliCommands.PROFILE} add", "Запустить пошаговое добавление профильной заметки."),
                HelpCommandDescriptor("${CliCommands.PROFILE} add <category> <текст>", "Добавить заметку в профиль активного пользователя."),
                HelpCommandDescriptor("${CliCommands.PROFILE} note edit <id> text|category <значение>", "Изменить заметку профиля активного пользователя."),
                HelpCommandDescriptor("${CliCommands.PROFILE} note delete <id>", "Удалить заметку профиля активного пользователя.")
            )
        ),
        HelpCommandGroup(
            title = "Ветки и checkpoint",
            commands = listOf(
                HelpCommandDescriptor(CliCommands.CHECKPOINT, "Создать checkpoint активной ветки."),
                HelpCommandDescriptor("${CliCommands.CHECKPOINT} <name>", "Создать именованный checkpoint."),
                HelpCommandDescriptor(CliCommands.BRANCHES, "Показать список веток."),
                HelpCommandDescriptor("${CliCommands.BRANCH} create <name>", "Создать новую ветку от последнего checkpoint."),
                HelpCommandDescriptor("${CliCommands.BRANCH} use <name>", "Переключиться на существующую ветку.")
            )
        )
    )
}
