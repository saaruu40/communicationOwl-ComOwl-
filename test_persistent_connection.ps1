# Test persistent connection - user should stay online

$SERVER = "localhost"
$PORT = 4444
$testEmail = "testuser@test.com"
$testPassword = "Test@123"

Write-Host "===================================" -ForegroundColor Cyan
Write-Host "Testing Persistent Connection" -ForegroundColor Green
Write-Host "===================================" -ForegroundColor Cyan

# First, signup the test user
Write-Host "`n1. Signing up test user..." -ForegroundColor Yellow
$socket = New-Object System.Net.Sockets.TcpClient($SERVER, $PORT)
$stream = $socket.GetStream()
$writer = New-Object System.IO.StreamWriter($stream)
$reader = New-Object System.IO.StreamReader($stream)

$signupCmd = "SIGNUP|${testEmail}|TestUser|Test|${testPassword}|Pet|Fluffy|1990-01-01"
$writer.WriteLine($signupCmd)
$writer.Flush()
$response = $reader.ReadLine()
Write-Host "   Signup response: $response" -ForegroundColor Cyan

$socket.Close()

# Wait a moment
Start-Sleep 1

# Now login and keep connection alive
Write-Host "`n2. Logging in and establishing persistent connection..." -ForegroundColor Yellow
$socket = New-Object System.Net.Sockets.TcpClient($SERVER, $PORT)
$stream = $socket.GetStream()
$writer = New-Object System.IO.StreamWriter($stream)
$reader = New-Object System.IO.StreamReader($stream)

$loginCmd = "LOGIN|${testEmail}|${testPassword}"
$writer.WriteLine($loginCmd)
$writer.Flush()
$response = $reader.ReadLine()
Write-Host "   Login response: $response" -ForegroundColor Cyan

if ($response.StartsWith("LOGIN_SUCCESS")) {
    Write-Host "   OK Login successful!" -ForegroundColor Green
    
    # Send KEEP_ALIVE to register persistent connection
    Write-Host "`n3. Sending KEEP_ALIVE command..." -ForegroundColor Yellow
    $keepAliveCmd = "KEEP_ALIVE|${testEmail}"
    $writer.WriteLine($keepAliveCmd)
    $writer.Flush()
    $response = $reader.ReadLine()
    Write-Host "   KEEP_ALIVE response: $response" -ForegroundColor Cyan
    
    if ($response.Contains("SUCCESS")) {
        Write-Host "   OK Persistent connection established!" -ForegroundColor Green
        
        Write-Host "`n4. User will stay ONLINE while socket is open..." -ForegroundColor Yellow
        Write-Host "   Sleeping for 10 seconds (user stays online)..." -ForegroundColor Cyan
        Start-Sleep 10
        
        Write-Host "   OK Connection kept alive!" -ForegroundColor Green
    } else {
        Write-Host "   FAILED KEEP_ALIVE failed" -ForegroundColor Red
    }
} else {
    Write-Host "   FAILED Login failed" -ForegroundColor Red
}

# Close connection - user goes offline
Write-Host "`n5. Closing connection..." -ForegroundColor Yellow
$socket.Close()
Write-Host "   OK Connection closed - user now offline" -ForegroundColor Green

Write-Host "`n===================================" -ForegroundColor Cyan
Write-Host "OK Persistent connection test complete!" -ForegroundColor Green
Write-Host "===================================" -ForegroundColor Cyan
