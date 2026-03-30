#!/bin/bash

# PingOwl Messaging System - Automated Test Script
# This script tests the complete messaging system end-to-end

echo "=========================================="
echo "  PingOwl Messaging System - Test Suite  "
echo "=========================================="
echo ""

# Configuration
SERVER_HOST="localhost"
SERVER_PORT="4444"
USER1_EMAIL="test_alice@example.com"
USER1_PASS="Alice@123"
USER1_NAME="Alice"
USER2_EMAIL="test_bob@example.com"
USER2_PASS="Bob@123"
USER2_NAME="Bob"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to test connection
test_connection() {
    echo -e "${YELLOW}Testing server connection...${NC}"
    if timeout 2 bash -c "echo > /dev/tcp/$SERVER_HOST/$SERVER_PORT" 2>/dev/null; then
        echo -e "${GREEN}✓ Server is reachable on $SERVER_HOST:$SERVER_PORT${NC}"
        return 0
    else
        echo -e "${RED}✗ Cannot connect to server at $SERVER_HOST:$SERVER_PORT${NC}"
        echo "  Make sure server is running: java -cp target/classes com.codes.server.Server"
        return 1
    fi
}

# Function to send command to server
send_command() {
    local command=$1
    echo -e "${YELLOW}Sending: $command${NC}"
    echo "$command" | timeout 5 nc -q 1 $SERVER_HOST $SERVER_PORT 2>/dev/null
}

# Function to test signup
test_signup() {
    echo ""
    echo -e "${YELLOW}=== Test 1: User Registration ===${NC}"
    
    # User 1
    echo "Creating User 1 ($USER1_NAME)..."
    response=$(send_command "SIGNUP|$USER1_EMAIL|$USER1_NAME|Smith|$USER1_PASS|favorite_color|blue|1990-05-15")
    if [[ $response == *"SUCCESS"* ]]; then
        echo -e "${GREEN}✓ User 1 signup successful${NC}"
    else
        echo -e "${RED}✗ User 1 signup failed${NC}"
    fi
    
    # User 2
    echo "Creating User 2 ($USER2_NAME)..."
    response=$(send_command "SIGNUP|$USER2_EMAIL|$USER2_NAME|Jones|$USER2_PASS|favorite_color|red|1992-03-20")
    if [[ $response == *"SUCCESS"* ]]; then
        echo -e "${GREEN}✓ User 2 signup successful${NC}"
    else
        echo -e "${RED}✗ User 2 signup failed${NC}"
    fi
}

# Function to test login
test_login() {
    echo ""
    echo -e "${YELLOW}=== Test 2: User Login ===${NC}"
    
    echo "Logging in User 1..."
    response=$(send_command "LOGIN|$USER1_EMAIL|$USER1_PASS")
    if [[ $response == *"SUCCESS"* ]]; then
        echo -e "${GREEN}✓ User 1 login successful${NC}"
    else
        echo -e "${RED}✗ User 1 login failed${NC}"
    fi
    
    echo "Logging in User 2..."
    response=$(send_command "LOGIN|$USER2_EMAIL|$USER2_PASS")
    if [[ $response == *"SUCCESS"* ]]; then
        echo -e "${GREEN}✓ User 2 login successful${NC}"
    else
        echo -e "${RED}✗ User 2 login failed${NC}"
    fi
}

# Function to test friend requests
test_friend_requests() {
    echo ""
    echo -e "${YELLOW}=== Test 3: Friend Requests ===${NC}"
    
    echo "User 1 sending friend request to User 2..."
    response=$(send_command "SEND_REQUEST|$USER1_EMAIL|$USER2_EMAIL")
    if [[ $response == *"SENT"* ]]; then
        echo -e "${GREEN}✓ Friend request sent${NC}"
    else
        echo -e "${RED}✗ Friend request failed${NC}"
    fi
    
    sleep 1
    
    echo "User 2 accepting friend request..."
    response=$(send_command "ACCEPT_REQUEST|$USER2_EMAIL|$USER1_EMAIL")
    if [[ $response == *"SUCCESS"* ]]; then
        echo -e "${GREEN}✓ Friend request accepted${NC}"
    else
        echo -e "${RED}✗ Friend request acceptance failed${NC}"
    fi
}

# Function to test get friends
test_get_friends() {
    echo ""
    echo -e "${YELLOW}=== Test 4: Get Friends List ===${NC}"
    
    echo "User 1 getting friends list..."
    response=$(send_command "GET_FRIENDS|$USER1_EMAIL")
    if [[ $response == *"$USER2_EMAIL"* ]]; then
        echo -e "${GREEN}✓ Friends list retrieved${NC}"
        echo "  Friends: $response"
    else
        echo -e "${RED}✗ Friends list failed or empty${NC}"
    fi
}

# Function to test messaging
test_messaging() {
    echo ""
    echo -e "${YELLOW}=== Test 5: Send and Receive Messages ===${NC}"
    
    echo "User 1 sending message to User 2..."
    response=$(send_command "SEND_MESSAGE|$USER1_EMAIL|$USER2_EMAIL|Hello Bob! This is a test message.")
    if [[ $response == *"SENT"* ]]; then
        echo -e "${GREEN}✓ Message sent${NC}"
    else
        echo -e "${RED}✗ Message send failed${NC}"
        echo "  Response: $response"
    fi
    
    sleep 1
    
    echo "User 1 sending another message..."
    response=$(send_command "SEND_MESSAGE|$USER1_EMAIL|$USER2_EMAIL|How are you doing?")
    if [[ $response == *"SENT"* ]]; then
        echo -e "${GREEN}✓ Second message sent${NC}"
    else
        echo -e "${RED}✗ Second message failed${NC}"
    fi
    
    sleep 1
    
    echo "User 2 replying..."
    response=$(send_command "SEND_MESSAGE|$USER2_EMAIL|$USER1_EMAIL|Hi Alice! I'm doing great, thanks for asking!")
    if [[ $response == *"SENT"* ]]; then
        echo -e "${GREEN}✓ Reply message sent${NC}"
    else
        echo -e "${RED}✗ Reply message failed${NC}"
    fi
}

# Function to test get messages
test_get_messages() {
    echo ""
    echo -e "${YELLOW}=== Test 6: Retrieve Messages ===${NC}"
    
    echo "User 1 retrieving conversation..."
    response=$(send_command "GET_MESSAGES|$USER1_EMAIL|$USER2_EMAIL")
    
    if [[ -z "$response" ]]; then
        echo -e "${RED}✗ No messages retrieved${NC}"
    else
        echo -e "${GREEN}✓ Messages retrieved:${NC}"
        echo "$response" | while IFS='|' read -r sender message timestamp type; do
            echo "  [$timestamp] $sender: $message"
        done
    fi
}

# Main test execution
main() {
    # Check if nc is available
    if ! command -v nc &> /dev/null; then
        echo -e "${RED}Error: netcat (nc) is not installed${NC}"
        echo "Install with: sudo apt-get install netcat-openbsd"
        exit 1
    fi
    
    # Test connection first
    if ! test_connection; then
        exit 1
    fi
    
    # Run all tests
    test_signup
    sleep 1
    test_login
    sleep 1
    test_friend_requests
    sleep 1
    test_get_friends
    sleep 1
    test_messaging
    sleep 1
    test_get_messages
    
    echo ""
    echo "=========================================="
    echo "  Tests Complete!"
    echo "=========================================="
}

# Run main function
main
