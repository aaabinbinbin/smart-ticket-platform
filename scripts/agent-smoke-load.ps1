param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Token = "",
    [int]$Concurrency = 10,
    [int]$Requests = 50,
    [string]$SessionPrefix = "load-agent",
    [switch]$Stream
)

$ErrorActionPreference = "Stop"

$headers = @{
    "Content-Type" = "application/json"
}

if ($Token -ne "") {
    $headers["Authorization"] = "Bearer $Token"
}

$endpoint = if ($Stream) { "/api/agent/chat/stream" } else { "/api/agent/chat" }
$url = "$BaseUrl$endpoint"
$messages = @(
    "query ticket 1001 status",
    "search history cases about login failure",
    "create ticket: test environment returns 500 during login",
    "transfer ticket 1001 to assignee 3"
)

Write-Host "Agent smoke load started"
Write-Host "Url=$url Concurrency=$Concurrency Requests=$Requests Stream=$Stream"

$jobs = @()
for ($i = 0; $i -lt $Requests; $i++) {
    $index = $i
    $jobs += Start-Job -ScriptBlock {
        param($Url, $Headers, $Messages, $Index, $SessionPrefix)

        $sessionId = "$SessionPrefix-$($Index % 5)"
        $message = $Messages[$Index % $Messages.Count]
        $body = @{
            sessionId = $sessionId
            message = $message
        } | ConvertTo-Json -Compress

        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            # Smoke load only verifies endpoint availability and latency; business semantics are validated by Java tests.
            Invoke-WebRequest -Method Post -Uri $Url -Headers $Headers -Body $body -UseBasicParsing | Out-Null
            $watch.Stop()
            [pscustomobject]@{
                Success = $true
                Duration = $watch.ElapsedMilliseconds
                Error = $null
            }
        } catch {
            $watch.Stop()
            [pscustomobject]@{
                Success = $false
                Duration = $watch.ElapsedMilliseconds
                Error = $_.Exception.Message
            }
        }
    } -ArgumentList $url, $headers, $messages, $index, $SessionPrefix

    while (($jobs | Where-Object { $_.State -eq "Running" }).Count -ge $Concurrency) {
        Start-Sleep -Milliseconds 100
    }
}

$results = @()
if ($jobs.Count -gt 0) {
    Wait-Job -Job $jobs | Out-Null
    $results = $jobs | Receive-Job
    Remove-Job -Job $jobs
}

$successCount = @($results | Where-Object { $_.Success }).Count
$failedCount = @($results | Where-Object { -not $_.Success }).Count
$durations = @($results | Where-Object { $_.Success } | ForEach-Object { [long]$_.Duration } | Sort-Object)
$avg = if ($durations.Count -eq 0) { 0 } else { [math]::Round(($durations | Measure-Object -Average).Average, 2) }
$p95Index = if ($durations.Count -eq 0) { 0 } else { [math]::Max(0, [math]::Ceiling($durations.Count * 0.95) - 1) }
$p95 = if ($durations.Count -eq 0) { 0 } else { $durations[$p95Index] }

Write-Host "Agent smoke load finished"
Write-Host "Success=$successCount Failed=$failedCount AvgMs=$avg P95Ms=$p95"

$errors = @($results | Where-Object { -not $_.Success -and $_.Error } | Select-Object -ExpandProperty Error)
if ($errors.Count -gt 0) {
    Write-Host "Sample errors:"
    $errors | Select-Object -First 5 | ForEach-Object { Write-Host "- $_" }
}

if ($failedCount -gt 0) {
    exit 1
}
