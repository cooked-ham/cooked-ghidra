param(
    [string]$Distribution = 'E:/cooked-ghidra/build/verification/coherent-ui-20261112/ghidra_12.2_DEV',
    [string]$DependencyDirectory = 'E:/cooked-ghidra/build/verification/eclipse-ui-review-20261112',
    [string]$Tag = ('eclipse-source-review-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($Tag -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Use a simple, fresh tag.' }
$run = Join-Path $workspace "build/verification/$Tag"
if (Test-Path -LiteralPath $run) { throw 'Use a fresh review directory.' }
$null = New-Item -ItemType Directory -Path $run
$cache = [IO.Path]::GetFullPath($DependencyDirectory)
$null = New-Item -ItemType Directory -Force -Path $cache
$downloads = @(
    @{ name='eclipse-rcp.zip'; algorithm='SHA512'; hash='24af50884d1341ba5d30070f0f877bb1a405a613dd50fdf759e380011aa7289e0b891d79c95ffd2f5a7e77aa279023acad72f72f0fdc4438043f20417ae46eaa'; url='https://download.eclipse.org/technology/epp/downloads/release/2026-09/R/eclipse-rcp-2026-09-R-win32-x86_64.zip'; destination='runtime' },
    @{ name='PyDev-9.3.0.zip'; algorithm='SHA256'; hash='45398edf2adb56078a80bc88a919941578f0c0b363efbdd011bfd158a99b112e'; url='https://sourceforge.net/projects/pydev/files/pydev/PyDev%209.3.0/PyDev%209.3.0.zip/download'; destination='pydev' },
    @{ name='cdt-8.6.0.zip'; algorithm='SHA256'; hash='81b7d19d57c4a3009f4761699a72e8d642b5e1d9251d2bb98df438b1e28f8ba9'; url='https://archive.eclipse.org/tools/cdt/releases/8.6/cdt-8.6.0.zip'; destination='cdt' }
)
foreach ($download in $downloads) {
    $archive = Join-Path $cache $download.name
    if (!(Test-Path -LiteralPath $archive)) {
        & curl.exe -L --fail --retry 2 --connect-timeout 20 --max-time 300 --silent --show-error --output $archive $download.url
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $($download.name)" }
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm $download.algorithm).Hash -ne $download.hash) {
        throw "Dependency checksum mismatch: $archive"
    }
    Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $run $download.destination)
}
$eclipse = Join-Path $run 'runtime/eclipse'
$core = [IO.Path]::GetFullPath($Distribution)
$pluginSource = Join-Path $workspace 'GhidraBuild/EclipsePlugins/GhidraDev/GhidraDevPlugin'
$sourceFiles = Get-ChildItem -LiteralPath (Join-Path $pluginSource 'src/main/java') -Recurse -Filter '*.java' -File
$dependencyJars = Get-ChildItem (Join-Path $eclipse 'plugins'), (Join-Path $run 'pydev/plugins'), (Join-Path $run 'cdt/plugins') -Recurse -Filter '*.jar' -File |
    Where-Object FullName -NotMatch '\.source_' | Select-Object -ExpandProperty FullName
$launchJar = Join-Path $workspace 'GhidraBuild/LaunchSupport/build/libs/LaunchSupport.jar'
$utilityJar = Join-Path $workspace 'Ghidra/Framework/Utility/build/libs/Utility.jar'
$coreJars = Get-ChildItem -LiteralPath (Join-Path $core 'Ghidra') -Recurse -Filter '*.jar' -File | Select-Object -ExpandProperty FullName
$classpath = ($dependencyJars + $coreJars + $launchJar) -join ';'
$classes = Join-Path $run 'ghidradev-classes'
$argsFile = Join-Path $run 'javac.args'
@('-cp', ('"'+$classpath.Replace('\','/')+'"'), '-d', ('"'+$classes.Replace('\','/')+'"')) +
    ($sourceFiles | ForEach-Object { '"'+$_.FullName.Replace('\','/')+'"' }) | Set-Content -LiteralPath $argsFile
& javac "@$argsFile"
if ($LASTEXITCODE -ne 0) { throw 'Source GhidraDev compilation failed.' }
$bundle = Join-Path $eclipse 'dropins/ghidradev/eclipse/plugins/ghidra.ghidradev_5.1.0.review'
$probe = Join-Path $eclipse 'dropins/ghidradev/eclipse/plugins/uireview.eclipse_1.0.0'
$null = New-Item -ItemType Directory -Force "$bundle/META-INF", "$bundle/build/data", "$probe/META-INF"
Copy-Item -LiteralPath (Join-Path $pluginSource 'plugin.xml') -Destination $bundle
Copy-Item -LiteralPath (Join-Path $pluginSource 'icons') -Destination (Join-Path $bundle 'icons') -Recurse
Copy-Item "$classes/*" $bundle -Recurse
Copy-Item -LiteralPath $utilityJar,$launchJar -Destination (Join-Path $bundle 'build/data')
(Get-Content -LiteralPath (Join-Path $pluginSource 'META-INF/MANIFEST.MF') -Raw).Replace('5.1.0.qualifier','5.1.0.review') |
    Set-Content -LiteralPath (Join-Path $bundle 'META-INF/MANIFEST.MF')
@('Manifest-Version: 1.0','Bundle-ManifestVersion: 2','Bundle-SymbolicName: uireview.eclipse;singleton:=true','Bundle-Version: 1.0.0',
    'Require-Bundle: org.eclipse.core.runtime,org.eclipse.core.resources,org.eclipse.equinox.app,org.eclipse.ui,org.eclipse.ui.ide,org.eclipse.jface,org.eclipse.swt,org.eclipse.jdt.core,org.eclipse.jdt.ui,org.eclipse.jdt.launching,org.eclipse.debug.ui,org.eclipse.debug.core,ghidra.ghidradev','') |
    Set-Content -LiteralPath (Join-Path $probe 'META-INF/MANIFEST.MF')
'<plugin><extension point="org.eclipse.core.runtime.applications" id="review"><application><run class="uireview.EclipseReviewApp"/></application></extension></plugin>' |
    Set-Content -LiteralPath (Join-Path $probe 'plugin.xml')
$eclipseClasspath = (Get-ChildItem -LiteralPath (Join-Path $eclipse 'plugins') -Filter '*.jar' -File |
    Where-Object Name -NotMatch '\.source_' | Select-Object -ExpandProperty FullName) -join ';'
@('-cp', ('"'+$eclipseClasspath.Replace('\','/')+'"'), '-d', ('"'+$probe.Replace('\','/')+'"'),
    ('"'+(Join-Path $PSScriptRoot 'EclipseReviewApp.java').Replace('\','/')+'"')) | Set-Content -LiteralPath (Join-Path $run 'javac-probe.args')
& javac "@$(Join-Path $run 'javac-probe.args')"
if ($LASTEXITCODE -ne 0) { throw 'Eclipse review harness compilation failed.' }
$review = Join-Path $run 'review'
$process = $null
try {
    $process = Start-Process -FilePath (Join-Path $eclipse 'eclipsec.exe') -ArgumentList @('-clean','-nosplash','-application','uireview.eclipse.review',
        '-data',(Join-Path $run 'workspace'),'-consoleLog','-vm',('"'+(Join-Path $env:JAVA_HOME 'bin/javaw.exe')+'"'),'-vmargs','-Xmx2G',
        "-Duireview.output=$review", "-Duireview.ghidra=$core") -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $run 'application.log') -RedirectStandardError (Join-Path $run 'application.err')
    $process.Id | Set-Content -LiteralPath (Join-Path $run 'process.pid')
    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline -and !$process.HasExited -and !(Test-Path -LiteralPath "$review/review.done") -and !(Test-Path -LiteralPath "$review/review.failures")) {
        Start-Sleep -Seconds 2
    }
    if (!(Test-Path -LiteralPath "$review/review.done") -or (Test-Path -LiteralPath "$review/review.failures")) {
        throw "Eclipse acceptance checks failed; inspect $run"
    }
    @{ revision=(git -C $workspace rev-parse HEAD); dependencies=$downloads;
        utilityJarSha256=(Get-FileHash -LiteralPath $utilityJar).Hash; launchJarSha256=(Get-FileHash -LiteralPath $launchJar).Hash;
        sources=@($sourceFiles | ForEach-Object { @{ file=$_.FullName.Substring($workspace.Length+1).Replace('\','/'); sha256=(Get-FileHash -LiteralPath $_.FullName).Hash } });
        harnessSha256=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'EclipseReviewApp.java')).Hash;
        rendering='SWT client-area capture; host Eclipse theme; no Swing theme substitution';
        scope='Wizard pages, source-folder chooser, Ghidra launch tabs, preferences, invalid ports and cancellation; no launch/build execution or optional PyDev/CDT activation' } |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $run 'manifest.json')
    Write-Output "Eclipse review passed: $run"
}
finally {
    Get-CimInstance Win32_Process | Where-Object {
        $_.Name -in @('eclipsec.exe','eclipse.exe','java.exe','javaw.exe') -and $_.CommandLine -like "*$run*"
    } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}
