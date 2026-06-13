<#
.SYNOPSIS
  Script test luồng thanh toán Parking Building BE.

.DESCRIPTION
  Hỗ trợ 3 chế độ:
    - CASH      : checkout tiền mặt (không cần PayOS/ngrok)
    - PAYOS     : initiate -> (simulate hoặc mở link PayOS) -> confirm-by-staff -> checkout
    - VNPAY     : tương tự PayOS với VNPay

.PARAMETER Mode
  CASH | PAYOS | VNPAY

.PARAMETER Simulate
  Bỏ qua cổng thanh toán thật, gọi POST /api/payments/confirm-success (dev/test).
  Không dùng với -OpenPaymentUrl.

.PARAMETER OpenPaymentUrl
  Mở paymentUrl trên trình duyệt (PayOS/VNPay thật). Script chờ bạn thanh toán xong rồi kiểm tra trạng thái.

.EXAMPLE
  # Tiền mặt — cần session ACTIVE + ticketCode
  .\test_payment.ps1 -Mode CASH -TicketCode "TKT-ABC123"

.EXAMPLE
  # PayOS giả lập webhook (không cần ngrok)
  .\test_payment.ps1 -Mode PAYOS -Simulate

.EXAMPLE
  # PayOS thật — mở link thanh toán
  .\test_payment.ps1 -Mode PAYOS -OpenPaymentUrl

.EXAMPLE
  # VNPay giả lập
  .\test_payment.ps1 -Mode VNPAY -Simulate -TicketCode "TKT-ABC123"
#>

param(
    [ValidateSet("CASH", "PAYOS", "VNPAY")]
    [string]$Mode = "PAYOS",

    [string]$BaseUrl = "http://localhost:8080",

    [string]$StaffEmail = "staff1@example.com",
    [string]$StaffPassword = "123",

    [string]$DriverEmail = "driver1@example.com",
    [string]$DriverPassword = "1",

    [string]$TicketCode = "",

    [decimal]$Amount = 0,

    [switch]$Simulate,
    [switch]$OpenPaymentUrl,

    [int]$PollSeconds = 3,
    [int]$PollMaxAttempts = 40
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok([string]$Message) {
    Write-Host "OK  $Message" -ForegroundColor Green
}

function Write-Warn([string]$Message) {
    Write-Host "!!  $Message" -ForegroundColor Yellow
}

function Write-Err([string]$Message) {
    Write-Host "ERR $Message" -ForegroundColor Red
}

function Invoke-Api {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = "",
        [hashtable]$Query = @{}
    )

    $uriBuilder = [System.UriBuilder]"$BaseUrl$Path"
    if ($Query.Count -gt 0) {
        $queryString = ($Query.GetEnumerator() | ForEach-Object {
            "{0}={1}" -f [uri]::EscapeDataString([string]$_.Key), [uri]::EscapeDataString([string]$_.Value)
        }) -join "&"
        $uriBuilder.Query = $queryString
    }
    $uri = $uriBuilder.Uri.AbsoluteUri

    $headers = @{ Accept = "application/json" }
    if ($Token) {
        $headers.Authorization = "Bearer $Token"
    }

    $params = @{
        Uri         = $uri
        Method      = $Method
        Headers     = $headers
        ContentType = "application/json"
        TimeoutSec  = 30
    }

    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }

    try {
        $response = Invoke-RestMethod @params
    }
    catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            try {
                $errJson = $_.ErrorDetails.Message | ConvertFrom-Json
                if ($errJson.message) { $detail = $errJson.message }
                elseif ($errJson.error) { $detail = $errJson.error }
            }
            catch {
                $detail = $_.ErrorDetails.Message
            }
        }
        throw "API $Method $Path failed: $detail"
    }

    if ($response.PSObject.Properties.Name -contains "success" -and $response.success -eq $false) {
        throw "API $Method $Path failed: $($response.message)"
    }

    return $response
}

function Login-User {
    param(
        [string]$Email,
        [string]$Password
    )

    $resp = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{
        email    = $Email
        password = $Password
    }

    if (-not $resp.data.token) {
        throw "Login failed for $Email"
    }

    return @{
        Token    = $resp.data.token
        UserId   = $resp.data.userId
        Email    = $resp.data.email
        Role     = $resp.data.role
        FullName = $resp.data.fullName
    }
}

function Get-CurrentSession {
    param([string]$DriverToken)

    $resp = Invoke-Api -Method GET -Path "/api/users/me/sessions/current" -Token $DriverToken
    return $resp.data
}

function Get-Estimate {
    param(
        [string]$Token,
        [string]$Code
    )

    $resp = Invoke-Api -Method GET -Path "/api/sessions/estimate" -Token $Token -Query @{
        ticketCode = $Code
        lostTicket = "false"
    }
    return $resp.data
}

function Get-PaymentDetails {
    param(
        [string]$Token,
        [string]$PaymentId
    )

    $resp = Invoke-Api -Method GET -Path "/api/payments/$PaymentId" -Token $Token
    return $resp.data
}

function Wait-PaymentSuccess {
    param(
        [string]$Token,
        [string]$PaymentId
    )

    for ($i = 1; $i -le $PollMaxAttempts; $i++) {
        $payment = Get-PaymentDetails -Token $Token -PaymentId $PaymentId
        $status = [string]$payment.paymentStatus
        Write-Host "    Poll $i/$PollMaxAttempts : paymentStatus = $status"

        if ($status -eq "PAID" -or $status -eq "CONFIRMED" -or $status -eq "SUCCESS") {
            return $payment
        }
        if ($status -eq "FAILED") {
            throw "Payment $PaymentId failed"
        }

        Start-Sleep -Seconds $PollSeconds
    }

    throw "Timeout waiting for payment $PaymentId to become SUCCESS"
}

function Invoke-Checkout {
    param(
        [string]$StaffToken,
        [string]$Code,
        [string]$PaymentMethod
    )

    $resp = Invoke-Api -Method POST -Path "/api/sessions/checkout" -Token $StaffToken -Body @{
        ticketCode    = $Code
        paymentMethod = $PaymentMethod
    }
    return $resp.data
}

Write-Host "Parking Payment Test Script" -ForegroundColor Magenta
Write-Host "BaseUrl: $BaseUrl | Mode: $Mode"

if ($OpenPaymentUrl -and $Simulate) {
    throw "Khong dung -Simulate va -OpenPaymentUrl cung luc."
}

if ($Mode -eq "CASH" -and ($Simulate -or $OpenPaymentUrl)) {
    Write-Warn "Mode CASH bo qua -Simulate / -OpenPaymentUrl"
}

# --- Login ---
Write-Step "Dang nhap Staff ($StaffEmail)"
$staff = Login-User -Email $StaffEmail -Password $StaffPassword
Write-Ok "Staff: $($staff.FullName) | userId=$($staff.UserId)"

Write-Step "Dang nhap Driver ($DriverEmail)"
$driver = Login-User -Email $DriverEmail -Password $DriverPassword
Write-Ok "Driver: $($driver.FullName) | userId=$($driver.UserId)"

# --- Resolve session ---
Write-Step "Lay thong tin session ACTIVE"
$session = $null
try {
    $session = Get-CurrentSession -DriverToken $driver.Token
    Write-Ok "Session hien tai: sessionId=$($session.sessionId) | ticket=$($session.ticketCode) | paymentStatus=$($session.paymentStatus)"
}
catch {
    if (-not $TicketCode) {
        throw "Khong co session ACTIVE. Can check-in truoc, hoac truyen -TicketCode."
    }
    Write-Warn "Driver khong co session ACTIVE, se dung -TicketCode"
}

if (-not $TicketCode -and $session) {
    $TicketCode = $session.ticketCode
}

if (-not $TicketCode) {
    throw "Thieu ticketCode."
}

Write-Step "Uoc tinh phi (ticketCode=$TicketCode)"
$estimate = Get-Estimate -Token $staff.Token -Code $TicketCode
$sessionId = $estimate.sessionId
$fee = [decimal]$estimate.totalFee

if ($Amount -le 0) {
    $Amount = $fee
}

Write-Ok "sessionId=$sessionId | totalFee=$fee | dung amount=$Amount"
Write-Host "    $($estimate.feeExplanation)"

if ($Mode -eq "CASH") {
    Write-Step "Checkout CASH"
    $checkout = Invoke-Checkout -StaffToken $staff.Token -Code $TicketCode -PaymentMethod "CASH"
    Write-Ok "Checkout thanh cong!"
    Write-Host ($checkout | ConvertTo-Json -Depth 6)
    exit 0
}

# --- Electronic payment (PAYOS / VNPAY) ---
if (-not $Simulate -and -not $OpenPaymentUrl) {
    Write-Warn "Khong co -Simulate hay -OpenPaymentUrl -> tu dong bat -Simulate (dev mode)"
    $Simulate = $true
}

Write-Step "Initiate payment ($Mode)"
$initBody = @{
    sessionId     = $sessionId
    paymentMethod = $Mode
    amount        = $Amount
    driverId      = $driver.UserId
    note          = "Test $Mode script"
}
if ($Mode -eq "VNPAY") {
    $initBody.language = "vn"
}

$initResp = Invoke-Api -Method POST -Path "/api/payments/initiate" -Token $staff.Token -Body $initBody
$payment = $initResp.data
$paymentId = $payment.paymentId
$paymentUrl = $payment.paymentUrl

Write-Ok "paymentId=$paymentId | status=$($payment.paymentStatus)"
if ($paymentUrl) {
    Write-Host "    paymentUrl: $paymentUrl"
}
else {
    Write-Warn "Khong co paymentUrl (kiem tra cau hinh PayOS/VNPay trong application.properties)"
}

if ($OpenPaymentUrl) {
    if (-not $paymentUrl) {
        throw "Khong co paymentUrl de mo trinh duyet"
    }
    Write-Step "Mo link thanh toan tren trinh duyet..."
    Start-Process $paymentUrl
    Write-Warn "Thanh toan xong tren trinh duyet, script se poll trang thai payment..."
    $payment = Wait-PaymentSuccess -Token $staff.Token -PaymentId $paymentId
}
elseif ($Simulate) {
    Write-Step "Simulate gateway callback (confirm-success)"
    $fakeTxn = "TEST-TXN-$(Get-Date -Format 'yyyyMMddHHmmss')"
    $confirmResp = Invoke-Api -Method POST -Path "/api/payments/confirm-success" -Token $staff.Token -Query @{
        paymentId       = $paymentId
        transactionCode = $fakeTxn
    }
    $payment = $confirmResp.data
    Write-Ok "Payment confirmed: status=$($payment.paymentStatus) | txn=$fakeTxn"
}
else {
    $payment = Wait-PaymentSuccess -Token $staff.Token -PaymentId $paymentId
}

Write-Step "Staff confirm payment"
$staffConfirm = Invoke-Api -Method POST -Path "/api/payments/confirm-by-staff" -Token $staff.Token -Body @{
    paymentId   = $paymentId
    sessionId   = $sessionId
    driverId    = $driver.UserId
    staffId     = $staff.UserId
    isConfirmed = $true
}
Write-Ok "confirmationStatus=$($staffConfirm.data.confirmationStatus)"

Write-Step "Checkout ($Mode)"
$checkout = Invoke-Checkout -StaffToken $staff.Token -Code $TicketCode -PaymentMethod $Mode
Write-Ok "Checkout thanh cong!"

Write-Host ""
Write-Host "===== KET QUA =====" -ForegroundColor Magenta
Write-Host "PaymentId   : $paymentId"
Write-Host "SessionId   : $sessionId"
Write-Host "TicketCode  : $TicketCode"
Write-Host "Amount      : $Amount"
Write-Host "TotalFee    : $($checkout.totalFee)"
Write-Host "CheckoutTime: $($checkout.checkoutTime)"
Write-Host ""
Write-Host "Chi tiet checkout:" -ForegroundColor DarkGray
Write-Host ($checkout | ConvertTo-Json -Depth 6)
