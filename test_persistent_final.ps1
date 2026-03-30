cd e:\pingOwl
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Persistent Connection Test" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Cyan

# Create new account
$rand = Get-Random -Maximum 100000
$email = "persistent$rand@test.com"

Write-Host "`nStep 1: Creating account: $email" -ForegroundColor Yellow
$socket = New-Object System.Net.Sockets.TcpClient("localhost", 4444)
$writer = New-Object System.IO.StreamWriter($socket.GetStream())
$reader = New-Object System.IO.StreamReader($socket.GetStream())
$writer.AutoFlush = $true

$cmd = "SIGNUP|$email|Persist|Test|Pass123|Pet|Bird|1990-01-01"
$writer.WriteLine($cmd)
$resp = $reader.ReadLine()
Write-Host "  Signup: $resp" -ForegroundColor Cyan
$socket.Close()
Start-Sleep 1

Write-Host "`nStep 2: Login and keep online for 15 seconds" -ForegroundColor Yellow
$socket = New-Object System.Net.Sockets.TcpClient("localhost", 4444)
$writer = New-Object System.IO.StreamWriter($socket.GetStream())
$reader = New-Object System.IO.StreamReader($socket.GetStream())
$writer.AutoFlush = $true

$writer.WriteLine("LOGIN|$email|Pass123")
$resp = $reader.ReadLine()
Write-Host "  Login: $resp" -ForegroundColor Green

if ($resp.Contains("LOGIN_SUCCESS")) {
  Write-Host "`nStep 3: Establishing persistent connection (KEEP_ALIVE)" -ForegroundColor Yellow
  $writer.WriteLine("KEEP_ALIVE|$email")
  $resp = $reader.ReadLine()
  Write-Host "  Response: $resp" -ForegroundColor Magenta
  
  Write-Host "`nStep 4: User stays ONLINE!" -ForegroundColor Cyan
  Write-Host "  Check server console - should show 'User online: $email (Total: 1)'" -ForegroundColor Cyan
  Write-Host "  Sleeping for 15 seconds..." -ForegroundColor Cyan
  
  for ($i = 15; $i -gt 0; $i--) {
    Write-Host "  $i seconds remaining..." -NoNewline
    Start-Sleep 1
    Write-Host "`r" -NoNewline
  }
  
  Write-Host "`nStep 5: Closing connection - user goes OFFLINE" -ForegroundColor Yellow
  Write-Host "  Check server console - should show 'User offline: $email (Total: 0)'" -ForegroundColor Yellow
}

$socket.Close()

Write-Host "`n=====================================" -ForegroundColor Cyan
Write-Host "Test Complete! Check server console for online/offline messages" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Cyan
