# Security Center Deployment Guide

## Overview

This guide provides step-by-step instructions for deploying the Firefly Security Center microservice with either Keycloak or AWS Cognito as the Identity Provider.

---

## Table of Contents
- [Prerequisites](#prerequisites)
- [Architecture Overview](#architecture-overview)
- [Deployment Options](#deployment-options)
  - [Option 1: Keycloak IDP](#option-1-keycloak-idp)
  - [Option 2: AWS Cognito IDP](#option-2-aws-cognito-idp)
- [Configuration](#configuration)
- [Building and Running](#building-and-running)
- [Docker Deployment](#docker-deployment)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Verification and Testing](#verification-and-testing)
- [Monitoring and Observability](#monitoring-and-observability)
- [Troubleshooting](#troubleshooting)
- [Production Considerations](#production-considerations)

---

## Prerequisites

### Software Requirements
- **Java 21+**
- **Maven 3.9+**
- **Docker** (for containerized deployment)
- **Kubernetes** (for K8s deployment, optional)

### External Services
- **Identity Provider**: Keycloak 26.x or AWS Cognito User Pool
- **Cache** (optional for production): Redis 7+ for distributed caching
- **Downstream Services**:
  - customer-mgmt (port 8081)
  - contract-mgmt (port 8082)
  - product-mgmt (port 8083)
  - reference-master-data (port 8084)

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                      Clients / APIs                          │
└─────────────────────────┬────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│             Security Center (Port 8085)                       │
│  ┌────────────────────────────────────────────────────┐     │
│  │  AuthenticationController                           │     │
│  │  /api/v1/auth/login, /logout, /refresh            │     │
│  └─────────────────┬───────────────────────────────────┘     │
│                    │                                          │
│  ┌─────────────────▼───────────────────┐                     │
│  │   AuthenticationService             │                     │
│  │   • IDP delegation                  │                     │
│  │   • User mapping (partyId)         │                     │
│  └─────────────┬───────────────────────┘                     │
│                │                                              │
│  ┌─────────────▼──────────┐   ┌──────────────────────────┐ │
│  │  IdpAdapter            │   │  SessionAggregationService│ │
│  │  (Keycloak/Cognito)    │   │  • Customer info          │ │
│  │                        │   │  • Contracts              │ │
│  │                        │   │  • Products               │ │
│  │                        │   │  • Roles & Scopes         │ │
│  └────────────────────────┘   └───────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
             │                              │
             ▼                              ▼
┌──────────────────────┐      ┌───────────────────────────────┐
│   Keycloak / Cognito │      │  Downstream Microservices     │
│   (IDP)              │      │  • customer-mgmt              │
└──────────────────────┘      │  • contract-mgmt              │
                              │  • product-mgmt               │
                              │  • reference-master-data      │
                              └───────────────────────────────┘
```

---

## Deployment Options

### Option 1: Keycloak IDP

#### Step 1: Set up Keycloak

**Using Docker:**
```bash
docker run -d \
  --name keycloak \
  -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:26.0 \
  start-dev
```

**Access Keycloak:**
- URL: http://localhost:8080
- Admin credentials: admin/admin

#### Step 2: Configure Keycloak Realm

1. **Create Realm**:
   - Login to Keycloak Admin Console
   - Click "Create Realm"
   - Name: `firefly`

2. **Create Client**:
   - Realm: firefly → Clients → Create Client
   - Client ID: `security-center`
   - Client Protocol: `openid-connect`
   - Access Type: `confidential`
   - Service Accounts Enabled: `ON`
   - Valid Redirect URIs: `*` (for dev)
   - Save and copy the **Client Secret**

3. **Create Test User**:
   - Users → Add User
   - Username: `testuser`
   - Email: `testuser@example.com`
   - Credentials → Set Password
   - Temporary: `OFF`

4. **Create Roles**:
   - Realm Roles → Add Role
   - Example roles: `admin`, `user`, `manager`

#### Step 3: Configure Security Center for Keycloak

**application.yml:**
```yaml
firefly:
  security-center:
    idp:
      provider: keycloak
      keycloak:
        server-url: http://localhost:8080
        realm: firefly
        client-id: security-center
        client-secret: YOUR_CLIENT_SECRET_HERE
        admin-username: admin
        admin-password: admin
        connection-pool-size: 10
        connection-timeout: 30000
        request-timeout: 60000

    clients:
      customer-mgmt:
        base-url: http://localhost:8081
      contract-mgmt:
        base-url: http://localhost:8082
      product-mgmt:
        base-url: http://localhost:8083
      reference-master-data:
        base-url: http://localhost:8084
```

**Environment Variables (Production):**
```bash
export KEYCLOAK_URL=https://keycloak.prod.example.com
export KEYCLOAK_REALM=firefly
export KEYCLOAK_CLIENT_ID=security-center
export KEYCLOAK_CLIENT_SECRET=prod-secret-here
export KEYCLOAK_ADMIN_USERNAME=admin
export KEYCLOAK_ADMIN_PASSWORD=secure-admin-password
```

---

### Option 2: AWS Cognito IDP

#### Step 1: Create Cognito User Pool

**Using AWS Console:**

1. **Create User Pool**:
   - Navigate to Amazon Cognito
   - Create User Pool
   - Pool name: `firefly-users`
   - Configure sign-in experience:
     - Username + Email
     - Password policy: Strong (8+ chars, uppercase, lowercase, numbers)
   - Configure MFA: Optional
   - Enable Self-registration: Optional

2. **Create App Client**:
   - User Pool → App Integration → App clients
   - Create App Client
   - Name: `security-center-client`
   - Authentication flows:
     - ☑ ALLOW_USER_PASSWORD_AUTH
     - ☑ ALLOW_REFRESH_TOKEN_AUTH
   - Generate client secret: Yes (if needed)
   - Save Client ID and Client Secret

3. **Create Groups (Roles)**:
   - User Pool → Groups
   - Create groups: `admin`, `user`, `manager`
   - Set precedence for each group

4. **Create Test User**:
   - Users → Create User
   - Username: `testuser`
   - Email: `testuser@example.com`
   - Temporary password: Generate
   - Add user to groups

**Using AWS CLI:**
```bash
# Create User Pool
aws cognito-idp create-user-pool \
  --pool-name firefly-users \
  --policies "PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true}" \
  --auto-verified-attributes email

# Create App Client
aws cognito-idp create-user-pool-client \
  --user-pool-id us-east-1_XXXXXXXXX \
  --client-name security-center-client \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --generate-secret

# Create Group
aws cognito-idp create-group \
  --user-pool-id us-east-1_XXXXXXXXX \
  --group-name admin \
  --description "Administrator role"
```

#### Step 2: Configure Security Center for AWS Cognito

**application.yml:**
```yaml
firefly:
  security-center:
    idp:
      provider: cognito
      cognito:
        region: us-east-1
        user-pool-id: us-east-1_XXXXXXXXX
        client-id: YOUR_CLIENT_ID
        client-secret: YOUR_CLIENT_SECRET
        connection-timeout: 30000
        request-timeout: 60000

    clients:
      customer-mgmt:
        base-url: http://localhost:8081
      contract-mgmt:
        base-url: http://localhost:8082
      product-mgmt:
        base-url: http://localhost:8083
      reference-master-data:
        base-url: http://localhost:8084
```

**Environment Variables (Production):**
```bash
export AWS_REGION=us-east-1
export COGNITO_USER_POOL_ID=us-east-1_XXXXXXXXX
export COGNITO_CLIENT_ID=your-client-id
export COGNITO_CLIENT_SECRET=your-client-secret
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
```

---

## Configuration

### Cache Configuration

**In-Memory Caffeine (Development):**
```yaml
firefly:
  cache:
    enabled: true
    default-cache-type: CAFFEINE
    caffeine:
      enabled: true
      maximum-size: 10000
      expire-after-write: PT30M
      record-stats: true
```

**Redis (Production):**
```yaml
firefly:
  cache:
    enabled: true
    default-cache-type: REDIS
    redis:
      enabled: true
      host: redis.prod.example.com
      port: 6379
      password: ${REDIS_PASSWORD}
      key-prefix: "firefly:session"
```

### Logging Configuration

```yaml
logging:
  level:
    com.firefly.security.center: INFO
    com.firefly.idp: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

---

## Building and Running

### Build from Source

```bash
cd common-platform-security-center
mvn clean install -DskipTests
```

### Run Locally

```bash
java -jar common-platform-security-center-web/target/common-platform-security-center-web-1.0.0-SNAPSHOT.jar
```

### Run with Profile

```bash
# Development
java -jar -Dspring.profiles.active=dev target/security-center.jar

# Production
java -jar -Dspring.profiles.active=prod target/security-center.jar
```

---

## Docker Deployment

### Build Docker Image

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/common-platform-security-center-web-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build:**
```bash
docker build -t firefly/security-center:1.0.0 .
```

### Run with Docker Compose

**docker-compose.yml:**
```yaml
version: '3.8'

services:
  security-center:
    image: firefly/security-center:1.0.0
    ports:
      - "8085:8085"
    environment:
      # IDP Configuration (Keycloak)
      - KEYCLOAK_URL=http://keycloak:8080
      - KEYCLOAK_REALM=firefly
      - KEYCLOAK_CLIENT_ID=security-center
      - KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET}
      
      # OR IDP Configuration (AWS Cognito)
      # - AWS_REGION=us-east-1
      # - COGNITO_USER_POOL_ID=${COGNITO_USER_POOL_ID}
      # - COGNITO_CLIENT_ID=${COGNITO_CLIENT_ID}
      # - COGNITO_CLIENT_SECRET=${COGNITO_CLIENT_SECRET}
      
      # Downstream Services
      - CUSTOMER_MGMT_URL=http://customer-mgmt:8081
      - CONTRACT_MGMT_URL=http://contract-mgmt:8082
      - PRODUCT_MGMT_URL=http://product-mgmt:8083
      - REFERENCE_MASTER_DATA_URL=http://reference-master-data:8084
      
      # Redis (Production)
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - REDIS_PASSWORD=${REDIS_PASSWORD}
    depends_on:
      - redis
    networks:
      - firefly-network

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --requirepass ${REDIS_PASSWORD}
    networks:
      - firefly-network

networks:
  firefly-network:
    driver: bridge
```

**Run:**
```bash
export KEYCLOAK_CLIENT_SECRET=your-secret
export REDIS_PASSWORD=redis-password
docker-compose up -d
```

---

## Kubernetes Deployment

### ConfigMap

**security-center-config.yaml:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: security-center-config
  namespace: firefly
data:
  application.yml: |
    firefly:
      security-center:
        idp:
          provider: cognito
        clients:
          customer-mgmt:
            base-url: http://customer-mgmt:8081
          contract-mgmt:
            base-url: http://contract-mgmt:8082
          product-mgmt:
            base-url: http://product-mgmt:8083
          reference-master-data:
            base-url: http://reference-master-data:8084
```

### Secret

**security-center-secret.yaml:**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: security-center-secret
  namespace: firefly
type: Opaque
stringData:
  COGNITO_CLIENT_SECRET: "your-secret-here"
  REDIS_PASSWORD: "redis-password"
```

### Deployment

**security-center-deployment.yaml:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: security-center
  namespace: firefly
spec:
  replicas: 3
  selector:
    matchLabels:
      app: security-center
  template:
    metadata:
      labels:
        app: security-center
    spec:
      containers:
      - name: security-center
        image: firefly/security-center:1.0.0
        ports:
        - containerPort: 8085
        env:
        - name: AWS_REGION
          value: "us-east-1"
        - name: COGNITO_USER_POOL_ID
          value: "us-east-1_XXXXXXXXX"
        - name: COGNITO_CLIENT_ID
          value: "your-client-id"
        - name: COGNITO_CLIENT_SECRET
          valueFrom:
            secretKeyRef:
              name: security-center-secret
              key: COGNITO_CLIENT_SECRET
        - name: REDIS_HOST
          value: "redis-service"
        - name: REDIS_PORT
          value: "6379"
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: security-center-secret
              key: REDIS_PASSWORD
        volumeMounts:
        - name: config
          mountPath: /app/config
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8085
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8085
          initialDelaySeconds: 20
          periodSeconds: 5
      volumes:
      - name: config
        configMap:
          name: security-center-config
---
apiVersion: v1
kind: Service
metadata:
  name: security-center
  namespace: firefly
spec:
  selector:
    app: security-center
  ports:
  - port: 8085
    targetPort: 8085
  type: ClusterIP
```

**Deploy:**
```bash
kubectl apply -f security-center-config.yaml
kubectl apply -f security-center-secret.yaml
kubectl apply -f security-center-deployment.yaml
```

---

## Verification and Testing

### Health Check

```bash
curl http://localhost:8085/actuator/health
```

**Expected Response:**
```json
{
  "status": "UP"
}
```

### Test Authentication

**Login:**
```bash
curl -X POST http://localhost:8085/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

**Expected Response:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "idToken": "eyJhbGc...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "sessionId": "session_abc123",
  "partyId": "uuid-here"
}
```

### Test Session

```bash
curl http://localhost:8085/api/v1/sessions/session_abc123 \
  -H "Authorization: Bearer eyJhbGc..."
```

---

## Monitoring and Observability

### Metrics

```bash
# Prometheus metrics
curl http://localhost:8085/actuator/prometheus

# Cache metrics
curl http://localhost:8085/actuator/metrics/cache.gets
curl http://localhost:8085/actuator/metrics/cache.puts
```

### Logs

```bash
# Docker
docker logs -f security-center

# Kubernetes
kubectl logs -f deployment/security-center -n firefly
```

---

## Troubleshooting

### Issue: IDP Connection Failed

**Symptoms:**
- 500 errors on login
- "Connection refused" in logs

**Solutions:**
1. Verify IDP URL is accessible
2. Check network connectivity
3. Verify credentials
4. Check firewall rules

### Issue: Session Not Found

**Symptoms:**
- 404 on session endpoints
- Sessions expire immediately

**Solutions:**
1. Check cache configuration
2. Verify Redis connectivity (if used)
3. Check TTL settings
4. Review cache eviction logs

### Issue: Downstream Service Timeout

**Symptoms:**
- Slow session creation
- Timeout errors in logs

**Solutions:**
1. Verify downstream service URLs
2. Check network latency
3. Increase timeout configuration
4. Review downstream service health

---

## Production Considerations

### Security
- ✅ Use HTTPS/TLS in production
- ✅ Store secrets in secure vaults (AWS Secrets Manager, HashiCorp Vault)
- ✅ Rotate credentials regularly
- ✅ Enable network policies in Kubernetes
- ✅ Use service mesh for mutual TLS

### Performance
- ✅ Use Redis for distributed caching
- ✅ Enable connection pooling
- ✅ Configure appropriate cache TTLs
- ✅ Monitor cache hit rates
- ✅ Scale horizontally with multiple replicas

### Reliability
- ✅ Configure health checks and readiness probes
- ✅ Set resource limits (CPU, memory)
- ✅ Enable graceful shutdown
- ✅ Implement circuit breakers for downstream services
- ✅ Set up monitoring and alerting

### Compliance
- ✅ Enable audit logging
- ✅ Mask sensitive data in logs
- ✅ Implement GDPR compliance (data retention, deletion)
- ✅ Regular security audits

---

## Support

For issues or questions:
- Check logs: `kubectl logs -f security-center-pod -n firefly`
- Review metrics: `/actuator/prometheus`
- Consult documentation: `IDP_INTEGRATION.md`, `IMPLEMENTATION_SUMMARY.md`
- Contact DevOps team for infrastructure issues
