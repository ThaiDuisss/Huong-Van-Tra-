# Script to register/update all Debezium Outbox Connectors to Kafka Connect
param(
    [string]$ConnectUrl = "http://100.99.2.30:8083",
    # Dia chi MySQL nhin tu ben trong container Kafka Connect tren box 2 (thuong la
    # ten container, vi du "mysql"). Bat buoc: dien sai thi connector khong doc binlog.
    [Parameter(Mandatory = $true)][string]$DbHost,
    # Password user debezium; giu gia tri cu lam default, nhung nen rotate.
    [string]$DbPassword = "debezium"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$connectorFiles = Get-ChildItem -Path $scriptDir -Filter "*-connector.json"

foreach ($file in $connectorFiles) {
    $json = Get-Content $file.FullName -Raw | ConvertFrom-Json
    $name = $json.name
    $json.config."database.hostname" = $DbHost
    $json.config."database.password" = $DbPassword
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
