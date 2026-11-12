param(
    [string]$BuiltDistribution = 'E:\cooked-ghidra\build\dist\ghidra_12.2_DEV',
    [string]$ProfileSeed = 'E:\cooked-ghidra\build\verification\profile',
    [string]$Tag = 'coherent-ui-20261112'
)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if ($Tag -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Use a simple tag.' }
$run = Join-Path $workspace "build\verification\$Tag"
if (Test-Path -LiteralPath $run) { throw 'Choose a fresh runtime tag.' }
$sourceVersion = ((Get-Content -LiteralPath (Join-Path $workspace 'Ghidra\application.properties') |
    Where-Object { $_ -match '^application.version=' }) -split '=',2)[1]
$runtimeVersion = ((Get-Content -LiteralPath (Join-Path $BuiltDistribution 'Ghidra\application.properties') |
    Where-Object { $_ -match '^application.version=' }) -split '=',2)[1]
if ($sourceVersion -ne $runtimeVersion) { throw "Runtime $runtimeVersion does not match source $sourceVersion. Run gradlew assembleAll." }
$null = New-Item -ItemType Directory -Path $run
$runtime = Join-Path $run ('ghidra_' + $sourceVersion + '_DEV')
Copy-Item -LiteralPath $BuiltDistribution -Destination $runtime -Recurse
$profile = Join-Path $run 'profile'
Copy-Item -LiteralPath $ProfileSeed -Destination $profile -Recurse
$settings = Get-ChildItem -LiteralPath $profile -Directory -Recurse |
    Where-Object { $_.Name -like 'ghidra_*_DEV' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'tools')) } |
    Select-Object -First 1
if (!$settings) { throw 'Configured probe seed settings missing.' }
$currentSettings = Join-Path $settings.Parent.FullName ('ghidra_' + $sourceVersion + '_DEV')
if ($settings.FullName -ne $currentSettings) { Copy-Item -LiteralPath $settings.FullName -Destination $currentSettings -Recurse }
$extensions = Join-Path $currentSettings 'Extensions'
foreach ($name in @('Jython','Lisa','MachineLearning','SampleTablePlugin','sample','SleighDevTools','SymbolicSummaryZ3')) {
    $zip = Get-ChildItem -LiteralPath (Join-Path $runtime 'Extensions\Ghidra') -Filter (('*_' + $name + '.zip')) | Select-Object -First 1
    if (!$zip) { throw "Built extension missing: $name" }
    Expand-Archive -LiteralPath $zip.FullName -DestinationPath $extensions -Force
}
$harnessProperties = Join-Path $extensions 'GhidraUiRefresh\extension.properties'
$properties = [IO.File]::ReadAllText($harnessProperties)
$properties = [regex]::Replace($properties, '(?m)^version=.*$', ('version=' + $sourceVersion))
[IO.File]::WriteAllText($harnessProperties, $properties)
@{ runtime = $runtime; profile = $profile; version = $sourceVersion;
   revision = (git -C $workspace rev-parse HEAD); dirtyTree = $true;
   extensions = @('Jython','Lisa','MachineLearning','SampleTablePlugin','sample','SleighDevTools','SymbolicSummaryZ3');
   note = 'Probe extension is recompiled by capture-reference; seed settings are copied, not migrated in place.' } |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $run 'runtime-manifest.json')
Write-Output "Runtime: $runtime"
Write-Output "Profile: $profile"
