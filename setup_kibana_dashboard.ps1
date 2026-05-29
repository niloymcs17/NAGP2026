$headers = @{
    "kbn-xsrf"     = "true"
    "Content-Type" = "application/json"
}

$kibanaBaseUrl = "http://192.168.68.117:5601"
$hasErrors     = $false

# Safe helper: get HTTP response body from a caught web exception
function Get-ResponseBody {
    param($err)
    if ($err.ErrorDetails -and $err.ErrorDetails.Message) {
        return $err.ErrorDetails.Message
    }
    try {
        $response = $err.Exception.Response
        if ($null -eq $response) { return "(no response body)" }
        $stream = $response.GetResponseStream()
        $stream.Position = 0
        return (New-Object System.IO.StreamReader($stream)).ReadToEnd()
    } catch {
        return "(could not read response body)"
    }
}

# Safe helper: get HTTP status code without crashing on null response
function Get-StatusCode {
    param($err)
    try {
        $response = $err.Exception.Response
        if ($null -eq $response) { return 0 }
        return [int]$response.StatusCode
    } catch { return 0 }
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "      Kibana Dashboard Auto-Installer     " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# ── Step 0: Connectivity check ────────────────────────────────────────────────
Write-Host "[0/3] Verifying connection to Kibana at $kibanaBaseUrl ..." -ForegroundColor Cyan
$connected = $false
try {
    $null = Invoke-RestMethod -Uri "$kibanaBaseUrl/api/status" -Method Get -TimeoutSec 10
    Write-Host "  -> Connected to Kibana successfully!" -ForegroundColor Green
    $connected = $true
} catch {
    Write-Host ""
    Write-Host "  [ERROR] Cannot connect to Kibana at $kibanaBaseUrl" -ForegroundColor Red
    Write-Host "  Make sure Docker Compose services are running:" -ForegroundColor Yellow
    Write-Host "      docker-compose up -d" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Details: $_" -ForegroundColor DarkGray
    $hasErrors = $true
}

if (-not $connected) {
    Write-Host ""
    Write-Host "  Skipping remaining steps due to connection failure." -ForegroundColor Red
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host ""
    exit 1
}

Write-Host ""

# ── Step 1: Data View ─────────────────────────────────────────────────────────
Write-Host "[1/3] Creating Data View (leave-portal-logs-*) ..." -ForegroundColor Cyan

$dataViewPayload = @'
{
  "data_view": {
    "id":            "leave-portal-logs",
    "title":         "leave-portal-logs-*",
    "name":          "leave-portal-logs-*",
    "timeFieldName": "@timestamp"
  }
}
'@

try {
    $null = Invoke-RestMethod -Uri "$kibanaBaseUrl/api/data_views/data_view" `
                              -Method Post -Headers $headers -Body $dataViewPayload
    Write-Host "  -> Data View created!" -ForegroundColor Green
} catch {
    $body       = Get-ResponseBody $_
    $statusCode = Get-StatusCode $_

    if ($statusCode -eq 409 -or
        ($statusCode -eq 400 -and ($body -match "Duplicate" -or $body -match "already exists"))) {
        Write-Host "  -> Data View already exists, continuing..." -ForegroundColor Yellow
    } else {
        Write-Host "  [ERROR] Failed to create Data View (HTTP $statusCode)" -ForegroundColor Red
        Write-Host "  Details: $body" -ForegroundColor DarkGray
        $hasErrors = $true
    }
}

Write-Host ""

# ── Step 2: Saved Search ──────────────────────────────────────────────────────
Write-Host "[2/3] Creating Saved Search (Microservices Log Stream) ..." -ForegroundColor Cyan

$searchPayload = @'
{
  "attributes": {
    "title":       "Microservices Log Stream",
    "description": "Live stream of all microservice logs - service, level, traceId, message.",
    "columns":     ["service", "level", "message", "logger_name", "traceId"],
    "sort":        [["@timestamp", "desc"]],
    "kibanaSavedObjectMeta": {
      "searchSourceJSON": "{\"query\":{\"query\":\"\",\"language\":\"kuery\"},\"filter\":[],\"index\":\"leave-portal-logs\"}"
    }
  },
  "references": [
    {
      "id":   "leave-portal-logs",
      "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
      "type": "index-pattern"
    }
  ]
}
'@

try {
    $null = Invoke-RestMethod `
        -Uri "$kibanaBaseUrl/api/saved_objects/search/microservices-log-stream?overwrite=true" `
        -Method Post -Headers $headers -Body $searchPayload
    Write-Host "  -> Saved Search created!" -ForegroundColor Green
} catch {
    $body       = Get-ResponseBody $_
    $statusCode = Get-StatusCode $_
    Write-Host "  [ERROR] Failed to create Saved Search (HTTP $statusCode)" -ForegroundColor Red
    Write-Host "  Details: $body" -ForegroundColor DarkGray
    $hasErrors = $true
}

Write-Host ""

# ── Step 3: Dashboard ─────────────────────────────────────────────────────────
Write-Host "[3/3] Creating Dashboard (Microservices Central Dashboard) ..." -ForegroundColor Cyan

$panelsJSON = '[{"version":"8.13.0","type":"search","gridData":{"x":0,"y":0,"w":48,"h":20,"i":"1"},"panelIndex":"1","id":"microservices-log-stream","embeddableConfig":{"enhancements":{}}}]'

$dashboardPayload = @"
{
  "attributes": {
    "title":       "Microservices Central Dashboard",
    "description": "Unified logging dashboard for all Employee Leave Portal microservices.",
    "panelsJSON":  $(ConvertTo-Json $panelsJSON -Compress),
    "optionsJSON": "{\"useMargins\":true,\"syncColors\":false,\"hidePanelTitles\":false}",
    "timeRestore": false,
    "kibanaSavedObjectMeta": {
      "searchSourceJSON": "{\"query\":{\"query\":\"\",\"language\":\"kuery\"},\"filter\":[]}"
    }
  },
  "references": [
    {
      "id":   "microservices-log-stream",
      "name": "1:panel_1",
      "type": "search"
    }
  ]
}
"@

try {
    $null = Invoke-RestMethod `
        -Uri "$kibanaBaseUrl/api/saved_objects/dashboard/microservices-central-dashboard?overwrite=true" `
        -Method Post -Headers $headers -Body $dashboardPayload
    Write-Host "  -> Dashboard created!" -ForegroundColor Green
} catch {
    $body       = Get-ResponseBody $_
    $statusCode = Get-StatusCode $_
    Write-Host "  [ERROR] Failed to create Dashboard (HTTP $statusCode)" -ForegroundColor Red
    Write-Host "  Details: $body" -ForegroundColor DarkGray
    $hasErrors = $true
}

# ── Summary ───────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan

if ($hasErrors) {
    Write-Host ""
    Write-Host "  [FAILED] One or more steps had errors." -ForegroundColor Red
    Write-Host "  Review the messages above, fix any issues, and re-run." -ForegroundColor Yellow
    Write-Host ""
} else {
    $dashboardUrl = "$kibanaBaseUrl/app/dashboards#/view/microservices-central-dashboard"
    Write-Host ""
    Write-Host "  [SUCCESS] All objects created successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Dashboard URL:" -ForegroundColor Cyan
    Write-Host "  $dashboardUrl" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Opening in your default browser..." -ForegroundColor Cyan
    try { Start-Process $dashboardUrl } catch {
        Write-Host "  (Could not auto-open browser - paste the URL above manually)" -ForegroundColor DarkGray
    }
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
