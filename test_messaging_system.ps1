#!/usr/bin/env pwsh

# PingOwl Messaging System - Automated Test Script (PowerShell)
# This script tests the complete messaging system end-to-end

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  PingOwl Messaging System - Test Suite  " -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Configuration
$SERVER_HOST = "localhost"
$SERVER_PORT = 4444
$USER1_EMAIL = "test_alice@example.com"
$USER1_PASS = "Alice@123"
$USER1_NAME = "Alice"
$USER2_EMAIL = "test_bob@example.com"
$USER2_PASS = "Bob@123"
$USER2_NAME = "Bob"

# Function to test connection
function Test-Connection {
    Write-Host "Testing server connection..." -ForegroundColor Yellow
    try {
        $socket = New-Object System.Net.Sockets.TcpClient
        $socket.Connect($SERVER_HOST, $SERVER_PORT)
        $socket.Close()
        Write-Host "✓ Server is reachable on $SERVER_HOST`:$SERVER_PORT" -ForegroundColor Green
        return $true
    } catch {
        Write-Host "✗ Cannot connect to server at $SERVER_HOST`:$SERVER_PORT" -ForegroundColor Red
        Write-Host "  Make sure server is running: java -cp target/classes com.codes.server.Server" -ForegroundColor DarkRed
        return $false
    }
}

# Function to send command to server
function Send-Command {
    param([string]$command)
    
    Write-Host "Sending: $command" -ForegroundColor Yellow
    
    try {
        $socket = New-Object System.Net.Sockets.TcpClient($SERVER_HOST, $SERVER_PORT)
        $stream = $socket.GetStream()
        $writer = New-Object System.IO.StreamWriter($stream)
        $reader = New-Object System.IO.StreamReader($stream)
        
        # Send command
        $writer.WriteLine($command)
        $writer.Flush()
        
        # Read response
        $response = $reader.ReadLine()
        
        # Cleanup
        $reader.Close()
        $writer.Close()
        $stream.Close()
        $socket.Close()
        
        return $response
    } catch {
        Write-Host "Error sending command: $_" -ForegroundColor Red
        return $null
    }
}

# Function to test signup
function Test-Signup {
    Write-Host ""
    Write-Host "=== Test 1: User Registration ===" -ForegroundColor Yellow
    
    # User 1
    Write-Host "Creating User 1 ($USER1_NAME)..."
    $response = Send-Command "SIGNUP|$USER1_EMAIL|$USER1_NAME|Smith|$USER1_PASS|favorite_color|blue|1990-05-15"
    if ($response -match "SUCCESS") {
        Write-Host "✓ User 1 signup successful" -ForegroundColor Green
    } else {
        Write-Host "✗ User 1 signup failed" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 500
    
    # User 2
    Write-Host "Creating User 2 ($USER2_NAME)..."
    $response = Send-Command "SIGNUP|$USER2_EMAIL|$USER2_NAME|Jones|$USER2_PASS|favorite_color|red|1992-03-20"
    if ($response -match "SUCCESS") {
        Write-Host "✓ User 2 signup successful" -ForegroundColor Green
    } else {
        Write-Host "✗ User 2 signup failed" -ForegroundColor Red
    }
}

# Function to test login
function Test-Login {
    Write-Host ""
    Write-Host "=== Test 2: User Login ===" -ForegroundColor Yellow
    
    Write-Host "Logging in User 1..."
    $response = Send-Command "LOGIN|$USER1_EMAIL|$USER1_PASS"
    if ($response -match "SUCCESS") {
        Write-Host "✓ User 1 login successful" -ForegroundColor Green
    } else {
        Write-Host "✗ User 1 login failed" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 500
    
    Write-Host "Logging in User 2..."
    $response = Send-Command "LOGIN|$USER2_EMAIL|$USER2_PASS"
    if ($response -match "SUCCESS") {
        Write-Host "✓ User 2 login successful" -ForegroundColor Green
    } else {
        Write-Host "✗ User 2 login failed" -ForegroundColor Red
    }
}

# Function to test friend requests
function Test-FriendRequests {
    Write-Host ""
    Write-Host "=== Test 3: Friend Requests ===" -ForegroundColor Yellow
    
    Write-Host "User 1 sending friend request to User 2..."
    $response = Send-Command "SEND_REQUEST|$USER1_EMAIL|$USER2_EMAIL"
    if ($response -match "SENT") {
        Write-Host "✓ Friend request sent" -ForegroundColor Green
    } else {
        Write-Host "✗ Friend request failed" -ForegroundColor Red
    }
    
    Start-Sleep -Seconds 1
    
    Write-Host "User 2 accepting friend request..."
    $response = Send-Command "ACCEPT_REQUEST|$USER2_EMAIL|$USER1_EMAIL"
    if ($response -match "SUCCESS") {
        Write-Host "✓ Friend request accepted" -ForegroundColor Green
    } else {
        Write-Host "✗ Friend request acceptance failed" -ForegroundColor Red
    }
}

# Function to test get friends
function Test-GetFriends {
    Write-Host ""
    Write-Host "=== Test 4: Get Friends List ===" -ForegroundColor Yellow
    
    Write-Host "User 1 getting friends list..."
    $response = Send-Command "GET_FRIENDS|$USER1_EMAIL"
    if ($response -match $USER2_EMAIL) {
        Write-Host "✓ Friends list retrieved" -ForegroundColor Green
        Write-Host "  Friends: $response" -ForegroundColor Cyan
    } else {
        Write-Host "✗ Friends list failed or empty" -ForegroundColor Red
    }
}

# Function to test messaging
function Test-Messaging {
    Write-Host ""
    Write-Host "=== Test 5: Send and Receive Messages ===" -ForegroundColor Yellow
    
    Write-Host "User 1 sending message to User 2..."
    $response = Send-Command "SEND_MESSAGE|$USER1_EMAIL|$USER2_EMAIL|Hello Bob! This is a test message."
    if ($response -match "SENT") {
        Write-Host "✓ Message sent" -ForegroundColor Green
    } else {
        Write-Host "✗ Message send failed" -ForegroundColor Red
        Write-Host "  Response: $response" -ForegroundColor DarkRed
    }
    
    Start-Sleep -Milliseconds 500
    
    Write-Host "User 1 sending another message..."
    $response = Send-Command "SEND_MESSAGE|$USER1_EMAIL|$USER2_EMAIL|How are you doing?"
    if ($response -match "SENT") {
        Write-Host "✓ Second message sent" -ForegroundColor Green
    } else {
        Write-Host "✗ Second message failed" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 500
    
    Write-Host "User 2 replying..."
    $response = Send-Command "SEND_MESSAGE|$USER2_EMAIL|$USER1_EMAIL|Hi Alice! I'm doing great, thanks for asking!"
    if ($response -match "SENT") {
        Write-Host "✓ Reply message sent" -ForegroundColor Green
    } else {
        Write-Host "✗ Reply message failed" -ForegroundColor Red
    }
}

# Function to test get messages
function Test-GetMessages {
    Write-Host ""
    Write-Host "=== Test 6: Retrieve Messages ===" -ForegroundColor Yellow
    
    Write-Host "User 1 retrieving conversation..."
    $response = Send-Command "GET_MESSAGES|$USER1_EMAIL|$USER2_EMAIL"
    
    if ([string]::IsNullOrEmpty($response)) {
        Write-Host "✗ No messages retrieved" -ForegroundColor Red
    } else {
        Write-Host "✓ Messages retrieved:" -ForegroundColor Green
        $messages = $response -split "`n"
        foreach ($msg in $messages) {
            if (-not [string]::IsNullOrEmpty($msg)) {
                $parts = $msg -split "\|"
                if ($parts.Length -ge 3) {
                    Write-Host "  [$($parts[2])] $($parts[0]): $($parts[1])" -ForegroundColor Cyan
                }
            }
        }
    }
}

# Main execution
function Main {
    # Test connection first
    if (-not (Test-Connection)) {
        exit 1
    }
    
    # Run all tests
    Test-Signup
    Start-Sleep -Milliseconds 500
    Test-Login
    Start-Sleep -Milliseconds 500
    Test-FriendRequests
    Start-Sleep -Milliseconds 500
    Test-GetFriends
    Start-Sleep -Milliseconds 500
    Test-Messaging
    Start-Sleep -Milliseconds 500
    Test-GetMessages
    
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "  Tests Complete!" -ForegroundColor Green
    Write-Host "==========================================" -ForegroundColor Cyan
}

# Run main function
Main
