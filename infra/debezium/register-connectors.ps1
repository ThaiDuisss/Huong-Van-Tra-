# Script to register/update all Debezium Outbox Connectors to Kafka Connect
param(
    [string]$ConnectUrl = "http://100.93.252.63:8083"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$connectorFiles = Get-ChildItem -Path $scriptDir -Filter "*-connector.json"

foreach ($file in $connectorFiles) {
    $json = Get-Content $file.FullName -Raw | ConvertFrom-Json
    $name = $json.name
    $configJson = $json.config | ConvertTo-Json -Depth 10 -Compress
    $url = "$ConnectUrl/connectors/$name/config"

    Write-Host "Registering/Updating $name via $url..."
    try {
        $response = Invoke-RestMethod -Uri $url -Method Put -ContentType "application/json" -Body $configJson
        Write-Host "  -> Successfully updated $name" -ForegroundColor Green
    } catch {
        $errMsg = $_.Exception.Message
        Write-Host "  -> Failed to update ${name}: $errMsg" -ForegroundColor Red
    }
}

Write-Host "`nChecking connector status..."
Start-Sleep -Seconds 3
try {
    $statuses = Invoke-RestMethod -Uri "$ConnectUrl/connectors?expand=status"
    foreach ($prop in $statuses.PSObject.Properties) {
        $name = $prop.Name
        $status = $prop.Value.status
        $connState = $status.connector.state
        $tasks = ($status.tasks | ForEach-Object { "Task $($_.id):$($_.state)" }) -join ", "
        Write-Host "Connector '$name': $connState | $tasks" -ForegroundColor Cyan
    }
} catch {
    $errMsg = $_.Exception.Message
    Write-Host "Failed to fetch connector status: $errMsg" -ForegroundColor Red
}
