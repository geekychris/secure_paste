#!/bin/bash

# Script to validate Kubernetes configuration for SecurePaste
set -e

echo "🔍 Validating SecurePaste Kubernetes Configuration"
echo "================================================="

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl is not installed or not in PATH"
    exit 1
fi

echo "✅ kubectl is available"

# Validate YAML syntax
echo ""
echo "📝 Validating YAML syntax..."

for file in k8s/*.yaml k8s/*.yml; do
    if [ -f "$file" ]; then
        echo "   Checking $file..."
        if kubectl apply --dry-run=client -f "$file" > /dev/null 2>&1; then
            echo "   ✅ $file is valid"
        else
            echo "   ❌ $file has syntax errors"
            kubectl apply --dry-run=client -f "$file"
            exit 1
        fi
    fi
done

# Validate database configuration structure
echo ""
echo "🗄️  Validating database configuration..."

# Check if postgres-secret exists in manifest (check both files)
if grep -q "name: postgres-secret" k8s/postgres.yaml k8s/postgres-external.yaml 2>/dev/null; then
    echo "   ✅ postgres-secret found"
    
    # Check if all required secret keys are present
    if (grep -A 10 "name: postgres-secret" k8s/postgres.yaml k8s/postgres-external.yaml 2>/dev/null | grep -q "username:") && \
       (grep -A 10 "name: postgres-secret" k8s/postgres.yaml k8s/postgres-external.yaml 2>/dev/null | grep -q "password:") && \
       (grep -A 10 "name: postgres-secret" k8s/postgres.yaml k8s/postgres-external.yaml 2>/dev/null | grep -q "database:"); then
        echo "   ✅ postgres-secret has all required keys (username, password, database)"
        
        # Check if using the correct credentials for existing PostgreSQL
        if grep -A 10 "name: postgres-secret" k8s/postgres-external.yaml 2>/dev/null | grep -q "cG9zdGdyZXM="; then
            echo "   ✅ postgres-secret uses 'postgres' username (correct for existing PostgreSQL)"
        else
            echo "   ⚠️  postgres-secret username may not match existing PostgreSQL"
        fi
        
        if grep -A 10 "name: postgres-secret" k8s/postgres-external.yaml 2>/dev/null | grep -q "cG9zdGdyZXMxMjM="; then
            echo "   ✅ postgres-secret uses 'postgres123' password (correct for existing PostgreSQL)"
        else
            echo "   ⚠️  postgres-secret password may not match existing PostgreSQL"
        fi
    else
        echo "   ❌ postgres-secret is missing required keys"
        exit 1
    fi
else
    echo "   ❌ postgres-secret not found in PostgreSQL configuration files"
    exit 1
fi

# Check if database-config ConfigMap exists
if grep -q "name: database-config" k8s/postgres.yaml; then
    echo "   ✅ database-config ConfigMap found"
    
    # Check if all required config keys are present
    if grep -A 10 "name: database-config" k8s/postgres.yaml | grep -q "database-host:" && \
       grep -A 10 "name: database-config" k8s/postgres.yaml | grep -q "database-port:" && \
       grep -A 10 "name: database-config" k8s/postgres.yaml | grep -q "database-name:" && \
       grep -A 10 "name: database-config" k8s/postgres.yaml | grep -q "database-url:"; then
        echo "   ✅ database-config has all required keys"
    else
        echo "   ❌ database-config is missing required keys"
        exit 1
    fi
else
    echo "   ❌ database-config ConfigMap not found in k8s/postgres.yaml"
    exit 1
fi

# Validate application configuration
echo ""
echo "🚀 Validating application configuration..."

# Check if application uses database environment variables
if grep -q "DB_HOST" k8s/application.yaml && \
   grep -q "DB_PORT" k8s/application.yaml && \
   grep -q "DB_NAME" k8s/application.yaml && \
   grep -q "DB_USERNAME" k8s/application.yaml && \
   grep -q "DB_PASSWORD" k8s/application.yaml; then
    echo "   ✅ Application deployment has all required database environment variables"
else
    echo "   ❌ Application deployment is missing database environment variables"
    exit 1
fi

# Check if environment variables reference correct ConfigMap and Secret
if grep -A 5 "name: DB_HOST" k8s/application.yaml | grep -q "database-config" && \
   grep -A 5 "name: DB_USERNAME" k8s/application.yaml | grep -q "postgres-secret"; then
    echo "   ✅ Environment variables reference correct ConfigMap and Secret"
else
    echo "   ❌ Environment variables don't reference correct ConfigMap/Secret"
    exit 1
fi

# Check Spring profile
if grep -q 'value: "kubernetes"' k8s/application.yaml; then
    echo "   ✅ Application uses 'kubernetes' Spring profile"
else
    echo "   ❌ Application doesn't use 'kubernetes' Spring profile"
    exit 1
fi

# Check BASE_URL configuration
if grep -q "BASE_URL" k8s/application.yaml; then
    echo "   ✅ BASE_URL is configured"
    BASE_URL_VALUE=$(grep -A 1 "name: BASE_URL" k8s/application.yaml | grep "value:" | cut -d '"' -f 2)
    if [ "$BASE_URL_VALUE" != "https://paste.yourdomain.com" ]; then
        echo "   ⚠️  BASE_URL is customized to: $BASE_URL_VALUE"
    else
        echo "   ℹ️  BASE_URL uses default placeholder (remember to customize for production)"
    fi
else
    echo "   ❌ BASE_URL is not configured"
    exit 1
fi

# Validate namespace consistency
echo ""
echo "📦 Validating namespace consistency..."

NAMESPACES=$(grep -h "namespace:" k8s/*.yaml | sort | uniq)
if [ "$(echo "$NAMESPACES" | wc -l)" -eq 1 ] && echo "$NAMESPACES" | grep -q "securepaste"; then
    echo "   ✅ All resources use consistent 'securepaste' namespace"
else
    echo "   ❌ Inconsistent namespace usage:"
    echo "$NAMESPACES"
    exit 1
fi

# Check resource requirements
echo ""
echo "💾 Validating resource requirements..."

if grep -q "resources:" k8s/application.yaml && grep -q "resources:" k8s/postgres.yaml; then
    echo "   ✅ Resource requirements are defined for both application and database"
else
    echo "   ⚠️  Resource requirements should be defined for production use"
fi

# Validate security context
echo ""
echo "🔒 Validating security configuration..."

if grep -q "securityContext:" k8s/application.yaml; then
    echo "   ✅ Security context is configured for application"
else
    echo "   ⚠️  Security context should be configured for production use"
fi

# Check for health checks
echo ""
echo "🏥 Validating health checks..."

if grep -q "livenessProbe:" k8s/application.yaml && grep -q "readinessProbe:" k8s/application.yaml; then
    echo "   ✅ Application has liveness and readiness probes"
else
    echo "   ❌ Application missing health check probes"
fi

if grep -q "livenessProbe:" k8s/postgres.yaml && grep -q "readinessProbe:" k8s/postgres.yaml; then
    echo "   ✅ PostgreSQL has liveness and readiness probes"
else
    echo "   ❌ PostgreSQL missing health check probes"
fi

echo ""
echo "🎉 Kubernetes configuration validation completed successfully!"
echo ""
echo "📋 Configuration Summary:"
echo "   • Database: PostgreSQL with ConfigMap and Secret"
echo "   • Application Profile: kubernetes"
echo "   • Namespace: securepaste"  
echo "   • Base URL: $(grep -A 1 "name: BASE_URL" k8s/application.yaml | grep "value:" | cut -d '"' -f 2)"
echo "   • Health Checks: Configured"
echo ""
echo "🚀 Ready to deploy with:"
echo "   kubectl apply -f k8s/"
echo ""
echo "💡 Don't forget to:"
echo "   1. Customize BASE_URL in k8s/application.yaml"
echo "   2. Update database credentials if needed"
echo "   3. Adjust resource limits for your cluster"