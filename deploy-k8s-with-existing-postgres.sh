#!/bin/bash

# Deploy SecurePaste to Kubernetes with existing PostgreSQL
set -e

echo "🚀 Deploying SecurePaste to Kubernetes with existing PostgreSQL"
echo "=============================================================="

# Configuration
NAMESPACE="securepaste"
POSTGRES_HOST="${POSTGRES_HOST:-postgres}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
BASE_URL="${BASE_URL:-https://paste.yourdomain.com}"

echo "📍 Configuration:"
echo "   Namespace: ${NAMESPACE}"
echo "   PostgreSQL Host: ${POSTGRES_HOST}"
echo "   PostgreSQL Port: ${POSTGRES_PORT}"
echo "   Base URL: ${BASE_URL}"
echo ""

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl is not installed or not in PATH"
    exit 1
fi

echo "✅ kubectl is available"

# Create namespace if it doesn't exist
echo ""
echo "📦 Creating namespace..."
kubectl apply -f k8s/namespace.yaml
echo "✅ Namespace created/updated"

# Deploy database configuration (secrets and configmaps)
echo ""
echo "🗄️  Deploying database configuration..."
kubectl apply -f k8s/postgres-external.yaml
echo "✅ Database configuration deployed"

# Wait a moment for resources to be created
sleep 2

# Run database initialization job
echo ""
echo "🔧 Running database initialization..."
kubectl delete job postgres-init-job -n ${NAMESPACE} --ignore-not-found=true
kubectl apply -f k8s/postgres-external.yaml

# Wait for the initialization job to complete
echo "⏳ Waiting for database initialization to complete..."
kubectl wait --for=condition=complete --timeout=300s job/postgres-init-job -n ${NAMESPACE}

if kubectl get job postgres-init-job -n ${NAMESPACE} -o jsonpath='{.status.conditions[?(@.type=="Complete")].status}' | grep -q "True"; then
    echo "✅ Database initialization completed successfully"
else
    echo "❌ Database initialization failed"
    echo "Job status:"
    kubectl describe job postgres-init-job -n ${NAMESPACE}
    echo ""
    echo "Job logs:"
    kubectl logs job/postgres-init-job -n ${NAMESPACE}
    exit 1
fi

# Update BASE_URL in application.yaml if provided
if [ "$BASE_URL" != "https://paste.yourdomain.com" ]; then
    echo ""
    echo "🔧 Updating BASE_URL to: ${BASE_URL}"
    sed -i.bak "s|https://paste.yourdomain.com|${BASE_URL}|g" k8s/application.yaml
fi

# Deploy the application
echo ""
echo "🚀 Deploying SecurePaste application..."
kubectl apply -f k8s/application.yaml
echo "✅ Application deployed"

# Deploy ingress if it exists
if [ -f "k8s/ingress.yaml" ]; then
    echo ""
    echo "🌐 Deploying ingress..."
    kubectl apply -f k8s/ingress.yaml
    echo "✅ Ingress deployed"
fi

# Wait for application to be ready
echo ""
echo "⏳ Waiting for application to be ready..."
kubectl wait --for=condition=available --timeout=300s deployment/securepaste-app -n ${NAMESPACE}

# Check the status
echo ""
echo "📊 Deployment Status:"
kubectl get pods -n ${NAMESPACE}
echo ""
kubectl get services -n ${NAMESPACE}
echo ""
kubectl get ingress -n ${NAMESPACE} 2>/dev/null || echo "No ingress configured"

# Get application logs
echo ""
echo "📋 Recent application logs:"
kubectl logs -l app=securepaste,component=application -n ${NAMESPACE} --tail=10

echo ""
echo "🎉 Deployment completed successfully!"
echo ""
echo "📋 Summary:"
echo "   • Database: PostgreSQL at ${POSTGRES_HOST}:${POSTGRES_PORT}"
echo "   • Database: securepaste (created/verified)"
echo "   • Application: Running with kubernetes profile"
echo "   • Base URL: ${BASE_URL}"
echo ""
echo "🔍 To check status:"
echo "   kubectl get pods -n ${NAMESPACE}"
echo "   kubectl logs -f deployment/securepaste-app -n ${NAMESPACE}"
echo ""
echo "🧪 To test the application:"
echo "   kubectl port-forward service/securepaste-service 8080:80 -n ${NAMESPACE}"
echo "   # Then access http://localhost:8080"
echo ""
echo "💡 To update BASE_URL later:"
echo "   kubectl set env deployment/securepaste-app BASE_URL=https://your-new-domain.com -n ${NAMESPACE}"

# Restore original file if we modified it
if [ -f "k8s/application.yaml.bak" ]; then
    mv k8s/application.yaml.bak k8s/application.yaml
fi