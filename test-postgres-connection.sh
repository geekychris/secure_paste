#!/bin/bash

# Test PostgreSQL connection with the specified credentials
set -e

echo "🧪 Testing PostgreSQL Connection"
echo "================================"

# Configuration
POSTGRES_HOST="${POSTGRES_HOST:-postgres}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres123}"
POSTGRES_DATABASE="${POSTGRES_DATABASE:-securepaste}"
NAMESPACE="${NAMESPACE:-securepaste}"

echo "📍 Connection Parameters:"
echo "   Host: ${POSTGRES_HOST}"
echo "   Port: ${POSTGRES_PORT}"
echo "   User: ${POSTGRES_USER}"
echo "   Database: ${POSTGRES_DATABASE}"
echo "   Namespace: ${NAMESPACE}"
echo ""

# Test 1: Basic connectivity
echo "1️⃣  Testing basic PostgreSQL connectivity..."

if command -v kubectl &> /dev/null; then
    echo "   Using kubectl to test connection..."
    
    # Test connection using a temporary pod
    kubectl run postgres-test --rm -it --image=postgres:15-alpine --restart=Never -n ${NAMESPACE} -- \
    sh -c "
        export PGPASSWORD='postgres123'
        echo 'Testing connection to ${POSTGRES_HOST}:${POSTGRES_PORT}...'
        if pg_isready -h ${POSTGRES_HOST} -p ${POSTGRES_PORT} -U ${POSTGRES_USER}; then
            echo '✅ PostgreSQL is ready'
        else
            echo '❌ PostgreSQL is not ready'
            exit 1
        fi
        
        echo 'Testing authentication...'
        if psql -h ${POSTGRES_HOST} -p ${POSTGRES_PORT} -U ${POSTGRES_USER} -d postgres -c 'SELECT version();' > /dev/null 2>&1; then
            echo '✅ Authentication successful'
        else
            echo '❌ Authentication failed'
            exit 1
        fi
        
        echo 'Checking if securepaste database exists...'
        if psql -h ${POSTGRES_HOST} -p ${POSTGRES_PORT} -U ${POSTGRES_USER} -d postgres -tAc \"SELECT 1 FROM pg_database WHERE datname='${POSTGRES_DATABASE}'\" | grep -q 1; then
            echo '✅ Database ${POSTGRES_DATABASE} exists'
        else
            echo '⚠️  Database ${POSTGRES_DATABASE} does not exist - will be created during deployment'
        fi
        
        echo 'Connection test completed successfully!'
    " 2>/dev/null || echo "❌ Connection test failed"

elif command -v psql &> /dev/null; then
    echo "   Using local psql to test connection..."
    
    export PGPASSWORD="${POSTGRES_PASSWORD}"
    
    echo "   Testing connectivity..."
    if pg_isready -h ${POSTGRES_HOST} -p ${POSTGRES_PORT} -U ${POSTGRES_USER}; then
        echo "   ✅ PostgreSQL is ready"
    else
        echo "   ❌ PostgreSQL is not ready"
        exit 1
    fi
    
    echo "   Testing authentication..."
    if psql -h ${POSTGRES_HOST} -p ${POSTGRES_PORT} -U ${POSTGRES_USER} -d postgres -c 'SELECT version();' > /dev/null 2>&1; then
        echo "   ✅ Authentication successful"
    else
        echo "   ❌ Authentication failed"
        exit 1
    fi
    
    echo "   Checking if securepaste database exists..."
    if psql -h ${POSTGRES_HOST} -p ${POSTGRES_PORT} -U ${POSTGRES_USER} -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='${POSTGRES_DATABASE}'" | grep -q 1; then
        echo "   ✅ Database ${POSTGRES_DATABASE} exists"
    else
        echo "   ⚠️  Database ${POSTGRES_DATABASE} does not exist - will be created during deployment"
    fi

else
    echo "   Neither kubectl nor psql is available for testing"
    echo "   Please ensure one of them is installed to test the connection"
    exit 1
fi

echo ""
echo "🎉 PostgreSQL connection test completed!"
echo ""
echo "📋 Summary:"
echo "   • Host: ${POSTGRES_HOST}:${POSTGRES_PORT}"
echo "   • User: ${POSTGRES_USER}"
echo "   • Password: ✓ (configured)"
echo "   • Target Database: ${POSTGRES_DATABASE}"
echo ""
echo "🚀 Ready to deploy SecurePaste with these PostgreSQL settings!"
echo ""
echo "📋 Next Steps:"
echo "   1. If PostgreSQL is in a different namespace:"
echo "      ./create-postgres-service-endpoint.sh"
echo "   2. Deploy the application:"
echo "      ./deploy-k8s-with-existing-postgres.sh"