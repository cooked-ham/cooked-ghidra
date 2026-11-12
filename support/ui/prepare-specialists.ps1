param(
    [string]$Distribution = 'E:\cooked-ghidra\build\verification\release\ghidra_12.3_DEV',
    [string]$Output = 'E:\cooked-ghidra\build\verification\graphite-specialists',
    [string]$Python = 'C:\Python315\python.exe',
    [switch]$Repository,
    [ValidateRange(1024,65533)][int]$RepositoryPort = 14150
)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $Output) { throw 'Use a new output directory to preserve existing fixtures.' }
$null = New-Item -ItemType Directory -Path $Output
$Output = (Resolve-Path -LiteralPath $Output).Path
& $Python (Join-Path $PSScriptRoot 'specialist_inputs.py') $Output
if ($LASTEXITCODE -ne 0) { throw 'Specialist input generation failed.' }
$headless = Join-Path $Distribution 'support\analyzeHeadless.bat'
$previousJavaOptions = $env:GHIDRA_JAVA_OPTIONS
try {
$env:GHIDRA_JAVA_OPTIONS = "$previousJavaOptions -Dapplication.settingsdir=`"$Output\profile`""
& $headless $Output 'UiSpecialists' -import (Join-Path $Output 'binaries') -recursive -loader BinaryLoader -loader-baseAddr 00401000 -processor x86:LE:64:default -cspec windows -noanalysis -scriptPath $PSScriptRoot -postScript CreateSpecialistPrograms.java $Output -log (Join-Path $Output 'programs.log')
if ($LASTEXITCODE -ne 0) { throw 'Specialist program import failed.' }
foreach ($name in @('source', 'destination', 'merge-base', 'merge-latest', 'merge-private')) {
    if (!(Test-Path -LiteralPath (Join-Path $Output "$name.program.tsv"))) { throw "Missing fixture report: $name" }
}
& $headless $Output 'UiSpecialists/binaries' -process source.bin -noanalysis -scriptPath $PSScriptRoot -postScript CreateSpecialistSessions.java $Output -log (Join-Path $Output 'sessions.log')
if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath (Join-Path $Output 'specialists.ready'))) { throw 'VT/BSim fixture verification failed; inspect sessions.log.' }
& $headless $Output 'UiSpecialists/binaries' -process source.bin -noanalysis -scriptPath $Output -postScript CreateUiDemoTrace.java $Output -log (Join-Path $Output 'trace.log')
if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath (Join-Path $Output 'trace.ready'))) { throw 'Debugger trace fixture verification failed; inspect trace.log.' }
& $headless $Output 'UiSpecialists/binaries' -process source.bin -noanalysis -scriptPath $PSScriptRoot -postScript CreateUiRepository.java $Output -postScript VerifySpecialistFixtures.java $Output -log (Join-Path $Output 'verification.log')
if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath (Join-Path $Output 'repository.ready')) -or !(Test-Path -LiteralPath (Join-Path $Output 'verified.ready'))) { throw 'Repository/persistence verification failed; inspect verification.log.' }
if ($Repository) {
    & (Join-Path $PSScriptRoot 'fixture-repository.ps1') -Distribution $Distribution -Fixture $Output -Port $RepositoryPort -Prepare
}
$remaining = @('Live Windows debugger', 'VT correlator/markup failure paths', 'BSim authentication/network errors', 'Specialist visual acceptance')
if (!$Repository) { $remaining += 'Running repository server/shared projects/checkouts and repository-backed merge conflict' }
$scriptHashes = @('CreateSpecialistPrograms.java', 'CreateSpecialistSessions.java', 'CreateUiRepository.java', 'VerifySpecialistFixtures.java', 'PrepareUiSharedProjects.java', 'specialist_inputs.py', 'prepare-specialists.ps1', 'fixture-repository.ps1') | ForEach-Object {
    @{ script = $_; sha256 = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $_) -Algorithm SHA256).Hash }
}
@{ project = (Join-Path $Output 'UiSpecialists.gpr'); distribution = $Distribution;
   timestamp = (Get-Date).ToUniversalTime().ToString('o'); revision = (git rev-parse HEAD);
   dirtyTree = [bool](git status --porcelain); scripts = @($scriptHashes);
   repositoryWorkflowPrepared = $Repository.IsPresent;
   repositoryBasePort = $(if ($Repository) { $RepositoryPort } else { $null });
   remaining = $remaining } |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $Output 'fixture-manifest.json') -Encoding utf8
Write-Output "Specialist fixtures verified: $Output"
}
finally {
    $env:GHIDRA_JAVA_OPTIONS = $previousJavaOptions
}
