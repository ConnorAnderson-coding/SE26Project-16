#Requires -Version 5.1
[CmdletBinding()]
param(
    [int]$Threads = 100,
    [int]$RampUpSeconds = 30,
    [int]$DurationSeconds = 300,
    [string]$RunName = "formal-100-users"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$TestPlan = Join-Path $PSScriptRoot "jmeter\campus-activity-load-test.jmx"
$ResultsRoot = Join-Path $PSScriptRoot "results"
$RunDir = Join-Path $ResultsRoot $RunName
$Jtl = Join-Path $RunDir "results.jtl"
$Log = Join-Path $RunDir "jmeter.log"
$Report = Join-Path $RunDir "html-report"
$JMeter = Join-Path $env:LOCALAPPDATA "CodexTools\apache-jmeter-5.6.3\bin\jmeter.bat"

if (-not (Test-Path $JMeter)) {
    throw "Apache JMeter 5.6.3 was not found at $JMeter"
}
if (-not (Test-Path $TestPlan)) {
    throw "JMeter test plan was not found at $TestPlan"
}
try {
    $health = Invoke-WebRequest -Uri "http://127.0.0.1:8080/actuator/health" -UseBasicParsing -TimeoutSec 5
    if ($health.StatusCode -ne 200) {
        throw "Backend health check returned HTTP $($health.StatusCode)"
    }
}
catch {
    throw "Backend is not ready at http://127.0.0.1:8080: $($_.Exception.Message)"
}

if (Test-Path $RunDir) {
    $resolvedResults = [IO.Path]::GetFullPath($ResultsRoot)
    $resolvedRun = [IO.Path]::GetFullPath($RunDir)
    if (-not $resolvedRun.StartsWith($resolvedResults + [IO.Path]::DirectorySeparatorChar)) {
        throw "Refusing to clear a result path outside $resolvedResults"
    }
    Remove-Item -LiteralPath $RunDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

# This desktop environment omits System32 from PATH; JMeter's Windows launcher
# needs findstr.exe when validating JAVA_HOME.
$env:Path = "C:\Windows\System32;" + $env:Path
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"

Push-Location $ProjectRoot
try {
    & $JMeter `
        -n `
        -t $TestPlan `
        -l $Jtl `
        -j $Log `
        -e `
        -o $Report `
        "-Jthreads=$Threads" `
        "-Jrampup=$RampUpSeconds" `
        "-Jduration=$DurationSeconds"
    if ($LASTEXITCODE -ne 0) {
        throw "JMeter exited with code $LASTEXITCODE. See $Log"
    }
}
finally {
    Pop-Location
}

Write-Host "JTL:    $Jtl"
Write-Host "Report: $(Join-Path $Report 'index.html')"
