param([string]$Tag = ('location-restart-' + (Get-Date -Format 'yyyyMMdd-HHmmss')))
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($Tag -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Invalid tag' }
$run = Join-Path $workspace "build/verification/$Tag"
if (Test-Path -LiteralPath $run) { throw 'Run already exists' }
$null = New-Item -ItemType Directory -Path $run
# Private runtime and profile: never mutate the frozen specialist installation or user profile.
$runtime = Join-Path $run 'runtime'
Copy-Item -LiteralPath (Join-Path $workspace 'build/verification/release/ghidra_12.3_DEV') -Destination $runtime -Recurse
$profile = Join-Path $run 'profile'
Copy-Item -LiteralPath (Join-Path $workspace 'build/verification/profile') -Destination $profile -Recurse
foreach ($module in @('Gui', 'Docking', 'Base', 'Decompiler')) {
    Copy-Item -LiteralPath (Join-Path $workspace "Ghidra/$(if ($module -in @('Base','Decompiler')) { 'Features' } else { 'Framework' })/$module/build/libs/$module.jar") -Destination (Join-Path $runtime "Ghidra/$(if ($module -in @('Base','Decompiler')) { 'Features' } else { 'Framework' })/$module/lib/$module.jar")
}
$projectDir = Join-Path $run 'project'
$null = New-Item -ItemType Directory -Path $projectDir
foreach ($suffix in @('gpr', 'rep')) {
    Copy-Item -LiteralPath (Join-Path $workspace "build/verification/graphite-fixtures/GraphiteReference.$suffix") -Destination $projectDir -Recurse
}
$source = Get-Content -LiteralPath 'E:/ghidra-ui-refresh/extension/GhidraUiRefresh/src/main/java/uirefresh/UiRefreshHarnessPlugin.java' -Raw
$anchor = 'private void runThemeProbe(String out) {'
if (($source.Split($anchor).Count - 1) -ne 1) { throw 'Probe anchor changed' }
$source = $source.Replace($anchor, $anchor + "`n        if (Boolean.getBoolean(`"uirefresh.vector.review`")) { LocationContextRestartReview.run(tool, out); return; }")
$javaSource = Join-Path $run 'UiRefreshHarnessPlugin.java'
[IO.File]::WriteAllText($javaSource, $source)
$classes = Join-Path $run 'classes'
$classpath = (Get-ChildItem -LiteralPath (Join-Path $runtime 'Ghidra') -Recurse -Filter '*.jar' -File | Select-Object -ExpandProperty FullName) -join ';'
$argsFile = Join-Path $run 'javac.args'
@('-cp', ('"' + $classpath.Replace('\','/') + '"'), '-d', ('"' + $classes.Replace('\','/') + '"'), ('"' + $javaSource.Replace('\','/') + '"'), ('"' + (Join-Path $PSScriptRoot 'LocationContextRestartReview.java').Replace('\','/') + '"')) | Set-Content -LiteralPath $argsFile
& javac "@$argsFile"
if ($LASTEXITCODE -ne 0) { throw 'Probe compile failed' }
$extension = Get-ChildItem -LiteralPath $profile -Recurse -Directory | Where-Object Name -eq 'GhidraUiRefresh' | Select-Object -First 1
$probeJar = Get-ChildItem -LiteralPath (Join-Path $extension.FullName 'lib') -Filter '*.jar' | Select-Object -First 1
& jar --update --file $probeJar.FullName -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed' }
$oldOptions = $env:GHIDRA_JAVA_OPTIONS
try {
    foreach ($phase in @('save', 'restart')) {
        $out = Join-Path $run "$phase.csv"
        $restart = ($phase -eq 'restart').ToString().ToLowerInvariant()
        $env:GHIDRA_JAVA_OPTIONS = "-Dapplication.settingsdir=$profile -Dghidra.ui.locationContext=true -Duirefresh.vector.review=true -Duirefresh.vector.restart=$restart -Duirefresh.themeprobe.out=$out -Duirefresh.autolaunch=true -Dsun.java2d.uiScale=1.0"
        $active = Start-Process -FilePath (Join-Path $runtime 'ghidraRun.bat') -ArgumentList (Join-Path $projectDir 'GraphiteReference.gpr') -WorkingDirectory $runtime -WindowStyle Hidden -PassThru
        try {
            $deadline = (Get-Date).AddMinutes(3)
            while ((Get-Date) -lt $deadline -and !(Test-Path -LiteralPath "$out.done") -and !(Test-Path -LiteralPath "$out.err")) { Start-Sleep -Seconds 2 }
            if (Test-Path -LiteralPath "$out.err") { throw (Get-Content -LiteralPath "$out.err" -Raw) }
            if (!(Test-Path -LiteralPath "$out.done")) { throw "Timed out: $phase" }
            Start-Sleep -Seconds 3
        }
        finally {
            if (!$active.HasExited) { & taskkill /PID $active.Id /T /F 2>$null | Out-Null }
            Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" | Where-Object { $_.CommandLine -like "*uirefresh.themeprobe.out=$out*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        }
    }
    $saved = Import-Csv -LiteralPath (Join-Path $run 'save.csv')
    $restarted = Import-Csv -LiteralPath (Join-Path $run 'restart.csv')
    if ($saved[0].pid -eq $restarted.pid -or $saved[0].address -ne $restarted.address -or $restarted.detached -ne 'true') { throw 'Restart identity/location/layout mismatch' }
    @{
        rendering = 'Live Swing persistence checks and in-process frame painting; not mouse or physical DPI acceptance'
        jars = @('Gui','Docking','Base','Decompiler') | ForEach-Object { @{ name = $_; sha256 = (Get-FileHash -LiteralPath (Join-Path $runtime "Ghidra/$(if ($_ -in @('Base','Decompiler')) { 'Features' } else { 'Framework' })/$_/lib/$_.jar")).Hash } }
        sources = @('LocationContextRestartReview.java','review-location-restart.ps1') | ForEach-Object { @{ name = $_; sha256 = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $_)).Hash } }
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $run 'manifest.json')
    'PASS' | Set-Content -LiteralPath (Join-Path $run 'review.done')
}
finally { $env:GHIDRA_JAVA_OPTIONS = $oldOptions }
