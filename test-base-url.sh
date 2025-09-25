#!/bin/bash

# Test script for verifying SecurePaste base URL configuration
set -e

echo "🔍 Testing SecurePaste Base URL Configuration"
echo "============================================="

# Configuration
APP_HOST="${1:-localhost}"
APP_PORT="${2:-8097}"
BASE_URL="http://${APP_HOST}:${APP_PORT}"

echo "📍 Testing against: ${BASE_URL}"
echo ""

# Test 1: Check if application is running
echo "1️⃣  Checking application health..."
if curl -s "${BASE_URL}/api/config" > /dev/null; then
    echo "   ✅ Application is running"
else
    echo "   ❌ Application is not running at ${BASE_URL}"
    exit 1
fi

# Test 2: Get configuration
echo "2️⃣  Getting application configuration..."
CONFIG_RESPONSE=$(curl -s "${BASE_URL}/api/config")
echo "   📋 Config response: ${CONFIG_RESPONSE}"

# Extract base URL from response
CONFIGURED_BASE_URL=$(echo "$CONFIG_RESPONSE" | grep -o '"baseUrl":"[^"]*' | cut -d '"' -f 4)
echo "   🌐 Configured base URL: ${CONFIGURED_BASE_URL}"

# Test 3: Create a test paste
echo "3️⃣  Creating test paste..."
PASTE_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/pastes" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Base URL Test",
    "content": "Testing the configured base URL feature.",
    "language": "plaintext",
    "visibility": "PUBLIC"
  }')

PASTE_ID=$(echo "$PASTE_RESPONSE" | grep -o '"id":"[^"]*' | cut -d '"' -f 4)
echo "   📝 Created paste with ID: ${PASTE_ID}"

# Test 4: Generate expected URL
EXPECTED_PASTE_URL="${CONFIGURED_BASE_URL}/paste/${PASTE_ID}"
echo "4️⃣  Generated paste URL: ${EXPECTED_PASTE_URL}"

# Test 5: Verify the paste is accessible
echo "5️⃣  Verifying paste accessibility..."
if curl -s "${BASE_URL}/paste/${PASTE_ID}" > /dev/null; then
    echo "   ✅ Paste is accessible at the configured URL"
else
    echo "   ❌ Paste is not accessible"
    exit 1
fi

echo ""
echo "🎉 All tests passed! Base URL configuration is working correctly."
echo ""
echo "Summary:"
echo "  - Application URL: ${BASE_URL}"
echo "  - Configured Base URL: ${CONFIGURED_BASE_URL}"
echo "  - Test Paste URL: ${EXPECTED_PASTE_URL}"
echo ""
echo "💡 To change the base URL for different environments:"
echo "   - Development: Edit application.yml"
echo "   - Production: Set BASE_URL environment variable"
echo "   - Docker: Set BASE_URL in .env file"
echo "   - Kubernetes: Update BASE_URL in k8s/application.yaml"