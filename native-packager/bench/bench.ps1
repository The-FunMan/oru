# Desktop benchmark: native-packager against zipalign plus apksigner sign.
#
# Runs both chains alternately, $Runs times each, and reports wall time and the
# peak working set of each process. Outputs are verified once with apksigner.
#
#   pwsh bench.ps1 -InputApk <stock.apk> -Tool <native-packager.exe> -Keystore <ks.p12> -KeyPem <key.pem> -CertPem <cert.pem>
#
# The keystore is a PKCS#12 file with the key and certificate, password "benchpw":
#   openssl pkcs12 -export -inkey key.pem -in cert.pem -out ks.p12 -passout pass:benchpw
param(
    [Parameter(Mandatory)] [string] $InputApk,
    [Parameter(Mandatory)] [string] $Tool,
    [Parameter(Mandatory)] [string] $Keystore,
    [Parameter(Mandatory)] [string] $KeyPem,
    [Parameter(Mandatory)] [string] $CertPem,
    [string] $BuildTools = "$env:ANDROID_HOME\build-tools\36.0.0",
    [string] $Java = 'java',
    [string] $Work = $env:TEMP,
    [int] $Runs = 3
)

$ErrorActionPreference = 'Stop'
$zipalign = Join-Path $BuildTools 'zipalign.exe'
$apksignerJar = Join-Path $BuildTools 'lib\apksigner.jar'
$aligned = Join-Path $Work 'bench-aligned.apk'
$baseline = Join-Path $Work 'bench-baseline.apk'
$ours = Join-Path $Work 'bench-native.apk'

# Runs one process, returns its wall time and peak working set.
function Invoke-Measured([string] $Exe, [string[]] $Arguments) {
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $process = Start-Process -FilePath $Exe -ArgumentList $Arguments -NoNewWindow -PassThru
    # Peak working set is only readable while the process runs, so sample it.
    $peak = 0
    while (-not $process.HasExited) {
        $process.Refresh()
        $peak = [math]::Max($peak, $process.PeakWorkingSet64)
        Start-Sleep -Milliseconds 5
    }
    $process.WaitForExit()
    $watch.Stop()
    if ($process.ExitCode -ne 0) { throw "$Exe exited with $($process.ExitCode)" }
    [pscustomobject]@{
        Seconds = $watch.Elapsed.TotalSeconds
        PeakMB = [math]::Round($peak / 1MB, 1)
    }
}

function Invoke-Ours {
    Remove-Item $ours -ErrorAction SilentlyContinue
    $run = Invoke-Measured $Tool @('--input', $InputApk, '--output', $ours, '--key', $KeyPem, '--cert', $CertPem)
    $run
}

function Invoke-Baseline {
    Remove-Item $aligned, $baseline -ErrorAction SilentlyContinue
    $align = Invoke-Measured $zipalign @('-f', '-P', '16', '4', $InputApk, $aligned)
    $sign = Invoke-Measured $Java @(
        '-jar', $apksignerJar, 'sign',
        '--ks', $Keystore, '--ks-type', 'PKCS12', '--ks-pass', 'pass:benchpw',
        '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true',
        '--v3-signing-enabled', 'false', '--v4-signing-enabled', 'false',
        '--out', $baseline, $aligned
    )
    [pscustomobject]@{
        Seconds = $align.Seconds + $sign.Seconds
        PeakMB = [math]::Max($align.PeakMB, $sign.PeakMB)
    }
}

$oursRuns = @()
$baselineRuns = @()
for ($i = 1; $i -le $Runs; $i++) {
    $oursRuns += Invoke-Ours
    $baselineRuns += Invoke-Baseline
}

Write-Output 'apksigner verify (native-packager output, then zipalign+sign output):'
foreach ($apk in @($ours, $baseline)) {
    & (Join-Path $BuildTools 'apksigner.bat') verify -v $apk |
        Select-String -Pattern 'Verifies|v2 scheme' | ForEach-Object { '  ' + $_.Line.Trim() }
}

Write-Output ''
Write-Output ('native-packager  seconds: ' + (($oursRuns.Seconds | ForEach-Object { '{0:N2}' -f $_ }) -join ', ') + '  peak MB: ' + (($oursRuns.PeakMB) -join ', '))
Write-Output ('zipalign+sign    seconds: ' + (($baselineRuns.Seconds | ForEach-Object { '{0:N2}' -f $_ }) -join ', ') + '  peak MB: ' + (($baselineRuns.PeakMB) -join ', '))
Write-Output ('median seconds: native-packager {0:N2}, zipalign+sign {1:N2}' -f `
    ($oursRuns.Seconds | Sort-Object)[[math]::Floor($Runs / 2)], `
    ($baselineRuns.Seconds | Sort-Object)[[math]::Floor($Runs / 2)])
