param(
    [string]$Distribution = 'E:\cooked-ghidra\build\verification\runtime-specialist-ui',
    [string]$ProfileSeed = 'E:\cooked-ghidra\build\verification\profile',
    [string]$Fixture = 'E:\cooked-ghidra\build\verification\graphite-specialists-acceptance-inputs',
    [string]$ProbeSourceDir = 'E:\ghidra-ui-refresh\extension\GhidraUiRefresh\src\main\java\uirefresh',
    [string]$Python = 'C:\Python315\python.exe',
    [string[]]$Surfaces = @('codebrowser', 'vt', 'debugger'),
    [string]$Tag = ('graphite-specialist-ui-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
)
# Runs one specialist surface per Ghidra process so only one tool window is ever created.
# The Front End is hidden and tool frames are positioned off-screen before they become visible.
# This is review preparation, not visual/functional acceptance.
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$verification = [IO.Path]::GetFullPath((Join-Path $workspace 'build\verification'))
$scriptLog = Join-Path $verification 'review-specialists.log'
"$(Get-Date -Format o) start PID=$PID" | Set-Content -LiteralPath $scriptLog
trap {
    "$(Get-Date -Format o) TRAP: $_" | Add-Content -LiteralPath $scriptLog
    throw
}
$runtime = [IO.Path]::GetFullPath($Distribution)
$fixturePath = [IO.Path]::GetFullPath($Fixture)
if (!$runtime.StartsWith($verification + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Use a disposable distribution under this checkout build\verification.'
}
if (!(Test-Path -LiteralPath (Join-Path $runtime 'ghidraRun.bat'))) {
    throw "Missing Ghidra distribution. Prepare a frozen runtime first or pass -Distribution: $runtime"
}
if (!$fixturePath.StartsWith($verification + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Use a prepared fixture under this checkout build\verification.'
}
foreach ($required in @('UiSpecialists.gpr', 'UiSpecialists.rep', 'ui-specialist-bsim.mv.db')) {
    if (!(Test-Path -LiteralPath (Join-Path $fixturePath $required))) {
        throw "Missing specialist fixture item: $required"
    }
}
if ($Tag -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Tag must contain letters, numbers, underscores or hyphens.' }
$run = Join-Path $verification $Tag
if (Test-Path -LiteralPath $run) { throw "Choose a new tag; run already exists: $run" }
$lock = Join-Path $verification '.review-specialists.lock'
if (Test-Path -LiteralPath $lock) {
    $owner = 0
    try { $owner = [int]((Get-Content -LiteralPath $lock -Raw).Trim()) } catch { }
    if ($owner -gt 0 -and (Get-Process -Id $owner -ErrorAction SilentlyContinue)) {
        throw "A specialist review is already running (PID $owner); refusing to start another."
    }
    Remove-Item -LiteralPath $lock -Force
}
Set-Content -LiteralPath $lock -Value $PID
$null = New-Item -ItemType Directory -Path $run

# Prepare one clean profile, then clone it for each surface so tool state and project locks
# cannot leak from one run to the next.
$profileTemplate = Join-Path $run 'profile-template'
Copy-Item -LiteralPath $ProfileSeed -Destination $profileTemplate -Recurse
$seedHarness = Get-ChildItem -LiteralPath $profileTemplate -Directory -Recurse |
    Where-Object { $_.Name -eq 'GhidraUiRefresh' } | Select-Object -First 1
if (!$seedHarness) { throw 'Seed profile must contain the UI Refresh probe extension.' }

$adapted = Join-Path $run 'probe-source'
& $Python (Join-Path $PSScriptRoot 'adapt_specialist_probe.py') `
    (Join-Path $ProbeSourceDir 'UiRefreshHarnessPlugin.java') $adapted
if ($LASTEXITCODE -ne 0) { throw 'Probe adapter failed.' }

$classes = Join-Path $run 'probe-classes'
$null = New-Item -ItemType Directory -Path $classes
$sources = @(
    (Join-Path $adapted 'uirefresh\UiRefreshHarnessPlugin.java'),
    (Join-Path $PSScriptRoot 'RuntimeInventory.java'),
    (Join-Path $PSScriptRoot 'SpecialistReview.java')
)
$classpath = (Get-ChildItem -LiteralPath (Join-Path $runtime 'Ghidra') -Recurse -Filter '*.jar' -File |
    Select-Object -ExpandProperty FullName) -join ';'
$argsFile = Join-Path $run 'javac.args'
@('-cp', ('"' + $classpath.Replace('\', '/') + '"'), '-d', ('"' + $classes.Replace('\', '/') + '"')) +
    ($sources | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }) |
    Set-Content -LiteralPath $argsFile -Encoding utf8
& javac "@$argsFile"
if ($LASTEXITCODE -ne 0) { throw 'Specialist probe compilation failed.' }
$probeJar = Get-ChildItem -LiteralPath (Join-Path $seedHarness.FullName 'lib') -Filter '*.jar' |
    Select-Object -First 1
& jar --update --file $probeJar.FullName -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Specialist probe packaging failed.' }

$review = Join-Path $run 'review'
$null = New-Item -ItemType Directory -Path $review
$oldOptions = $env:GHIDRA_JAVA_OPTIONS
$active = $null
try {
    foreach ($surface in $Surfaces) {
        $surfaceRun = Join-Path $run $surface
        $null = New-Item -ItemType Directory -Path $surfaceRun
        $profile = Join-Path $surfaceRun 'profile'
        Copy-Item -LiteralPath $profileTemplate -Destination $profile -Recurse
        $projectDir = Join-Path $surfaceRun 'project'
        $null = New-Item -ItemType Directory -Path $projectDir
        Copy-Item -LiteralPath (Join-Path $fixturePath 'UiSpecialists.gpr') -Destination $projectDir
        Copy-Item -LiteralPath (Join-Path $fixturePath 'UiSpecialists.rep') -Destination $projectDir -Recurse
        $surfaceReview = Join-Path $review $surface
        $null = New-Item -ItemType Directory -Path $surfaceReview
        $env:GHIDRA_JAVA_OPTIONS = "-Dapplication.settingsdir=$profile -Dghidra.ui.locationContext=true -Duirefresh.autolaunch=true -Duirefresh.specialists.out=$surfaceReview -Duirefresh.specialists.surface=$surface -Duirefresh.specialists.fixture=$fixturePath -Duirefresh.themeprobe.shots=true -Dsun.java2d.uiScale=1.0"
        "$(Get-Date -Format o) launching $surface" | Add-Content -LiteralPath $scriptLog
        $active = Start-Process -FilePath (Join-Path $runtime 'ghidraRun.bat') `
            -ArgumentList (Join-Path $projectDir 'UiSpecialists.gpr') `
            -WorkingDirectory $runtime -WindowStyle Hidden -PassThru
        "$(Get-Date -Format o) launched $surface PID=$($active.Id)" | Add-Content -LiteralPath $scriptLog
        $deadline = (Get-Date).AddMinutes(4)
        while ((Get-Date) -lt $deadline -and
                !(Test-Path -LiteralPath (Join-Path $surfaceReview 'specialist-ui-review.done')) -and
                !(Test-Path -LiteralPath (Join-Path $surfaceReview 'specialist-ui-review.err')) -and
                !(Test-Path -LiteralPath (Join-Path $surfaceReview 'specialist-ui-review.failures'))) {
            Start-Sleep -Seconds 2
        }
        if ($active -and !$active.HasExited) {
            & taskkill /PID $active.Id /T /F 2>$null | Out-Null
        }
        Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" |
            Where-Object { $_.CommandLine -like "*uirefresh.specialists.out=$surfaceReview*" } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        $active = $null
        if (Test-Path -LiteralPath (Join-Path $surfaceReview 'specialist-ui-review.err')) {
            throw ([IO.File]::ReadAllText((Join-Path $surfaceReview 'specialist-ui-review.err')))
        }
        if (Test-Path -LiteralPath (Join-Path $surfaceReview 'specialist-ui-review.failures')) {
            throw ([IO.File]::ReadAllText((Join-Path $surfaceReview 'specialist-ui-review.failures')))
        }
        if (!(Test-Path -LiteralPath (Join-Path $surfaceReview 'specialist-ui-review.done'))) {
            throw "Specialist UI review did not finish within 4 minutes: $surface"
        }
        Get-ChildItem -LiteralPath $surfaceReview -Filter '*.runtime.csv' | ForEach-Object {
            if ((Get-Content -LiteralPath $_.FullName | Measure-Object -Line).Lines -lt 2) {
                throw "Empty runtime capture: $($_.Name)"
            }
        }
        "$(Get-Date -Format o) completed $surface" | Add-Content -LiteralPath $scriptLog
        Start-Sleep -Seconds 1
    }

    $header = 'surface,fixture,implementation,entry_path,populated_evidence,screenshots'
    $lines = @($header)
    foreach ($surface in $Surfaces) {
        $surfaceReview = Join-Path $review $surface
        $csv = Join-Path $surfaceReview 'specialist-ui-review.csv'
        if (!(Test-Path -LiteralPath $csv)) { throw "Missing review report: $surface" }
        $lines += (Get-Content -LiteralPath $csv | Select-Object -Skip 1)
    }
    $lines | Set-Content -LiteralPath (Join-Path $review 'specialist-ui-review.csv') -Encoding utf8
    $reviewRows = Import-Csv -LiteralPath (Join-Path $review 'specialist-ui-review.csv')
    if ($reviewRows.Count -lt 1) { throw 'Specialist review report is incomplete.' }
    @{ timestamp = (Get-Date).ToUniversalTime().ToString('o');
       fixture = $fixturePath; distribution = $runtime; sequence = 'one surface per process';
       logicalScale = 1.0; rendering = 'In-process Swing painting; off-screen tool frames; not visual acceptance';
       revision = (git -C $workspace rev-parse HEAD);
       probeJarSha256 = (Get-FileHash -LiteralPath $probeJar.FullName -Algorithm SHA256).Hash;
       surfaces = $Surfaces;
       scripts = @('adapt_specialist_probe.py', 'SpecialistReview.java', 'RuntimeInventory.java',
           'review-specialists.ps1') | ForEach-Object {
               @{ script = $_; sha256 = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $_) -Algorithm SHA256).Hash }
           } } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $run 'specialist-ui-manifest.json') -Encoding utf8
    Get-Content -LiteralPath (Join-Path $review 'specialist-ui-review.csv')
    Write-Output "Specialist UI review: $run"
}
finally {
    "$(Get-Date -Format o) finally" | Add-Content -LiteralPath $scriptLog
    $env:GHIDRA_JAVA_OPTIONS = $oldOptions
    if ($active -and !$active.HasExited) {
        & taskkill /PID $active.Id /T /F 2>$null | Out-Null
    }
    Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" |
        Where-Object { $_.CommandLine -like "*uirefresh.specialists.out=$review*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Remove-Item -LiteralPath $lock -Force -ErrorAction SilentlyContinue
}
