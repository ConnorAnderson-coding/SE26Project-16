#Requires -Version 5.1
<#
.SYNOPSIS
  Campus Activity Platform - API end-to-end test

.EXAMPLE
  .\scripts\e2e-api-test.ps1
  .\scripts\e2e-api-test.ps1 -BaseUrl http://127.0.0.1:8080
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$StudentId = "524030910001",
    [string]$OrganizerId = "T001",
    [string]$AdminId = "admin001",
    [string]$Password = "123456"
)

$ErrorActionPreference = "Stop"
$Api = "$BaseUrl/api/v1"
$Passed = 0
$Failed = 0

function Write-Step($Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Record-Pass($Name) {
    $script:Passed++
    Write-Host "[PASS] $Name" -ForegroundColor Green
}

function Record-Fail($Name, $Detail) {
    $script:Failed++
    Write-Host "[FAIL] $Name - $Detail" -ForegroundColor Red
}

function Invoke-Api {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = "",
        [int[]]$ExpectHttp = @(200)
    )

    $headers = @{ Accept = "application/json" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }

    $uri = "$Api$Path"
    $params = @{
        Method      = $Method
        Uri         = $uri
        Headers     = $headers
        ContentType = "application/json; charset=utf-8"
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }

    try {
        $response = Invoke-WebRequest @params -UseBasicParsing
        $httpCode = [int]$response.StatusCode
    }
    catch {
        if ($_.Exception.Response) {
            $httpCode = [int]$_.Exception.Response.StatusCode.value__
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $raw = $reader.ReadToEnd()
            $reader.Close()
            try { return @{ Http = $httpCode; Json = ($raw | ConvertFrom-Json); Raw = $raw } }
            catch { return @{ Http = $httpCode; Json = $null; Raw = $raw } }
        }
        throw
    }

    if ($ExpectHttp -notcontains $httpCode) {
        throw "HTTP $httpCode, expected $($ExpectHttp -join '/'), body=$($response.Content)"
    }

    $json = $null
    if ($response.Content) {
        $json = $response.Content | ConvertFrom-Json
    }
    return @{ Http = $httpCode; Json = $json; Raw = $response.Content }
}

function Assert-ApiOk {
    param($Response, [string]$StepName)
    if ($null -eq $Response.Json) {
        Record-Fail $StepName "response is not JSON"
        return $false
    }
    if ($Response.Json.code -ne 0) {
        Record-Fail $StepName "code=$($Response.Json.code) message=$($Response.Json.message)"
        return $false
    }
    Record-Pass $StepName
    return $true
}

function Login-User {
    param([string]$UserId)
    $resp = Invoke-Api -Method POST -Path "/auth/login" -Body @{
        userId   = $UserId
        password = $Password
    }
    if (-not (Assert-ApiOk $resp "login $UserId")) { return $null }
    return $resp.Json.data.token
}

try {
    Write-Host "Campus Activity - API E2E Test" -ForegroundColor Magenta
    Write-Host "BaseUrl: $BaseUrl"

    Write-Step "0. Environment"
    try {
        Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -UseBasicParsing -TimeoutSec 5 | Out-Null
        Record-Pass "backend health"
    }
    catch {
        Record-Fail "backend health" "cannot connect to $BaseUrl"
        exit 1
    }

    Write-Step "1. Auth"
    $studentToken = Login-User $StudentId
    $organizerToken = Login-User $OrganizerId
    $adminToken = Login-User $AdminId
    if (-not $studentToken -or -not $organizerToken -or -not $adminToken) { exit 1 }

    Invoke-Api -Method POST -Path "/auth/login" -Body @{ userId = $StudentId; password = "wrong" } -ExpectHttp @(401) | Out-Null
    Record-Pass "wrong password returns 401"

    Invoke-Api -Path "/users/me" -ExpectHttp @(403) | Out-Null
    Record-Pass "no token returns 403"

    Write-Step "2. Activity browse and cache"
    Assert-ApiOk (Invoke-Api -Path "/activities?page=0&size=10" -Token $studentToken) "activity list" | Out-Null

    $detail1 = Invoke-Api -Path "/activities/1" -Token $studentToken
    if (Assert-ApiOk $detail1 "activity detail first read") {
        if (-not $detail1.Json.data.organizerName) {
            Record-Fail "activity detail fields" "missing organizerName"
        }
    }

    Assert-ApiOk (Invoke-Api -Path "/activities/1" -Token $studentToken) "activity detail cache hit" | Out-Null
    Assert-ApiOk (Invoke-Api -Path "/activities/recommended?limit=3" -Token $studentToken) "recommended" | Out-Null
    Assert-ApiOk (Invoke-Api -Path "/home/stats" -Token $studentToken) "home stats" | Out-Null

    Write-Step "3. Favorites"
    Assert-ApiOk (Invoke-Api -Method POST -Path "/favorites/2" -Token $studentToken) "favorite toggle" | Out-Null
    Assert-ApiOk (Invoke-Api -Path "/favorites/2/status" -Token $studentToken) "favorite status" | Out-Null
    Assert-ApiOk (Invoke-Api -Path "/favorites" -Token $studentToken) "favorite list" | Out-Null

    Write-Step "4. Registration"
    $signup = Invoke-Api -Method POST -Path "/registrations" -Token $studentToken -Body @{ activityId = 2 }
    $registrationId = $null
    if ($signup.Json.code -eq 0) {
        Record-Pass "signup activity 2"
        $registrationId = $signup.Json.data.id
    }
    elseif ($signup.Json.message -like "*报名*") {
        Record-Pass "signup activity 2 already exists"
        $regList = Invoke-Api -Path "/registrations/mine" -Token $studentToken
        $registrationId = ($regList.Json.data | Where-Object { $_.activityId -eq 2 } | Select-Object -First 1).id
    }
    else {
        Record-Fail "signup activity 2" $signup.Json.message
    }

    Assert-ApiOk (Invoke-Api -Path "/registrations/status?activityId=2" -Token $studentToken) "signup status" | Out-Null

    if ($registrationId) {
        Assert-ApiOk (Invoke-Api -Method PUT -Path "/registrations/$registrationId/review" -Token $organizerToken -Body @{ approved = $true }) "organizer review" | Out-Null
    }

    Assert-ApiOk (Invoke-Api -Path "/registrations?activityId=2" -Token $organizerToken) "organizer registration list" | Out-Null

    Write-Step "5. Activity CRUD"
    $start = (Get-Date).AddDays(10).ToString("yyyy-MM-ddTHH:mm:ss")
    $end = (Get-Date).AddDays(10).AddHours(2).ToString("yyyy-MM-ddTHH:mm:ss")
    $create = Invoke-Api -Method POST -Path "/activities" -Token $organizerToken -Body @{
        title           = "E2E Test Activity"
        category        = "academic"
        description     = "created by e2e script"
        startTime       = $start
        endTime         = $end
        location        = "Lab 101"
        maxParticipants = 20
        tags            = @("E2E", "AI")
    }
    $newActivityId = $null
    if (Assert-ApiOk $create "create activity") {
        $newActivityId = $create.Json.data.id
    }

    if ($newActivityId) {
        Assert-ApiOk (Invoke-Api -Method PUT -Path "/activities/$newActivityId" -Token $organizerToken -Body @{
            title           = "E2E Test Activity Updated"
            category        = "academic"
            description     = "updated"
            startTime       = $start
            endTime         = $end
            location        = "Lab 102"
            maxParticipants = 25
            tags            = @("E2E")
        }) "update activity" | Out-Null

        $forbidden = Invoke-Api -Method PUT -Path "/activities/$newActivityId" -Token $studentToken -Body @{
            title = "Illegal"; category = "academic"; description = "x"
            startTime = $start; endTime = $end; location = "x"; maxParticipants = 1
        } -ExpectHttp @(400)
        if ($forbidden.Json.code -eq 403) { Record-Pass "non-organizer update denied" }
        else { Record-Fail "non-organizer update" "expected code 403" }
    }

    Assert-ApiOk (Invoke-Api -Path "/activities/mine" -Token $organizerToken) "organizer my activities" | Out-Null

    Write-Step "6. Feedback"
    Assert-ApiOk (Invoke-Api -Method POST -Path "/feedbacks" -Token $studentToken -Body @{
        activityId = 1; rating = 5; content = "E2E feedback"
    }) "submit feedback" | Out-Null
    Assert-ApiOk (Invoke-Api -Path "/feedbacks?activityId=1" -Token $studentToken) "feedback list" | Out-Null

    Write-Step "7. User profile"
    Assert-ApiOk (Invoke-Api -Path "/users/me" -Token $studentToken) "get profile" | Out-Null
    Assert-ApiOk (Invoke-Api -Method PUT -Path "/users/me" -Token $studentToken -Body @{
        name = "Zhang San"; college = "Software College"; grade = "2024"
        interests = @("AI"); availableTime = @("weekend")
    }) "update profile" | Out-Null

    Write-Step "8. Activity record"
    if ($newActivityId) {
        Assert-ApiOk (Invoke-Api -Method POST -Path "/activities/$newActivityId/record" -Token $organizerToken -Body @{
            summary = "E2E summary"; photos = @("https://example.com/p.jpg")
        }) "publish record" | Out-Null

        $ended = Invoke-Api -Path "/activities/$newActivityId" -Token $studentToken
        if (Assert-ApiOk $ended "activity after record") {
            if ($ended.Json.data.status -ne "ended") {
                Record-Fail "activity status" "expected ended got $($ended.Json.data.status)"
            }
        }
    }

    Write-Step "9. Cleanup"
    if ($newActivityId) {
        Assert-ApiOk (Invoke-Api -Method DELETE -Path "/activities/$newActivityId" -Token $organizerToken) "delete test activity" | Out-Null
    }

    Write-Host ""
    Write-Host "========================================" -ForegroundColor Magenta
    Write-Host "Done: passed=$Passed failed=$Failed" -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
    Write-Host "========================================" -ForegroundColor Magenta

    if ($Failed -gt 0) { exit 1 }
    exit 0
}
catch {
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "passed=$Passed failed=$Failed" -ForegroundColor Yellow
    exit 1
}
