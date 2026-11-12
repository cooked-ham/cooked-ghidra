param(
    [string]$Distribution = 'E:\cooked-ghidra\build\verification\release\ghidra_12.3_DEV',
    [string]$Fixture = 'E:\cooked-ghidra\build\verification\graphite-specialists',
    [ValidateRange(1024,65533)][int]$Port = 14150,
    [switch]$Prepare
)
# Foreground lifetime: Ctrl+C stops the owned server. -Prepare stages the shared
# projects, verifies the conflict and stops it automatically. No Windows service.
$ErrorActionPreference = 'Stop'
$Fixture = (Resolve-Path -LiteralPath $Fixture).Path
if (!(Test-Path -LiteralPath (Join-Path $Fixture 'repository.ready'))) { throw 'Prepare specialist fixtures first.' }
if ($Prepare -and ((Test-Path -LiteralPath (Join-Path $Fixture 'Alice.gpr')) -or (Test-Path -LiteralPath (Join-Path $Fixture 'Bob.gpr')))) { throw 'Shared projects already exist; use a new fixture directory.' }
$classpath = (Get-ChildItem -LiteralPath (Join-Path $Distribution 'Ghidra') -Recurse -Filter '*.jar' -File | Select-Object -ExpandProperty FullName) -join ';'
$argsFile = Join-Path $Fixture 'repository-java.args'
$keystore = Join-Path $Fixture 'repository-fixture.p12'
$certificate = Join-Path $Fixture 'repository-fixture.pem'
if (!(Test-Path -LiteralPath $keystore)) {
    & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -alias ui-fixture -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 -keystore $keystore -storepass ui-fixture-only -keypass ui-fixture-only -dname CN=GhidraUiFixture -ext SAN=IP:127.0.0.1,DNS:localhost -ext BC=ca:true -ext KU=digitalSignature,keyEncipherment,keyCertSign
    if ($LASTEXITCODE -ne 0) { throw 'Fixture certificate generation failed.' }
}
& (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -exportcert -rfc -alias ui-fixture -keystore $keystore -storepass ui-fixture-only -file $certificate
if ($LASTEXITCODE -ne 0) { throw 'Fixture certificate export failed.' }
@('-Djava.net.preferIPv4Stack=true', ('"-Dapplication.settingsdir=' + (Join-Path $Fixture 'server-profile').Replace('\','/') + '"'),
  ('"-Dghidra.keystore=' + $keystore.Replace('\','/') + '"'), '-Dghidra.password=ui-fixture-only',
  '-cp', ('"' + $classpath.Replace('\','/') + '"'), 'ghidra.server.remote.GhidraServer',
  '-a0', '-u', '-i127.0.0.1', '-ip127.0.0.1', "-p$Port", ('"' + (Join-Path $Fixture 'repositories').Replace('\','/') + '"')) |
    Set-Content -LiteralPath $argsFile -Encoding utf8
$ownedServer = $null
$previousJavaOptions = $env:GHIDRA_JAVA_OPTIONS
function Read-ServerOutput {
    $path = Join-Path $Fixture 'server.stdout.log'
    if (!(Test-Path -LiteralPath $path)) { return '' }
    $reader = [IO.StreamReader]::new([IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite))
    try { return $reader.ReadToEnd() }
    finally { $reader.Dispose() }
}
try {
    $ownedServer = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin\java.exe') -ArgumentList ('"@' + $argsFile + '"') -WindowStyle Hidden -WorkingDirectory $Distribution -PassThru -RedirectStandardOutput (Join-Path $Fixture 'server.stdout.log') -RedirectStandardError (Join-Path $Fixture 'server.stderr.log')
    $deadline = (Get-Date).AddSeconds(30)
    do {
        if ($ownedServer.HasExited) { throw 'Fixture server failed; inspect server.stdout.log/server.stderr.log.' }
        $ready = (Read-ServerOutput) -match 'Registered Ghidra Server'
        if (!$ready) { Start-Sleep -Milliseconds 300 }
    } while (!$ready -and (Get-Date) -lt $deadline)
    if (!$ready) { throw 'Fixture server startup timed out.' }
    Write-Output "Fixture server: 127.0.0.1:$Port; users ui_alice / ui_bob; fixture password ui-fixture-only; repository UI Review Repository"
    if ($Prepare) {
        $env:GHIDRA_JAVA_OPTIONS = "$previousJavaOptions -Dapplication.settingsdir=`"$Fixture\profile`" -Dghidra.cacerts=`"$certificate`""
        & (Join-Path $Distribution 'support\analyzeHeadless.bat') $Fixture 'UiSpecialists/binaries' -process source.bin -noanalysis -scriptPath $PSScriptRoot -postScript PrepareUiSharedProjects.java $Fixture $Port -log (Join-Path $Fixture 'shared-projects.log')
        if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath (Join-Path $Fixture 'shared-projects.ready'))) { throw 'Shared project fixture failed; inspect shared-projects.log.' }
        Get-Content -LiteralPath (Join-Path $Fixture 'shared-projects.ready')
    }
    else {
        Write-Output 'Keep this command running while reviewing the shared projects; Ctrl+C stops the server.'
        while (!$ownedServer.HasExited) { Start-Sleep -Milliseconds 500 }
    }
}
finally {
    $env:GHIDRA_JAVA_OPTIONS = $previousJavaOptions
    if ($ownedServer -and !$ownedServer.HasExited) { Stop-Process -Id $ownedServer.Id }
}
