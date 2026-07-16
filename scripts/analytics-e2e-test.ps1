
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OrganizerId = "T001",
    [string]$Password = "123456"
)

$ErrorActionPreference = "Stop"
$Api = "$BaseUrl/api/v1"

function Assert-Equal($Actual, $Expected, [string]$Name) {
    if ($Actual -ne $Expected) {
        throw "$Name failed: expected $Expected, actual $Actual"
    }
    Write-Host "[PASS] $Name = $Actual" -ForegroundColor Green
}

$login = Invoke-RestMethod -Method Post -Uri "$Api/auth/login" `
    -ContentType "application/json; charset=utf-8" `
    -Body (@{ userId = $OrganizerId; password = $Password } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.data.token)" }

$activities = Invoke-RestMethod -Uri "$Api/activities/mine" -Headers $headers
$demo = $activities.data | Where-Object { $_.checkInCode -eq "AI-DEMO" } | Select-Object -First 1
if (-not $demo) {
    throw "Analytics demo activity not found. Import database/seed-analytics-demo.sql first."
}

$analysis = Invoke-RestMethod -Uri "$Api/analytics/activity/$($demo.id)" -Headers $headers
$metrics = $analysis.data.metrics
Assert-Equal $metrics.viewCount 20 "view count"
Assert-Equal $metrics.signupCount 7 "signup count"
Assert-Equal $metrics.signupRate 35.0 "signup rate"
Assert-Equal $metrics.checkInCount 4 "check-in count"
Assert-Equal $metrics.attendanceRate 57.1 "attendance rate"
Assert-Equal $metrics.feedbackCount 5 "feedback count"
Assert-Equal $metrics.avgRating 2.80 "average rating"
Assert-Equal $metrics.ratingDistribution.'2' 2 "2-star count"
Assert-Equal $metrics.ratingDistribution.'3' 2 "3-star count"
Assert-Equal $metrics.ratingDistribution.'4' 1 "4-star count"

Write-Host "Analytics metrics E2E test passed (activityId=$($demo.id))" -ForegroundColor Cyan
