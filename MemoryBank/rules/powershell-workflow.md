# PowerShell Workflow

## Команда для запуска проекта в новом окне

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','Set-Location ''C:\Users\compadre\Downloads\Projects\AiAdvent\day_9''; .\build\install\ai_advent_day_9\bin\ai_advent_day_9.bat'
```

## Что делает эта команда

Эта команда открывает новое окно PowerShell.

После открытия окна она:

1. переходит в папку проекта `C:\Users\compadre\Downloads\Projects\AiAdvent\day_9`;
2. запускает собранный bat-файл проекта:

```powershell
.\build\install\ai_advent_day_9\bin\ai_advent_day_9.bat
```

Ключ `-NoExit` оставляет окно PowerShell открытым после запуска, чтобы можно было работать с интерактивным приложением в этом окне.

## Правила для Codex

В этом проекте нужно различать две отдельные команды пользователя.

### Команда: `собери проект`

Если пользователь пишет `собери проект`, по умолчанию это означает:

1. выполнить сборку:

```powershell
.\gradlew.bat build
.\gradlew.bat installDist
```

2. после этого открыть новое окно PowerShell и запустить проект командой:

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','Set-Location ''C:\Users\compadre\Downloads\Projects\AiAdvent\day_9''; .\build\install\ai_advent_day_9\bin\ai_advent_day_9.bat'
```

То есть `собери проект` в этом репозитории означает:
`build -> installDist -> запуск проекта в новом окне PowerShell`.

### Команда: `запусти проект`

Если пользователь пишет `запусти проект`, по умолчанию это означает только запуск уже собранной версии без предварительной сборки.

Исключение:

Если видно, что сборка давно не выполнялась, артефакты отсутствуют или есть основания считать текущую сборку несвежей, команду `запусти проект` нужно трактовать как `собери проект`.

Нужно сразу выполнить:

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','Set-Location ''C:\Users\compadre\Downloads\Projects\AiAdvent\day_9''; .\build\install\ai_advent_day_9\bin\ai_advent_day_9.bat'
```

То есть `запусти проект` в этом репозитории означает:
`только запуск в новом окне PowerShell без build и без installDist`.

### Дополнение

Если пользователь не уточняет и просит открыть проект, открыть терминал с проектом или формулирует похожую просьбу, по умолчанию нужно трактовать это ближе к команде `запусти проект`, то есть как запуск уже собранной версии в отдельном окне PowerShell.
