param(
    [string]$Distribution = 'E:\cooked-ghidra\build\verification\release\ghidra_12.3_DEV',
    [string]$Output = 'E:\cooked-ghidra\build\verification\graphite-fixtures'
)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $Output) { throw 'Use a new output directory to preserve existing fixtures.' }
$null = New-Item -ItemType Directory -Path $Output
$bytes = [byte[]]::new(256)
# int classify_address_for_preview(int location_id) { return location_id == 42 ? 1 : 0; }
$code = [Convert]::FromHexString('554889E583F92A7507B801000000EB05B8000000005DC3')
[Array]::Copy($code, 0, $bytes, 0, $code.Length)
$second = [Convert]::FromHexString('B82A000000C3')
[Array]::Copy($second, 0, $bytes, 64, $second.Length)
$label = [Text.Encoding]::ASCII.GetBytes("Listing and Decompiler location context`0")
[Array]::Copy($label, 0, $bytes, 128, $label.Length)
# Names retain compatibility with the existing UI Refresh probe. These are raw synthetic
# fixtures, not copies of Windows executables. BinaryLoader ignores the suffix.
foreach ($name in @('hostname_x64.exe', 'whoami_x64.exe')) {
    $binary = Join-Path $Output $name
    [IO.File]::WriteAllBytes($binary, $bytes)
    & (Join-Path $Distribution 'support\analyzeHeadless.bat') $Output 'GraphiteReference' -import $binary -loader BinaryLoader -loader-baseAddr 00401000 -processor x86:LE:64:default -cspec windows -noanalysis -scriptPath $PSScriptRoot -postScript CreateUiFixture.java -log (Join-Path $Output "$name.log")
    if ($LASTEXITCODE -ne 0) { throw "Fixture import failed: $name" }
}
Get-FileHash -LiteralPath (Join-Path $Output 'hostname_x64.exe') -Algorithm SHA256
Write-Output "Fixture project: $(Join-Path $Output 'GraphiteReference.gpr')"
