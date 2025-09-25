#!/bin/bash

# Test script for verifying SecurePaste database configuration
set -e

echo "🗄️  Testing SecurePaste Database Configuration"
echo "=============================================="

# Configuration
ENVIRONMENT="${1:-development}"
APP_HOST="${2:-localhost}"
APP_PORT="${3:-8097}"
BASE_URL="http://${APP_HOST}:${APP_PORT}"

echo "📍 Environment: ${ENVIRONMENT}"
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

# Test 2: Create a test paste to verify database connectivity
echo "2️⃣  Testing database connectivity by creating a paste..."
PASTE_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/pastes" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Database Test - '"${ENVIRONMENT}"'",
    "content": "Testing database connectivity in '"${ENVIRONMENT}"' environment.\nThis paste should be persisted in PostgreSQL.",
    "language": "plaintext",
    "visibility": "PUBLIC"
  }')

if echo "$PASTE_RESPONSE" | grep -q '"id"'; then
    PASTE_ID=$(echo "$PASTE_RESPONSE" | grep -o '"id":"[^"]*' | cut -d '"' -f 4)
    echo "   ✅ Database write successful - Created paste ID: ${PASTE_ID}"
else
    echo "   ❌ Database write failed"
    echo "   Response: ${PASTE_RESPONSE}"
    exit 1
fi

# Test 3: Retrieve the paste to verify database read
echo "3️⃣  Testing database read by retrieving the paste..."
RETRIEVE_RESPONSE=$(curl -s "${BASE_URL}/api/pastes/${PASTE_ID}")

if echo "$RETRIEVE_RESPONSE" | grep -q "Database Test - ${ENVIRONMENT}"; then
    echo "   ✅ Database read successful"
else
    echo "   ❌ Database read failed"
    echo "   Response: ${RETRIEVE_RESPONSE}"
    exit 1
fi

# Test 4: Get recent pastes to verify database queries
echo "4️⃣  Testing database queries by fetching recent pastes..."
RECENT_RESPONSE=$(curl -s "${BASE_URL}/api/pastes/public?size=5")

if echo "$RECENT_RESPONSE" | grep -q '"content"'; then
    PASTE_COUNT=$(echo "$RECENT_RESPONSE" | grep -o '"numberOfElements":[0-9]*' | cut -d ':' -f 2)
    echo "   ✅ Database query successful - Found ${PASTE_COUNT} pastes"
else
    echo "   ❌ Database query failed"
    echo "   Response: ${RECENT_RESPONSE}"
    exit 1
fi

# Test 5: Update the paste to verify database update
echo "5️⃣  Testing database update..."
UPDATE_RESPONSE=$(curl -s -X PUT "${BASE_URL}/api/pastes/${PASTE_ID}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Database Test - '"${ENVIRONMENT}"' (Updated)",
    "content": "Updated content to verify database update functionality."
  }')

if echo "$UPDATE_RESPONSE" | grep -q "Updated"; then
    echo "   ✅ Database update successful"
else
    echo "   ❌ Database update failed"
    echo "   Response: ${UPDATE_RESPONSE}"
    exit 1
fi

# Test 6: Clean up by deleting the test paste
echo "6️⃣  Cleaning up test data..."
DELETE_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/api/pastes/${PASTE_ID}" -w "%{http_code}")

if [[ "$DELETE_RESPONSE" == "204" ]]; then
    echo "   ✅ Database delete successful"
else
    echo "   ⚠️  Database delete returned: ${DELETE_RESPONSE}"
fi

echo ""
echo "🎉 All database tests passed!"
echo ""
echo "Database Configuration Summary for ${ENVIRONMENT}:"
case $ENVIRONMENT in
    "development")
        echo "  - Database: H2 (in-memory)"
        echo "  - Profile: default"
        echo "  - Configuration: application.yml default section"
        ;;
    "production")
        echo "  - Database: PostgreSQL"
        echo "  - Profile: production"
        echo "  - Configuration: Environment variables or application.yml production section"
        echo "  - Host: \${DB_HOST:-localhost}"
        echo "  - Port: \${DB_PORT:-5432}"
        echo "  - Database: \${DB_NAME:-pastebin}"
        ;;
    "docker")
        echo "  - Database: PostgreSQL (in Docker container)"
        echo "  - Profile: docker"
        echo "  - Configuration: Environment variables from docker-compose.yml"
        echo "  - Host: \${DB_HOST:-postgres}"
        echo "  - Port: \${DB_PORT:-5432}"
        echo "  - Database: \${DB_NAME:-pastebin}"
        ;;
    "kubernetes")
        echo "  - Database: PostgreSQL (Kubernetes service)"
        echo "  - Profile: kubernetes"
        echo "  - Configuration: Environment variables from ConfigMap and Secret"
        echo "  - Host: From database-config ConfigMap"
        echo "  - Port: From database-config ConfigMap"
        echo "  - Database: From database-config ConfigMap"
        echo "  - Credentials: From postgres-secret Secret"
        ;;
esac
echo ""
echo "💡 Environment Variables Used:"
echo "   DB_URL, DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD"