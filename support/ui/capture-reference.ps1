param(
    [string]$Distribution = 'E:\cooked-ghidra\build\verification\coherent-ui-20261112\ghidra_12.2_DEV',
    [string]$ProfileSeed = 'E:\cooked-ghidra\build\verification\coherent-ui-20261112\profile',
    [string]$FixtureProject = 'E:\cooked-ghidra\build\verification\graphite-fixtures\GraphiteReference.gpr',
    [string]$ProbeSource = 'E:\ghidra-ui-refresh\extension\GhidraUiRefresh\src\main\java\uirefresh\UiRefreshHarnessPlugin.java',
    [string]$Python = 'C:\Python315\python.exe',
    [string]$Tag = ('graphite-reference-' + (Get-Date -Format 'yyyyMMdd-HHmmss')),
    [string]$Sequence = 'Graphite Preview,(openprogram),(openprogram:whoami_x64.exe),(polish),(startup),(savecopy),Flat Dark Theme,(startup),Graphite Preview,(startup)',
    [switch]$Specialists,
    [switch]$TraceHandlers,
    [switch]$OptionalPlugins
)
# Uses the existing UI Refresh probe extension in the seed profile. It is a test harness,
# not a product dependency. The source, project and profile remain unchanged.
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$verification = [IO.Path]::GetFullPath((Join-Path $workspace 'build\verification'))
$runtime = [IO.Path]::GetFullPath($Distribution)
$sourceVersion = ((Get-Content -LiteralPath (Join-Path $workspace 'Ghidra\application.properties') | Where-Object { $_ -match '^application.version=' }) -split '=',2)[1]
$runtimeVersion = ((Get-Content -LiteralPath (Join-Path $runtime 'Ghidra\application.properties') | Where-Object { $_ -match '^application.version=' }) -split '=',2)[1]
if ($sourceVersion -ne $runtimeVersion) { throw "Runtime $runtimeVersion does not match checkout $sourceVersion; use prepare-review-runtime.ps1 after assembleAll." }
if (!$runtime.StartsWith($verification + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Use a disposable distribution under this checkout build\verification; this script replaces its UI jars.'
}
if ($Tag -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Tag must contain letters, numbers, underscores or hyphens.' }
$run = Join-Path $verification $Tag
if (Test-Path -LiteralPath $run) { throw "Choose a new tag; run already exists: $run" }
$null = New-Item -ItemType Directory -Path $run
$profile = Join-Path $run 'profile'
Copy-Item -LiteralPath $ProfileSeed -Destination $profile -Recurse
$seedHarness = Get-ChildItem -LiteralPath $profile -Directory -Recurse |
    Where-Object { $_.Name -eq 'GhidraUiRefresh' } | Select-Object -First 1
if (!$seedHarness) { throw 'Seed profile must contain the UI Refresh probe extension and its configured CodeBrowser tool.' }
$adapted = Join-Path $run 'probe-source\uirefresh\UiRefreshHarnessPlugin.java'
& $Python (Join-Path $PSScriptRoot 'adapt_probe.py') $ProbeSource $adapted
if ($LASTEXITCODE -ne 0) { throw 'Probe adapter failed.' }
$classes = Join-Path $run 'probe-classes'
$inventorySource = Join-Path $PSScriptRoot 'RuntimeInventory.java'
$classpath = (Get-ChildItem -LiteralPath (Join-Path $runtime 'Ghidra') -Recurse -Filter '*.jar' -File | Select-Object -ExpandProperty FullName) -join ';'
$argsFile = Join-Path $run 'javac.args'
$graphReviewSource = Join-Path $PSScriptRoot 'GraphReview.java'
@('-cp', ('"' + $classpath.Replace('\', '/') + '"'), '-d', ('"' + $classes.Replace('\', '/') + '"'), ('"' + $adapted.Replace('\', '/') + '"'), ('"' + $inventorySource.Replace('\', '/') + '"'), ('"' + $graphReviewSource.Replace('\', '/') + '"')) |
    Set-Content -LiteralPath $argsFile -Encoding utf8
& javac "@$argsFile"
if ($LASTEXITCODE -ne 0) { throw 'Adapted probe compilation failed.' }
$probeJar = Get-ChildItem -LiteralPath (Join-Path $seedHarness.FullName 'lib') -Filter '*.jar' | Select-Object -First 1
& jar --update --file $probeJar.FullName -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Adapted probe packaging failed.' }
$projectDir = Join-Path $run 'project'
$null = New-Item -ItemType Directory -Path $projectDir
$projectStem = [IO.Path]::GetFileNameWithoutExtension($FixtureProject)
Copy-Item -LiteralPath $FixtureProject -Destination $projectDir
Copy-Item -LiteralPath ([IO.Path]::ChangeExtension($FixtureProject, '.rep')) -Destination $projectDir -Recurse
$modules = @('Framework\Gui', 'Framework\Docking', 'Framework\Graph', 'Features\Base', 'Features\Decompiler', 'Features\FunctionGraph', 'Features\FunctionGraphDecompilerExtension')
$hashes = @()
foreach ($module in $modules) {
    $name = Split-Path $module -Leaf
    $sourceJar = Join-Path $workspace "Ghidra\$module\build\libs\$name.jar"
    $targetJar = Join-Path $runtime "Ghidra\$module\lib\$name.jar"
    Copy-Item -LiteralPath $targetJar -Destination (Join-Path $run "$name.original.jar")
    Copy-Item -LiteralPath $sourceJar -Destination $targetJar
    $hashes += @{ module = $module; sha256 = (Get-FileHash -LiteralPath $targetJar -Algorithm SHA256).Hash }
}
$sequence = $Sequence
$sequencePath = Join-Path $run 'capture.seq'
[IO.File]::WriteAllText($sequencePath, $sequence)
$probe = Join-Path $run 'capture.csv'
$oldOptions = $env:GHIDRA_JAVA_OPTIONS
try {
    $traceOptions = if ($TraceHandlers) { "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles=true -Duser.dir=$run" } else { '' }
    $env:GHIDRA_JAVA_OPTIONS = "$traceOptions -Dapplication.settingsdir=$profile -Dghidra.ui.locationContext=true -Duirefresh.inventory.components=true -Duirefresh.inventory.optional=$($OptionalPlugins.IsPresent.ToString().ToLowerInvariant()) -Duirefresh.inventory.specialists=$($Specialists.IsPresent.ToString().ToLowerInvariant()) -Duirefresh.themeprobe.out=$probe -Duirefresh.themeprobe.seqfile=$sequencePath -Duirefresh.themeprobe.shots=true -Duirefresh.themeprobe.close=true -Duirefresh.autolaunch=true -Dsun.java2d.uiScale=1.0"
    Start-Process -FilePath (Join-Path $runtime 'ghidraRun.bat') -ArgumentList (Join-Path $projectDir "$projectStem.gpr") -WorkingDirectory $runtime -WindowStyle Hidden
    $deadline = (Get-Date).AddMinutes(7)
    while ((Get-Date) -lt $deadline -and !(Test-Path -LiteralPath "$probe.done") -and !(Test-Path -LiteralPath "$probe.err")) {
        Start-Sleep -Seconds 2
    }
    if (Test-Path -LiteralPath "$probe.err") { throw ([IO.File]::ReadAllText("$probe.err")) }
    if (!(Test-Path -LiteralPath "$probe.done")) { throw "Capture timed out: $run" }
    $lines = [IO.File]::ReadAllText($probe)
    if ($lines -match 'NOT FOUND|,FAIL|,MISSING') { throw "Probe reported a missing theme/view or failure: $probe" }
    @{ timestamp = (Get-Date).ToUniversalTime().ToString('o'); jars = $hashes;
       probeSha256 = (Get-FileHash -LiteralPath $probeJar.FullName -Algorithm SHA256).Hash;
       fixture = $FixtureProject; sequence = $sequence; logicalScale = 1.0;
       rendering = 'In-process Swing painting; not physical DPI or screen-reader acceptance';
       revision = (git -C $workspace rev-parse HEAD); dirtyTree = $true; traceHandlers = $TraceHandlers.IsPresent;
       sourceApplicationVersion = $sourceVersion; runtimeApplicationVersion = $runtimeVersion } |
       ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $run 'capture-manifest.json') -Encoding utf8
    Get-Content -LiteralPath $probe
    Write-Output "Reference capture: $run"
}
finally {
    $env:GHIDRA_JAVA_OPTIONS = $oldOptions
    Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" |
        Where-Object { $_.CommandLine -like "*uirefresh.themeprobe.out=$probe*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -ErrorAction SilentlyContinue }
}
