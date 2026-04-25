param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin1",
    [string]$Password = "123456",
    [long]$AssigneeId = 2,

    # Default is false to avoid SSH password blocking.
    # If your Windows can ssh to CentOS, run with:
    # .\scripts\run_rag_pgvector_e2e.ps1 -UseSshPgCheck $true -LinuxSshTarget "youruser@192.168.100.128"
    [bool]$UseSshPgCheck = $false,
    [string]$LinuxSshTarget = "root@192.168.100.128",
    [string]$PgContainer = "postgres_vector",
    [string]$PgUser = "postgres",
    [string]$PgDb = "smart_ticket_vector",

    [int]$PollTimes = 18,
    [int]$PollIntervalSeconds = 5
)

$ErrorActionPreference = "Stop"

function Step([string]$Message) {
    Write-Host ""
    Write-Host "========== $Message ==========" -ForegroundColor Cyan
}

function Ok([string]$Message) {
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Warn([string]$Message) {
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $params = @{
        Method = $Method
        Uri = $Url
        Headers = $Headers
        TimeoutSec = 60
    }

    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = ($Body | ConvertTo-Json -Depth 20)
    }

    try {
        return Invoke-RestMethod @params
    } catch {
        Fail "HTTP request failed: $Method $Url"

        if ($_.Exception.Response -ne $null) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $respBody = $reader.ReadToEnd()
                Write-Host $respBody -ForegroundColor DarkYellow
            } catch {
                Write-Host $_.Exception.Message -ForegroundColor DarkYellow
            }
        } else {
            Write-Host $_.Exception.Message -ForegroundColor DarkYellow
        }

        throw
    }
}

function Assert-ApiSuccess {
    param(
        [object]$Response,
        [string]$StepName
    )

    if ($null -eq $Response) {
        throw "$StepName returned empty response"
    }

    if ($Response.PSObject.Properties.Name -contains "success") {
        if ($Response.success -ne $true) {
            $msg = $Response.message
            $code = $Response.code
            throw "$StepName failed: code=$code, message=$msg"
        }
    }
}

function Get-DataField {
    param(
        [object]$Response,
        [string]$FieldName
    )

    if ($null -eq $Response) {
        return $null
    }

    if ($Response.PSObject.Properties.Name -contains "data") {
        $data = $Response.data
        if ($null -ne $data -and $data.PSObject.Properties.Name -contains $FieldName) {
            return $data.$FieldName
        }
    }

    if ($Response.PSObject.Properties.Name -contains $FieldName) {
        return $Response.$FieldName
    }

    return $null
}

function Invoke-PgSqlViaSsh {
    param([string]$Sql)

    if (-not $UseSshPgCheck) {
        return $null
    }

    $escaped = $Sql.Replace('"', '\"')
    $remoteCmd = "docker exec -i $PgContainer psql -U $PgUser -d $PgDb -t -A -c `"$escaped`""
    $result = ssh $LinuxSshTarget $remoteCmd
    return ($result | Out-String).Trim()
}

function Get-VectorStoreCount {
    $raw = Invoke-PgSqlViaSsh "SELECT COUNT(*) FROM vector_store;"
    if ($null -eq $raw -or $raw -eq "") {
        return -1
    }

    $first = ($raw -split "`n" | Select-Object -First 1).Trim()
    return [int]$first
}

function Show-VectorStorePreview {
    $sql = "SELECT COALESCE(LEFT(content, 100), '') || ' | dims=' || vector_dims(embedding) FROM vector_store ORDER BY id DESC LIMIT 5;"
    $raw = Invoke-PgSqlViaSsh $sql

    if ($null -ne $raw -and $raw.Trim() -ne "") {
        Write-Host $raw -ForegroundColor Gray
    } else {
        Warn "vector_store has no preview rows"
    }
}

Step "0. Precheck"
Write-Host "BaseUrl=$BaseUrl"
Write-Host "Username=$Username"
Write-Host "AssigneeId=$AssigneeId"
Write-Host "UseSshPgCheck=$UseSshPgCheck"
if ($UseSshPgCheck) {
    Write-Host "LinuxSshTarget=$LinuxSshTarget"
    Write-Host "PgContainer=$PgContainer"
    Write-Host "PgDb=$PgDb"
}

$beforeCount = -1
if ($UseSshPgCheck) {
    Step "1. Check pgvector count before test"
    $beforeCount = Get-VectorStoreCount
    Write-Host "vector_store count before: $beforeCount"
} else {
    Step "1. Skip pgvector count before test"
    Warn "SSH pgvector polling is disabled. You can manually check on CentOS:"
    Write-Host "docker exec -it $PgContainer psql -U $PgUser -d $PgDb -c `"SELECT COUNT(*) FROM vector_store;`""
}

Step "2. Login"
$loginResp = Invoke-Json `
    -Method "POST" `
    -Url "$BaseUrl/api/auth/login" `
    -Body @{
        "username" = $Username
        "password" = $Password
    }

Assert-ApiSuccess $loginResp "Login"

$token = Get-DataField $loginResp "accessToken"
if ([string]::IsNullOrWhiteSpace($token)) {
    $token = Get-DataField $loginResp "token"
}
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Login succeeded but token not found. Expected data.accessToken or data.token."
}

$authHeaders = @{
    "Authorization" = "Bearer $token"
}
Ok "Login OK"

Step "3. Create test ticket"
$ts = Get-Date -Format "yyyyMMddHHmmss"
$idempotencyKey = "rag-pgvector-e2e-$ts"

$createHeaders = @{
    "Authorization" = "Bearer $token"
    "Idempotency-Key" = $idempotencyKey
}

$createResp = Invoke-Json `
    -Method "POST" `
    -Url "$BaseUrl/api/tickets" `
    -Headers $createHeaders `
    -Body @{
        "title" = "RAG pgvector test login token expired $ts"
        "description" = "In test environment, user login fails with token expired. Re-login still fails. Need to check auth-service cache, token signing key, and auth-service status. This ticket is used to verify RAG knowledge build and pgvector insertion."
        "type" = "INCIDENT"
        "category" = "SYSTEM"
        "priority" = "HIGH"
        "idempotencyKey" = $idempotencyKey
        "typeProfile" = @{
            "symptom" = "Login fails in test environment with token expired. Re-login still fails."
            "impactScope" = "Test environment login is unavailable for verification users."
        }
    }

Assert-ApiSuccess $createResp "Create ticket"

$ticketId = Get-DataField $createResp "id"
if ($null -eq $ticketId) {
    throw "Create ticket succeeded but data.id not found."
}
Ok "Ticket created: ticketId=$ticketId"

Step "4. Assign ticket"
$assignResp = Invoke-Json `
    -Method "PUT" `
    -Url "$BaseUrl/api/tickets/$ticketId/assign" `
    -Headers $authHeaders `
    -Body @{
        "assigneeId" = $AssigneeId
    }

Assert-ApiSuccess $assignResp "Assign ticket"
Ok "Ticket assigned to assigneeId=$AssigneeId"

Step "5. Add comments"
$commentResp1 = Invoke-Json `
    -Method "POST" `
    -Url "$BaseUrl/api/tickets/$ticketId/comments" `
    -Headers $authHeaders `
    -Body @{
        "content" = "Handling note: checked auth-service logs. The test environment still used old signing-key cache, causing token verification failure. Cache was cleared and auth-service was restarted."
    }
Assert-ApiSuccess $commentResp1 "Add comment 1"
Ok "Comment 1 added"

$commentResp2 = Invoke-Json `
    -Method "POST" `
    -Url "$BaseUrl/api/tickets/$ticketId/comments" `
    -Headers $authHeaders `
    -Body @{
        "content" = "Resolution: clear auth-service cache, restart auth-service, regenerate user token, and then verify login. Before reusing this solution, confirm environment, signing-key version, and impact scope."
    }
Assert-ApiSuccess $commentResp2 "Add comment 2"
Ok "Comment 2 added"

Step "6. Move ticket to RESOLVED"
$statusResp = Invoke-Json `
    -Method "PUT" `
    -Url "$BaseUrl/api/tickets/$ticketId/status" `
    -Headers $authHeaders `
    -Body @{
        "targetStatus" = "RESOLVED"
        "solutionSummary" = "Root cause: auth-service in test environment cached an old signing key, causing token verification failure. Fix: clear auth-service cache, restart service, regenerate token, and verify login."
    }

Assert-ApiSuccess $statusResp "Move to RESOLVED"
Ok "Ticket moved to RESOLVED"

Step "7. Close ticket"
$closeResp = Invoke-Json `
    -Method "PUT" `
    -Url "$BaseUrl/api/tickets/$ticketId/close" `
    -Headers $authHeaders

Assert-ApiSuccess $closeResp "Close ticket"
Ok "Ticket closed. Knowledge build should be triggered."

Step "8. Poll vector_store"
if ($UseSshPgCheck) {
    for ($i = 1; $i -le $PollTimes; $i++) {
        Start-Sleep -Seconds $PollIntervalSeconds
        $currentCount = Get-VectorStoreCount
        Write-Host "poll ${i}/${PollTimes}: vector_store count=$currentCount"

        if ($currentCount -gt $beforeCount) {
            Ok "vector_store inserted new rows: before=$beforeCount, current=$currentCount"
            Show-VectorStorePreview
            break
        }

        if ($i -eq $PollTimes) {
            Warn "vector_store count did not increase. Check IDEA logs, MySQL ticket_knowledge_build_task, and RabbitMQ."
        }
    }
} else {
    Warn "Skipped pgvector polling. Please run this on CentOS after a few seconds:"
    Write-Host "docker exec -it $PgContainer psql -U $PgUser -d $PgDb -c `"SELECT COUNT(*) FROM vector_store;`""
    Write-Host "docker exec -it $PgContainer psql -U $PgUser -d $PgDb -c `"SELECT id, LEFT(content, 100) AS content_preview, metadata, vector_dims(embedding) AS dims FROM vector_store LIMIT 5;`""
    Write-Host ""
    Warn "Sleep 30 seconds to wait for async knowledge build to complete..."
    Start-Sleep -Seconds 30
}

Step "9. Trigger Agent/RAG retrieval"
$agentResp = Invoke-Json `
    -Method "POST" `
    -Url "$BaseUrl/api/agent/chat" `
    -Headers $authHeaders `
    -Body @{
        "sessionId" = "rag-pgvector-e2e-session-$ts"
        "message" = "How to handle login token expired in test environment? Please refer to historical ticket experience."
    }

Assert-ApiSuccess $agentResp "Agent retrieval"
Ok "Agent request OK"

$intent = Get-DataField $agentResp "intent"
$traceId = Get-DataField $agentResp "traceId"
$reply = Get-DataField $agentResp "reply"

Write-Host "intent=$intent"
Write-Host "traceId=$traceId"
Write-Host "reply:"
Write-Host $reply -ForegroundColor Gray

Step "10. Direct RAG search"
$ragResp = Invoke-Json `
    -Method "GET" `
    -Url "$BaseUrl/api/rag/search?query=login+token+expired+test+environment&topK=5" `
    -Headers $authHeaders

Assert-ApiSuccess $ragResp "RAG search"
Ok "RAG search OK"

$retrievalPath = Get-DataField $ragResp "retrievalPath"
$fallbackUsed = Get-DataField $ragResp "fallbackUsed"
$ragHits = Get-DataField $ragResp "hits"

Write-Host "retrievalPath=$retrievalPath"
Write-Host "fallbackUsed=$fallbackUsed"
Write-Host "hits count=$((@($ragHits)).Count)"

Step "11. Check IDEA logs for the following"
Write-Host ""
Write-Host "Look for these log entries in IDEA console:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  1. Knowledge build task published: taskId=..., ticketId=..." -ForegroundColor White
Write-Host "  2. Knowledge build task started: taskId=..., ticketId=..." -ForegroundColor White
Write-Host "  3. Vector build started: knowledgeId=..., vectorStoreEnabled=true, chunks=8" -ForegroundColor White
Write-Host "  4. PGvector direct write completed: table=public.vector_store, documents=8, updated=8" -ForegroundColor White
Write-Host "  5. Knowledge build task completed: taskId=..., ticketId=..., knowledgeId=..., chunks=8" -ForegroundColor White
Write-Host "  6. RAG retrieval path=PGVECTOR, fallbackUsed=false" -ForegroundColor White
Write-Host ""
Write-Host "If vector_store count is still 0, check MySQL build_task table:" -ForegroundColor Yellow
Write-Host "  SELECT id, ticket_id, status, retry_count, error_message FROM ticket_knowledge_build_task ORDER BY id DESC LIMIT 10;"
Write-Host "  SELECT id, ticket_id, status FROM ticket_knowledge ORDER BY id DESC LIMIT 10;"
Write-Host ""
Write-Host "Manually verify pgvector data on CentOS:" -ForegroundColor Yellow
Write-Host "  docker exec -it $PgContainer psql -U $PgUser -d $PgDb -c `"SELECT COUNT(*) FROM vector_store;`""
Write-Host "  docker exec -it $PgContainer psql -U $PgUser -d $PgDb -c `"SELECT id, LEFT(content, 100), metadata, vector_dims(embedding) FROM vector_store LIMIT 5;`""
Write-Host ""
Write-Host "==============================" -ForegroundColor Cyan
Write-Host "E2E test completed!" -ForegroundColor Green
Write-Host "==============================" -ForegroundColor Cyan
