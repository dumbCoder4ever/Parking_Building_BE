try {
    $r = Invoke-WebRequest -Uri 'http://localhost:8080/api/auth/login' -Method POST -ContentType 'application/json' -Body '{}' -TimeoutSec 5
    Write-Host "Status:" $r.StatusCode
} catch {
    $status = $_.Exception.Response.StatusCode
    $msg = $_.Exception.Message.Substring(0, [Math]::Min(300, $_.Exception.Message.Length))
    Write-Host "Status:" $status
    Write-Host "Message:" $msg
}
