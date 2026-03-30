cd e:\pingOwl
$email = "online$(Get-Random -Maximum 10000)@test.com"
Write-Host "Testing with email: $email" -ForegroundColor Cyan

# Sign up first
$socket = New-Object System.Net.Sockets.TcpClient("localhost", 4444)
$writer = New-Object System.IO.StreamWriter($socket.GetStream())
$reader = New-Object System.IO.StreamReader($socket.GetStream())
$writer.AutoFlush = $true

Write-Host "1. Signing up..." -ForegroundColor Yellow
$writer.WriteLine("SIGNUP|$email|Online|Test|Pass123|Pet|Dog|1990-01-01")
$resp = $reader.ReadLine()
Write-Host "   Response: $resp" -ForegroundColor Cyan
$socket.Close()

Start-Sleep 1

# Now login and keep connection alive
Write-Host "2. Logging in..." -ForegroundColor Yellow
$socket = New-Object System.Net.Sockets.TcpClient("localhost", 4444)
$writer = New-Object System.IO.StreamWriter($socket.GetStream())
$reader = New-Object System.IO.StreamReader($socket.GetStream())
$writer.AutoFlush = $true

$writer.WriteLine("LOGIN|$email|Pass123")
$resp = $reader.ReadLine()
Write-Host "   Response: $resp" -ForegroundColor Green

if ($resp.StartsWith("LOGIN_SUCCESS")) {
   Write-Host "3. Sending KEEP_ALIVE..." -ForegroundColor Yellow
   $writer.WriteLine("KEEP_ALIVE|$email")
   $resp = $reader.ReadLine()
   Write-Host "   Response: $resp" -ForegroundColor Magenta
   
   Write-Host "4. User stays ONLINE for 10 seconds..." -ForegroundColor Cyan
   Write-Host "   (Check server console for 'User online' message)" -ForegroundColor Cyan
   Start-Sleep 10
   
   Write-Host "5. Closing connection..." -ForegroundColor Yellow
   Write-Host "   (Check server console for 'User offline' message)" -ForegroundColor Yellow
}
$socket.Close()

Write-Host "Test complete!" -ForegroundColor Green
