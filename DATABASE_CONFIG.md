# SecurePaste Database Configuration Guide

## Quick Reference

| Environment | Database | Profile | Configuration Method |
|-------------|----------|---------|---------------------|
| Development | H2 (in-memory) | `default` | application.yml (automatic) |
| Production | PostgreSQL | `production` | Environment variables |
| Docker | PostgreSQL | `docker` | docker-compose.yml + .env |
| Kubernetes | PostgreSQL | `kubernetes` | ConfigMap + Secret |

## Configuration Details

### 1. Development Environment
**No configuration required** - Uses H2 in-memory database automatically.

```bash
mvn spring-boot:run
# Automatically uses H2 database
```

### 2. Production Environment
**PostgreSQL with environment variables**

```bash
# Set environment variables
export DB_HOST="your-postgres-host"
export DB_PORT="5432"
export DB_NAME="pastebin"
export DB_USERNAME="your_username"  
export DB_PASSWORD="your_password"
export BASE_URL="https://paste.yourdomain.com"

# Run with production profile
java -jar target/secure-pastebin-*.jar --spring.profiles.active=production
```

### 3. Docker Environment
**PostgreSQL in containers**

```bash
# Copy environment template
cp .env.example .env

# Edit .env file:
# DB_HOST=postgres
# DB_PORT=5432
# DB_NAME=pastebin
# DB_USERNAME=pastebin
# DB_PASSWORD=pastebin123
# BASE_URL=http://localhost:8080

# Start with Docker Compose
docker-compose up -d
```

### 4. Kubernetes Environment
**PostgreSQL with ConfigMap and Secret**

#### Step 1: Customize Database Configuration
Edit `k8s/postgres.yaml`:

```yaml
# ConfigMap - Connection parameters
apiVersion: v1
kind: ConfigMap
metadata:
  name: database-config
  namespace: securepaste
data:
  database-host: "postgres"
  database-port: "5432" 
  database-name: "pastebin"
  database-url: "jdbc:postgresql://postgres:5432/pastebin"

---
# Secret - Credentials (base64 encoded)
apiVersion: v1
kind: Secret
metadata:
  name: postgres-secret
  namespace: securepaste
type: Opaque
data:
  username: cGFzdGViaW4=    # pastebin
  password: cGFzdGViaW4xMjM= # pastebin123
  database: cGFzdGViaW4=    # pastebin
```

#### Step 2: Update Application Configuration
Edit `k8s/application.yaml`:

```yaml
env:
- name: SPRING_PROFILES_ACTIVE
  value: "kubernetes"
- name: BASE_URL
  value: "https://paste.yourdomain.com"
# Database config from ConfigMap
- name: DB_HOST
  valueFrom:
    configMapKeyRef:
      name: database-config
      key: database-host
# Database credentials from Secret
- name: DB_USERNAME
  valueFrom:
    secretKeyRef:
      name: postgres-secret
      key: username
```

#### Step 3: Deploy
```bash
kubectl apply -f k8s/
kubectl get pods -n securepaste
```

## Environment Variables Reference

| Variable | Description | Default | Example |
|----------|-------------|---------|---------|
| `DB_URL` | Complete JDBC URL | Constructed from components | `jdbc:postgresql://localhost:5432/pastebin` |
| `DB_HOST` | Database hostname | `localhost` (prod), `postgres` (docker/k8s) | `my-postgres-server.com` |
| `DB_PORT` | Database port | `5432` | `5432` |
| `DB_NAME` | Database name | `pastebin` | `securepaste_prod` |
| `DB_USERNAME` | Database username | `pastebin` | `paste_user` |
| `DB_PASSWORD` | Database password | `pastebin123` | `secure_password` |
| `BASE_URL` | External URL | Environment specific | `https://paste.example.com` |

## Testing Database Configuration

Use the provided test script to verify database connectivity:

```bash
# Test development environment (H2)
./test-database-config.sh development

# Test production environment (PostgreSQL)  
./test-database-config.sh production

# Test Docker environment
./test-database-config.sh docker localhost 8080

# Test Kubernetes environment (after deployment)
./test-database-config.sh kubernetes your-k8s-host 80
```

## Troubleshooting

### Common Issues

1. **Connection refused**: Check if PostgreSQL is running and accessible
2. **Authentication failed**: Verify username/password in environment variables
3. **Database not found**: Ensure database exists or enable auto-creation
4. **Wrong profile**: Check `SPRING_PROFILES_ACTIVE` environment variable

### Debug Commands

```bash
# Check current configuration
curl http://localhost:8097/api/config

# View application logs
kubectl logs deployment/securepaste-app -n securepaste

# Check environment variables in pod
kubectl exec -it deployment/securepaste-app -n securepaste -- env | grep DB_

# Test database connectivity
kubectl exec -it deployment/postgres -n securepaste -- psql -U pastebin -d pastebin -c "SELECT version();"
```

## Security Notes

- Never commit database passwords to version control
- Use Kubernetes Secrets for sensitive data in production
- Consider using external secret management systems (e.g., HashiCorp Vault)
- Regularly rotate database credentials
- Use TLS/SSL for database connections in production