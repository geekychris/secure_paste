#!/bin/bash

# Deploy SecurePaste to Kubernetes - Working Configuration
# This script reflects the actual working deployment process
set -e

echo "🚀 Deploying SecurePaste to Kubernetes (Working Configuration)"
echo "=============================================================="

# Configuration
NAMESPACE="securepaste"
BASE_URL="${BASE_URL:-https://paste.yourdomain.com}"
NODEPORT="${NODEPORT:-30080}"

echo "📍 Configuration:"
echo "   Namespace: ${NAMESPACE}"
echo "   Base URL: ${BASE_URL}"
echo "   NodePort: ${NODEPORT}"
echo ""

# Check prerequisites
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl is not installed or not in PATH"
    exit 1
fi

if ! command -v docker &> /dev/null; then
    echo "❌ docker is not installed or not in PATH"
    exit 1
fi

echo "✅ Prerequisites check passed"

# Step 1: Build the application JAR
echo ""
echo "🔨 Building application JAR..."
if [ ! -f "target/secure-pastebin-1.0.0.jar" ]; then
    mvn clean package -DskipTests
    echo "✅ JAR built successfully"
else
    echo "✅ JAR already exists"
fi

# Step 2: Build Docker image
echo ""
echo "🐳 Building Docker image..."
docker build -t securepaste:latest -f Dockerfile.simple .
echo "✅ Docker image built successfully"

# Step 3: Find existing PostgreSQL service
echo ""
echo "🔍 Locating existing PostgreSQL service..."
POSTGRES_SERVICE=$(kubectl get services --all-namespaces | grep postgres | head -1)
if [ -z "$POSTGRES_SERVICE" ]; then
    echo "❌ No PostgreSQL service found. Please ensure PostgreSQL is running in your cluster."
    exit 1
fi

POSTGRES_NAMESPACE=$(echo "$POSTGRES_SERVICE" | awk '{print $1}')
POSTGRES_SERVICE_NAME=$(echo "$POSTGRES_SERVICE" | awk '{print $2}')
echo "✅ Found PostgreSQL: $POSTGRES_SERVICE_NAME in namespace $POSTGRES_NAMESPACE"

# Step 4: Get PostgreSQL credentials
echo ""
echo "🔑 Checking PostgreSQL configuration..."
POSTGRES_POD=$(kubectl get pods -n $POSTGRES_NAMESPACE | grep postgres | head -1 | awk '{print $1}')
if [ -z "$POSTGRES_POD" ]; then
    echo "❌ No PostgreSQL pod found"
    exit 1
fi

echo "✅ Found PostgreSQL pod: $POSTGRES_POD"

# Step 5: Create namespace
echo ""
echo "📦 Creating namespace and basic resources..."
kubectl apply -f k8s/namespace.yaml
echo "✅ Namespace created/updated"

# Step 6: Create database and service configuration
echo ""
echo "🗄️  Setting up database connection..."

# Get PostgreSQL pod IP for direct connection
POSTGRES_POD_IP=$(kubectl get pod $POSTGRES_POD -n $POSTGRES_NAMESPACE -o jsonpath='{.status.podIP}')
echo "   PostgreSQL Pod IP: $POSTGRES_POD_IP"

# Apply database configuration
kubectl apply -f k8s/postgres-external.yaml

# Create service with direct pod IP endpoint
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: ${NAMESPACE}
  labels:
    app: postgres
    component: database-external
spec:
  type: ClusterIP
  ports:
  - port: 5432
    targetPort: 5432
    protocol: TCP
    name: postgres
---
apiVersion: v1
kind: Endpoints
metadata:
  name: postgres
  namespace: ${NAMESPACE}
subsets:
- addresses:
  - ip: ${POSTGRES_POD_IP}
  ports:
  - port: 5432
    protocol: TCP
    name: postgres
EOF

echo "✅ Database service endpoint created"

# Step 7: Ensure securepaste database exists
echo ""
echo "🔧 Ensuring securepaste database exists..."
kubectl exec -it $POSTGRES_POD -n $POSTGRES_NAMESPACE -- psql -U postgres -c "CREATE DATABASE securepaste;" 2>/dev/null || echo "   Database already exists or created"
echo "✅ Database setup verified"

# Step 8: Deploy the application
echo ""
echo "🚀 Deploying SecurePaste application..."
kubectl apply -f k8s/application.yaml
echo "✅ Application deployed"

# Step 9: Wait for deployment to be ready
echo ""
echo "⏳ Waiting for application to be ready..."
kubectl wait --for=condition=available --timeout=180s deployment/securepaste-app -n ${NAMESPACE}
echo "✅ Application is ready"

# Step 10: Verify deployment
echo ""
echo "🔍 Verifying deployment..."
kubectl get pods -n ${NAMESPACE}
kubectl get services -n ${NAMESPACE}

# Get NodePort
ACTUAL_NODEPORT=$(kubectl get service securepaste-service -n ${NAMESPACE} -o jsonpath='{.spec.ports[0].nodePort}')

echo ""
echo "🎉 Deployment completed successfully!"
echo ""
echo "📋 Access Information:"
echo "   • Web UI: http://localhost:${ACTUAL_NODEPORT}/"
echo "   • API: http://localhost:${ACTUAL_NODEPORT}/api/config"
echo "   • Health: http://localhost:${ACTUAL_NODEPORT}/api/pastes/health"
echo "   • NodePort: ${ACTUAL_NODEPORT}"
echo ""
echo "🧪 Test the deployment:"
echo "   curl http://localhost:${ACTUAL_NODEPORT}/api/config"
echo ""
echo "📊 Monitor the application:"
echo "   kubectl get pods -n ${NAMESPACE} -w"
echo "   kubectl logs -f deployment/securepaste-app -n ${NAMESPACE}"
echo ""
echo "💾 Database Configuration:"
echo "   • Host: postgres (via service in ${NAMESPACE} namespace)"
echo "   • Database: securepaste"
echo "   • Connection: Direct pod IP (${POSTGRES_POD_IP}:5432)"