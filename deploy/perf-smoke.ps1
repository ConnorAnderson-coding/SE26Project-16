# 可选：对经 Nginx 的登录接口做 100 并发冒烟（需已 docker 部署）
# 用法: .\perf-smoke.ps1 [-BaseUrl http://localhost] [-Concurrency 100]

param(
    [string]$BaseUrl = "http://localhost",
    [int]$Concurrency = 100,
    [string]$Username = "524030910001",
    [string]$Password = "123456"
)

$ErrorActionPreference = "Stop"
$url = "$BaseUrl/api/v1/auth/login"
$body = (@{ username = $Username; password = $Password } | ConvertTo-Json)

Write-Host "POST $url  x $Concurrency" -ForegroundColor Cyan

$jobs = 1..$Concurrency | ForEach-Object {
    Start-Job -ScriptBlock {
        param($U, $B)
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $null = Invoke-RestMethod -Method Post -Uri $U -ContentType "application/json; charset=utf-8" -Body $B -TimeoutSec 10
            $sw.Stop()
            [pscustomobject]@{ Ok = $true; Ms = $sw.ElapsedMilliseconds }
        }
        catch {
            $sw.Stop()
            [pscustomobject]@{ Ok = $false; Ms = $sw.ElapsedMilliseconds; Error = $_.Exception.Message }
        }
    } -ArgumentList $url, $body
}

$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job -Force

$ok = @($results | Where-Object Ok)
$fail = @($results | Where-Object { -not $_.Ok })
$sorted = $ok.Ms | Sort-Object
$p95Index = [Math]::Max(0, [Math]::Ceiling($sorted.Count * 0.95) - 1)

Write-Host ("成功: {0}  失败: {1}" -f $ok.Count, $fail.Count)
if ($ok.Count -gt 0) {
    Write-Host ("耗时 ms  avg={0:N0}  max={1:N0}  p95≈{2:N0}" -f `
        (($ok.Ms | Measure-Object -Average).Average), `
        (($ok.Ms | Measure-Object -Maximum).Maximum), `
        $sorted[$p95Index])
}
if ($fail.Count -gt 0) {
    $fail | Select-Object -First 3 | Format-List
    exit 1
}
