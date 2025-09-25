# Connecting SecurePaste to Existing PostgreSQL in Kubernetes

This guide explains how to deploy SecurePaste to Kubernetes when you already have a PostgreSQL instance running with specific credentials.

## Prerequisites

- Existing PostgreSQL running in Kubernetes with:
  - Username: `postgres`
  - Password: `postgres123`
  - Port: `5432`
- kubectl configured to access your cluster
- SecurePaste application ready to deploy

## Quick Setup

### 1. Test PostgreSQL Connection

First, verify your PostgreSQL is accessible with the correct credentials:

```bash
# Test connection (will create a temporary pod to test)
./test-postgres-connection.sh
```

### 2. Configure Service Endpoint

If your PostgreSQL is in a different namespace, create a service endpoint:

```bash
# Auto-discover and create service endpoint
./create-postgres-service-endpoint.sh

# Or specify the namespace manually
export POSTGRES_NAMESPACE=your-postgres-namespace
export POSTGRES_SERVICE_NAME=your-postgres-service
./create-postgres-service-endpoint.sh
```

### 3. Deploy SecurePaste

Deploy the application with the working configuration:

```bash
# Deploy with the tested working configuration
./deploy-k8s-working.sh

# Or with custom base URL and NodePort
export BASE_URL="https://paste.yourdomain.com"
export NODEPORT="30080"
./deploy-k8s-working.sh
```

### 4. Access the Application

Once deployed, SecurePaste will be accessible via NodePort:

```bash
# Web UI
open http://localhost:30080/

# API endpoints
curl http://localhost:30080/api/config
curl http://localhost:30080/api/pastes/health

# Create a test paste
curl -X POST http://localhost:30080/api/pastes \
  -H "Content-Type: application/json" \
  -d '{"title":"Test","content":"Hello from Kubernetes!","visibility":"PUBLIC"}'
```

## Detailed Configuration

### Database Credentials Configuration

The application uses these credentials (configured in `k8s/postgres-external.yaml`):

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: postgres-secret
  namespace: securepaste
type: Opaque
data:
  username: cG9zdGdyZXM=      # base64: postgres
  password: cG9zdGdyZXMxMjM=   # base64: postgres123
  database: c2VjdXJlcGFzdGU=   # base64: securepaste
```

### Database Connection Configuration

Connection parameters are stored in a ConfigMap:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: database-config
  namespace: securepaste
data:
  database-host: "postgres"
  database-port: "5432"
  database-name: "securepaste"
  database-url: "jdbc:postgresql://postgres:5432/securepaste"
```

### Database Initialization

A Kubernetes Job automatically:
1. Creates the `securepaste` database if it doesn't exist
2. Sets up proper permissions
3. Creates initial schema and indexes
4. Creates an application user for future use

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: postgres-init-job
  namespace: securepaste
```

### Application Startup

The SecurePaste application includes an init container that:
1. Waits for PostgreSQL to be ready
2. Verifies the database exists
3. Only then starts the main application

## Deployment Process

The deployment follows this sequence:

1. **Namespace Creation**: Creates `securepaste` namespace
2. **Database Configuration**: Applies secrets and configmaps
3. **Database Initialization**: Runs job to setup database
4. **Application Deployment**: Deploys SecurePaste with init container
5. **Service Exposure**: Creates services and ingress (if configured)

## Verification

### Check Deployment Status

```bash
# Check all resources
kubectl get all -n securepaste

# Check specific components
kubectl get pods -n securepaste
kubectl get secrets -n securepaste  
kubectl get configmaps -n securepaste
kubectl get jobs -n securepaste
```

### View Logs

```bash
# Application logs
kubectl logs -f deployment/securepaste-app -n securepaste

# Database initialization logs  
kubectl logs job/postgres-init-job -n securepaste

# Init container logs
kubectl logs deployment/securepaste-app -c wait-for-postgres -n securepaste
```

### Test Database Connection

```bash
# Test connection from within cluster
kubectl run test-db --rm -it --image=postgres:15-alpine -n securepaste -- \
  psql -h postgres -p 5432 -U postgres -d securepaste

# Test application connectivity
kubectl port-forward service/securepaste-service 8080:80 -n securepaste
# Then access http://localhost:8080
```

## Configuration Options

### Environment Variables

You can customize the deployment using environment variables:

```bash
# PostgreSQL connection
export POSTGRES_HOST="your-postgres-host"
export POSTGRES_PORT="5432"
export POSTGRES_NAMESPACE="postgres-namespace"

# Application settings
export BASE_URL="https://paste.yourdomain.com"

# Deploy
./deploy-k8s-with-existing-postgres.sh
```

### Service Discovery

The service endpoint script supports multiple scenarios:

1. **Same Namespace**: PostgreSQL in `securepaste` namespace
2. **Different Namespace**: Creates ExternalName service
3. **Specific IP**: Creates service with manual endpoints
4. **Auto-Discovery**: Searches common namespaces

## Troubleshooting

### Common Issues

1. **Connection Refused**
   ```bash
   # Check PostgreSQL service
   kubectl get service postgres -n securepaste
   kubectl get endpoints postgres -n securepaste
   ```

2. **Authentication Failed**
   ```bash
   # Verify secret values
   kubectl get secret postgres-secret -n securepaste -o yaml
   # Check base64 decoded values
   echo "cG9zdGdyZXM=" | base64 -d  # Should output: postgres
   echo "cG9zdGdyZXMxMjM=" | base64 -d  # Should output: postgres123
   ```

3. **Database Not Found**
   ```bash
   # Check initialization job status
   kubectl describe job postgres-init-job -n securepaste
   kubectl logs job/postgres-init-job -n securepaste
   ```

4. **Application Won't Start**
   ```bash
   # Check init container logs
   kubectl logs deployment/securepaste-app -c wait-for-postgres -n securepaste
   # Check application logs  
   kubectl logs deployment/securepaste-app -c securepaste -n securepaste
   ```

### Manual Database Setup

If the automatic initialization fails, you can set up the database manually:

```bash
# Connect to PostgreSQL
kubectl run psql-client --rm -it --image=postgres:15-alpine -n securepaste -- \
  psql -h postgres -p 5432 -U postgres

# Run in PostgreSQL:
CREATE DATABASE securepaste;
GRANT ALL PRIVILEGES ON DATABASE securepaste TO postgres;
\q
```

### Reset Deployment

To start over:

```bash
# Delete all resources
kubectl delete namespace securepaste

# Redeploy
./deploy-k8s-with-existing-postgres.sh
```

## Security Considerations

1. **Credentials**: Stored in Kubernetes Secrets (base64 encoded)
2. **Network**: Uses ClusterIP services for internal communication
3. **Permissions**: Application user has minimal required permissions
4. **Isolation**: Runs in dedicated namespace with resource quotas

## Files Reference

- `k8s/postgres-external.yaml` - Database configuration for existing PostgreSQL
- `k8s/application.yaml` - SecurePaste application deployment  
- `deploy-k8s-with-existing-postgres.sh` - Automated deployment script
- `create-postgres-service-endpoint.sh` - Service endpoint creation
- `test-postgres-connection.sh` - Connection testing
- `validate-k8s-config.sh` - Configuration validation

## Next Steps

After successful deployment:

1. Configure ingress for external access
2. Set up monitoring and alerting
3. Configure backups for the database
4. Set up SSL/TLS certificates
5. Configure resource quotas and limits