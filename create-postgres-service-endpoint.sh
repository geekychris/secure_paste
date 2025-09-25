#!/bin/bash

# Script to create service and endpoints for existing PostgreSQL
set -e

echo "🔗 Creating Service Endpoint for Existing PostgreSQL"
echo "================================================="

# Configuration - Update these values based on your PostgreSQL location
POSTGRES_NAMESPACE="${POSTGRES_NAMESPACE:-default}"
POSTGRES_SERVICE_NAME="${POSTGRES_SERVICE_NAME:-postgres}"
POSTGRES_IP="${POSTGRES_IP}"
SECUREPASTE_NAMESPACE="${SECUREPASTE_NAMESPACE:-securepaste}"

echo "📍 Configuration:"
echo "   PostgreSQL Namespace: ${POSTGRES_NAMESPACE}"
echo "   PostgreSQL Service Name: ${POSTGRES_SERVICE_NAME}"
echo "   PostgreSQL IP: ${POSTGRES_IP:-auto-detect}"
echo "   SecurePaste Namespace: ${SECUREPASTE_NAMESPACE}"
echo ""

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl is not installed or not in PATH"
    exit 1
fi

# Function to create ExternalName service (for cross-namespace access)
create_external_name_service() {
    local external_name="$1"
    echo "🔧 Creating ExternalName service pointing to: ${external_name}"
    
    cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: ${SECUREPASTE_NAMESPACE}
  labels:
    app: postgres
    component: database-external
spec:
  type: ExternalName
  externalName: ${external_name}
  ports:
  - port: 5432
    targetPort: 5432
    protocol: TCP
    name: postgres
EOF
    
    echo "✅ ExternalName service created"
}

# Function to create service with manual endpoints (for specific IP)
create_service_with_endpoints() {
    local postgres_ip="$1"
    echo "🔧 Creating service and endpoints for IP: ${postgres_ip}"
    
    # Create the service
    cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: ${SECUREPASTE_NAMESPACE}
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
  namespace: ${SECUREPASTE_NAMESPACE}
subsets:
- addresses:
  - ip: ${postgres_ip}
  ports:
  - port: 5432
    protocol: TCP
    name: postgres
EOF
    
    echo "✅ Service and endpoints created"
}

# Create SecurePaste namespace if it doesn't exist
kubectl create namespace ${SECUREPASTE_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -

# Check if PostgreSQL service exists in the specified namespace
if kubectl get service ${POSTGRES_SERVICE_NAME} -n ${POSTGRES_NAMESPACE} &> /dev/null; then
    echo "✅ Found PostgreSQL service: ${POSTGRES_SERVICE_NAME} in namespace: ${POSTGRES_NAMESPACE}"
    
    # If it's in the same namespace, no need to create a service
    if [ "${POSTGRES_NAMESPACE}" = "${SECUREPASTE_NAMESPACE}" ]; then
        echo "✅ PostgreSQL is in the same namespace. No additional service needed."
    else
        # Create ExternalName service for cross-namespace access
        external_name="${POSTGRES_SERVICE_NAME}.${POSTGRES_NAMESPACE}.svc.cluster.local"
        create_external_name_service "$external_name"
    fi
    
elif [ -n "${POSTGRES_IP}" ]; then
    echo "📍 Using provided PostgreSQL IP: ${POSTGRES_IP}"
    create_service_with_endpoints "$POSTGRES_IP"
    
else
    echo "🔍 PostgreSQL service not found in namespace ${POSTGRES_NAMESPACE}"
    echo "Attempting to auto-discover PostgreSQL service..."
    
    # Try to find PostgreSQL service in common namespaces
    for ns in default kube-system postgres postgresql; do
        if kubectl get namespace "$ns" &> /dev/null; then
            echo "   Checking namespace: $ns"
            postgres_services=$(kubectl get services -n "$ns" -o name | grep -i postgres || true)
            if [ -n "$postgres_services" ]; then
                echo "   Found PostgreSQL services in namespace $ns:"
                echo "$postgres_services" | sed 's/^/     /'
                
                # Use the first found service
                first_service=$(echo "$postgres_services" | head -1 | sed 's|service/||')
                external_name="${first_service}.${ns}.svc.cluster.local"
                echo "   Using: $external_name"
                create_external_name_service "$external_name"
                echo ""
                echo "✅ Service endpoint created successfully!"
                exit 0
            fi
        fi
    done
    
    echo ""
    echo "❌ Could not find PostgreSQL service automatically."
    echo ""
    echo "Please provide the PostgreSQL connection details manually:"
    echo ""
    echo "Option 1 - If PostgreSQL is a Kubernetes service:"
    echo "   export POSTGRES_NAMESPACE=<namespace>"
    echo "   export POSTGRES_SERVICE_NAME=<service-name>"
    echo "   ./create-postgres-service-endpoint.sh"
    echo ""
    echo "Option 2 - If PostgreSQL has a specific IP:"
    echo "   export POSTGRES_IP=<ip-address>"
    echo "   ./create-postgres-service-endpoint.sh"
    echo ""
    echo "Option 3 - List all services to find PostgreSQL:"
    echo "   kubectl get services --all-namespaces | grep -i postgres"
    echo ""
    exit 1
fi

echo ""
echo "🎉 Service endpoint configuration completed!"
echo ""
echo "🔍 Verification:"
echo "   kubectl get service postgres -n ${SECUREPASTE_NAMESPACE}"
echo "   kubectl get endpoints postgres -n ${SECUREPASTE_NAMESPACE}"
echo ""
echo "🧪 Test connectivity:"
echo "   kubectl run test-postgres --rm -it --image=postgres:15-alpine -n ${SECUREPASTE_NAMESPACE} -- psql -h postgres -p 5432 -U postgres"
echo ""
echo "📋 Next steps:"
echo "   1. Test the connection using the above command"
echo "   2. Run: ./deploy-k8s-with-existing-postgres.sh"