[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ScenarioFile,

    [string]$OutputFile,

    [switch]$ClearConversations
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$batPath = Join-Path $repoRoot "build\install\ai_advent_day_11\bin\ai_advent_day_11.bat"
$conversationsDir = Join-Path $repoRoot "config\conversations"

if (-not (Test-Path -LiteralPath $batPath)) {
    throw "Built bat file not found: $batPath. Run .\\gradlew.bat build and .\\gradlew.bat installDist first."
}

$resolvedScenarioFile = (Resolve-Path -LiteralPath $ScenarioFile).Path
if (-not $OutputFile) {
    $scenarioName = [System.IO.Path]::GetFileNameWithoutExtension($resolvedScenarioFile)
    $OutputFile = Join-Path $repoRoot "build\smoke-check\$scenarioName-output.txt"
}

$resolvedOutputFile = [System.IO.Path]::GetFullPath($OutputFile)
$outputDir = Split-Path -Parent $resolvedOutputFile
if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

if ($ClearConversations -and (Test-Path -LiteralPath $conversationsDir)) {
    Get-ChildItem -LiteralPath $conversationsDir -File | Remove-Item -Force
}

[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "cmd.exe"
$psi.Arguments = "/c chcp 65001>nul && `"$batPath`""
$psi.WorkingDirectory = $repoRoot
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8
$psi.Environment["JAVA_TOOL_OPTIONS"] = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $psi
$exitCode = $null

try {
    $null = $process.Start()

    $scenarioContent = Get-Content -Encoding UTF8 -LiteralPath $resolvedScenarioFile -Raw
    $process.StandardInput.Write($scenarioContent)
    if (-not $scenarioContent.EndsWith("`n")) {
        $process.StandardInput.WriteLine()
    }
    $process.StandardInput.Close()

    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $exitCode = $process.ExitCode
}
finally {
    if ($process -and -not $process.HasExited) {
        $process.Kill()
    }
    $process.Dispose()
}

$combinedOutput = if ([string]::IsNullOrWhiteSpace($stderr)) {
    $stdout
} else {
    "$stdout`r`n[stderr]`r`n$stderr"
}

Set-Content -LiteralPath $resolvedOutputFile -Value $combinedOutput -Encoding UTF8

Write-Host "Scenario: $resolvedScenarioFile"
Write-Host "Output saved to: $resolvedOutputFile"
Write-Host "Exit code: $exitCode"

if ($exitCode -ne 0) {
    throw "Scripted session finished with exit code $exitCode."
}

