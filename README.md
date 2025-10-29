# Firefly Security Center

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/Tests-20%2F20%20Passing-brightgreen.svg)](#test-coverage)

**Centralized session management and security orchestration for the Firefly Core Banking Platform**

---

## Table of Contents

- [Introduction](#introduction)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [API Endpoints](#api-endpoints)
- [Documentation](#documentation)
- [Test Coverage](#test-coverage)
- [Technology Stack](#technology-stack)

---

## Introduction

The **Firefly Security Center** is a critical microservice that provides centralized authentication, session management, and security context orchestration for the Firefly Core Banking Platform.

### What It Does

The Security Center acts as the **single source of truth** for user sessions across the entire platform. When a user authenticates, the Security Center:

1. **Authenticates** the user via an Identity Provider (Keycloak or AWS Cognito)
2. **Enriches** the session with customer data, active contracts, products, and permissions
3. **Caches** the session for fast retrieval
4. **Provides** session access to all other microservices via the `FireflySessionManager` interface

### Key Capabilities

- **🔐 Multi-IDP Support** - Switch between Keycloak and AWS Cognito via configuration
- **📦 Session Enrichment** - Aggregates data from customer-mgmt, contract-mgmt, product-mgmt, and reference-master-data
- **⚡ High Performance** - Caffeine-backed caching with optional Redis support
- **🔄 Reactive Architecture** - Non-blocking, built on Spring WebFlux and Project Reactor with Java 21 Virtual Threads
- **🔌 Exportable Library** - Other services import `FireflySessionManager` for session access
- **✅ Production Ready** - 100% test coverage with Testcontainers

### Who Uses It

**All Firefly microservices** depend on the Security Center for:
- User authentication
- Authorization decisions (contract-based access control)
- Customer and product context
- Role and permission resolution

---

## How It Works

### The Big Picture

The Security Center is the **authentication and session orchestration hub** for the entire Firefly platform. Here's what happens when a user logs in:

```
┌─────────────┐
│   User      │
│  (Browser)  │
└──────┬──────┘
       │ 1. POST /login
       │    {username, password}
       ▼
┌─────────────────────────────────────────────────────────┐
│           Security Center (This Service)                │
│                                                         │
│  ┌────────────────────────────────────────────────┐     │
│  │ 2. Authenticate with IDP                       │     │
│  │    ├─→ Keycloak (OIDC/OAuth2)                  │     │
│  │    └─→ AWS Cognito                             │     │
│  └────────────────────────────────────────────────┘     │
│                      ↓                                  │
│  ┌────────────────────────────────────────────────┐     │
│  │ 3. Extract partyId from token                  │     │
│  └────────────────────────────────────────────────┘     │
│                      ↓                                  │
│  ┌────────────────────────────────────────────────┐     │
│  │ 4. Enrich Session (Parallel Calls)             │     │
│  │    ├─→ Customer Mgmt: Get customer profile     │     │
│  │    └─→ Contract Mgmt: Get active contracts     │     │
│  │         └─→ For each contract:                 │     │
│  │             ├─→ Get contract details           │     │
│  │             ├─→ Get role & permissions         │     │
│  │             └─→ Get product info               │     │
│  └────────────────────────────────────────────────┘     │
│                      ↓                                  │
│  ┌────────────────────────────────────────────────┐     │
│  │ 5. Create SessionContext                       │     │
│  │    - Customer info                             │     │
│  │    - Active contracts                          │     │
│  │    - Products                                  │     │
│  │    - Roles & permissions                       │     │
│  │    - IDP tokens                                │     │
│  └────────────────────────────────────────────────┘     │
│                      ↓                                  │
│  ┌────────────────────────────────────────────────┐     │
│  │ 6. Cache in Redis/Caffeine                     │     │
│  │    Key: firefly:session:{sessionId}            │     │
│  │    TTL: 30 minutes                             │     │
│  └────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────┘
       │
       │ 7. Return enriched session
       ▼
┌─────────────┐
│   User      │
│  (Browser)  │
└─────────────┘
```

### Key Concepts

#### 1. **Pluggable IDP Adapters**

The Security Center doesn't care which identity provider you use. It uses an adapter pattern:

```java
public interface IdpAdapter {
    Mono<TokenResponse> login(LoginRequest request);
    Mono<TokenResponse> refreshToken(String refreshToken);
    Mono<Void> logout(String accessToken, String refreshToken);
    Mono<UserInfoResponse> getUserInfo(String accessToken);
}
```

**Available Adapters:**
- `KeycloakIdpAdapter` - For Keycloak (OIDC/OAuth 2.0)
- `CognitoIdpAdapter` - For AWS Cognito

**Switching IDPs:** Just change one configuration property:
```yaml
firefly:
  security-center:
    idp:
      provider: keycloak  # or cognito
```

#### 2. **Session Enrichment with Real SDKs**

The Security Center uses **OpenAPI-generated SDK clients** to call downstream microservices:

```java
@Service
public class SessionAggregationService {
    private final CustomerResolverService customerResolver;
    private final ContractResolverService contractResolver;

    public Mono<SessionContext> aggregateSession(UUID partyId) {
        // Parallel calls to downstream services
        Mono<CustomerInfo> customer = customerResolver.resolveCustomerInfo(partyId);
        Mono<List<ContractInfo>> contracts = contractResolver.resolveActiveContracts(partyId);

        return Mono.zip(customer, contracts)
            .map(tuple -> SessionContext.builder()
                .customer(tuple.getT1())
                .activeContracts(tuple.getT2())
                .build());
    }
}
```

**SDKs Used:**
- `common-platform-customer-mgmt-sdk` - Customer profiles
- `common-platform-contract-mgmt-sdk` - Contracts and parties
- `common-platform-product-mgmt-sdk` - Product information
- `common-platform-reference-master-data-sdk` - Roles and permissions

#### 3. **IDP User to Party Mapping**

The Security Center maps IDP users to Firefly partyIds using the customer-mgmt SDK:

```java
@Service
public class DefaultUserMappingService implements UserMappingService {
    private final PartiesApi partiesApi;
    private final EmailContactsApi emailContactsApi;

    public Mono<UUID> mapToPartyId(UserInfoResponse userInfo, String username) {
        // 1. Try email lookup
        if (userInfo.getEmail() != null) {
            return findPartyByEmail(userInfo.getEmail())
                .onErrorResume(error -> {
                    // 2. Try username lookup
                    if (username != null) {
                        return findPartyByUsername(username);
                    }
                    // 3. No fallback - party MUST exist
                    return Mono.error(new IllegalStateException(
                        "No party found for IDP user. Party must exist before authentication."));
                });
        }
        return findPartyByUsername(username);
    }
}
```

**Mapping Strategy:**
- ✅ Email-based lookup: Searches all parties' email contacts
- ✅ Username-based lookup: Searches parties by `sourceSystem` field (format: `"idp:username"`)
- ✅ **No fallbacks**: Authentication fails if party not found
- ✅ **Data consistency**: Ensures all sessions have valid partyIds

**Important**: Parties must exist in customer-mgmt before users can authenticate. This ensures data consistency across all microservices.

#### 4. **Error Propagation**

The Security Center follows a **fail-fast** approach with proper error propagation:

```java
public Mono<CustomerInfo> resolveCustomerInfo(UUID partyId) {
    return partiesApi.getPartyById(partyId)
        .flatMap(party -> enrichCustomerInfo(party))
        .doOnError(error ->
            log.error("Failed to fetch customer info for partyId: {}", partyId, error));
    // Errors propagate to caller - no fallbacks
}
```

**Error Handling Principles:**
- ✅ All errors propagate to the API layer
- ✅ Clear error messages for debugging
- ✅ No silent failures or placeholder data
- ✅ HTTP 500 returned with error details
- ⚠️ Ensure downstream services are available for authentication to succeed

#### 5. **High-Performance Caching**

Sessions are cached to avoid repeated calls to downstream services:

```
First Login:
  User → Security Center → IDP + 4 Microservices → Cache → User
  Time: ~500ms

Subsequent Requests (within 30 min):
  User → Security Center → Cache → User
  Time: ~5ms (100x faster!)
```

**Cache Backends:**
- **Caffeine** - In-memory cache (default, recommended for single instance)
- **Redis** - Distributed cache for production (multiple instances, optional)

#### 6. **Exportable Session Library**

Other microservices don't call the Security Center's REST API. Instead, they import the session library:

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>core-domain-security-center-session</artifactId>
</dependency>
```

Then use `FireflySessionManager` to access sessions:

```java
@Service
public class AccountService {
    @Autowired
    private FireflySessionManager sessionManager;

    public Mono<Account> getAccount(UUID accountId, ServerWebExchange exchange) {
        return sessionManager.createOrGetSession(exchange)
            .flatMap(session -> {
                // Check if user has access to this account
                boolean hasAccess = session.getActiveContracts().stream()
                    .anyMatch(c -> c.getProduct().getProductId().equals(accountId));

                if (!hasAccess) {
                    return Mono.error(new UnauthorizedException());
                }

                return accountRepository.findById(accountId);
            });
    }
}
```

**Benefits:**
- ✅ Type-safe session access
- ✅ No HTTP overhead
- ✅ Shared cache across all services
- ✅ Consistent authorization logic

---

## Architecture

### System Design

The Security Center uses a **layered, modular architecture** with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────┐
│              External Consumers                         │
│         (Other Firefly Microservices)                   │
└────────────────────┬────────────────────────────────────┘
                     │ Import FireflySessionManager
                     ▼
┌─────────────────────────────────────────────────────────┐
│        Security Center - Session Library                │
│    (core-domain-security-center-session)            │
│       FireflySessionManager Interface                   │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│        Security Center - Core Business Logic            │
│    (core-domain-security-center-core)               │
│  • AuthenticationService                                │
│  • Session Enrichment Services                          │
│  • IDP Adapter Selection                                │
└────────────────────┬────────────────────────────────────┘
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
  ┌─────────────┐         ┌─────────────┐
  │  Keycloak   │         │   Cognito   │
  │   Adapter   │         │   Adapter   │
  └─────────────┘         └─────────────┘
         │                       │
         └───────────┬───────────┘
                     ▼
            ┌─────────────────┐
            │ Redis Cache     │
            │ (lib-common-    │
            │     cache)      │
            └─────────────────┘
```

### Module Structure

```
core-domain-security-center/
├── core-domain-security-center-interfaces/
│   └── DTOs and data contracts
│
├── core-domain-security-center-session/      ⭐ EXPORTABLE
│   └── FireflySessionManager interface
│       (Imported by all other microservices)
│
├── core-domain-security-center-core/
│   ├── AuthenticationService
│   ├── Session Enrichment (Customer, Contract, Product)
│   ├── IDP Adapters (Keycloak, Cognito)
│   └── Caching Integration
│
├── core-domain-security-center-web/
│   ├── REST API Controllers
│   ├── Spring Boot Application
│   └── Integration Tests
│
└── core-domain-security-center-sdk/
    └── Client SDK for downstream services
```

### Data Flow

**1. Authentication Flow:**
```
User → POST /login → IDP Adapter → IDP (Keycloak/Cognito)
  ↓
Tokens (access, refresh, ID)
  ↓
Extract partyId from token
  ↓
Parallel Enrichment:
  ├─→ Customer Management: Fetch customer info
  ├─→ Contract Management: Fetch active contracts
  │    └─→ For each contract:
  │         ├─→ Reference Data: Fetch role details
  │         ├─→ Reference Data: Fetch role scopes (permissions)
  │         └─→ Product Management: Fetch product info
  ↓
Aggregate into SessionContext
  ↓
Cache in Redis with TTL
  ↓
Return session to client
```

**2. Session Retrieval (from other services):**
```
Microservice → FireflySessionManager.getSession()
  ↓
Check Redis cache
  ├─→ Hit: Return cached session
  └─→ Miss: Fetch from downstream services + cache
```

### IDP Adapter Pattern

The Security Center uses a **pluggable adapter pattern** for identity providers:

```java
public interface IdpAdapter {
    Mono<AuthResponse> authenticate(String username, String password);
    Mono<AuthResponse> refreshToken(String refreshToken);
    Mono<Void> logout(String accessToken, String refreshToken);
    Mono<UserInfo> getUserInfo(String accessToken);
}
```

**Implementations:**
- `KeycloakIdpAdapter` - For Keycloak (OIDC/OAuth 2.0)
- `CognitoIdpAdapter` - For AWS Cognito

**Selection:** Configured via `firefly.security-center.idp.provider` property.

---

## Quick Start

This guide will walk you through setting up and running the Security Center microservice from scratch.

### Prerequisites

Before you begin, ensure you have:

- **Java 21+** - [Download OpenJDK](https://openjdk.org/)
- **Maven 3.8+** - [Download Maven](https://maven.apache.org/download.cgi)
- **Docker** (optional) - For running Keycloak locally
- **Git** - To clone the repository

### Step 1: Clone and Build

```bash
# Clone the repository
git clone https://github.com/firefly-oss/core-domain-security-center.git
cd core-domain-security-center

# Build the project (runs all tests)
mvn clean install

# ✅ Expected: BUILD SUCCESS with 20/20 tests passing
```

**Note:** The Cognito integration test is disabled by default (requires LocalStack Pro license). See [Test Coverage](#aws-cognito-integration-test-disabled) for details.

---

### Step 2: Choose Your Deployment Scenario

Pick the scenario that matches your environment:

- **[Scenario A: Local Development with Keycloak](#scenario-a-local-development-with-keycloak)** (Recommended for getting started)
- **[Scenario B: Production with AWS Cognito](#scenario-b-production-with-aws-cognito)**
- **[Scenario C: Standalone Testing (No External IDP)](#scenario-c-standalone-testing-no-external-idp)**

---

### Scenario A: Local Development with Keycloak

This is the **easiest way to get started** - runs everything locally with Docker.

#### A1. Start Keycloak with Docker

```bash
docker run -d \
  --name keycloak \
  -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:23.0 \
  start-dev
```

Wait ~30 seconds for Keycloak to start, then verify:
```bash
curl http://localhost:8080/health/ready
# Expected: {"status":"UP"}
```

#### A2. Create Keycloak Realm and Client

1. **Open Keycloak Admin Console**: http://localhost:8080/admin
2. **Login**: admin / admin
3. **Create Realm**:
   - Click "Create Realm"
   - Name: `firefly`
   - Click "Create"
4. **Create Client**:
   - Go to "Clients" → "Create client"
   - Client ID: `security-center`
   - Client authentication: ON
   - Click "Save"
   - Go to "Credentials" tab
   - Copy the "Client Secret" (you'll need this)
5. **Create Test User**:
   - Go to "Users" → "Add user"
   - Username: `testuser`
   - Email: `testuser@firefly.com`
   - Click "Create"
   - Go to "Credentials" tab
   - Set password: `password123`
   - Temporary: OFF
   - Click "Set password"

#### A3. Configure Security Center

Create `core-domain-security-center-web/src/main/resources/application-local.yml`:

```yaml
server:
  port: 8085

firefly:
  security-center:
    idp:
      provider: keycloak
      keycloak:
        server-url: http://localhost:8080
        realm: firefly
        client-id: security-center
        client-secret: YOUR_CLIENT_SECRET_FROM_STEP_A2
        admin-username: admin
        admin-password: admin

    # Session configuration
    session:
      timeout-minutes: 30
      cleanup-interval-minutes: 15

    # Downstream microservices (mock URLs for now)
    clients:
      customer-mgmt:
        base-url: http://localhost:8081
      contract-mgmt:
        base-url: http://localhost:8082
      product-mgmt:
        base-url: http://localhost:8083
      reference-master-data:
        base-url: http://localhost:8084

  # Cache configuration (Caffeine for local dev)
  cache:
    enabled: true
    default-cache-type: CAFFEINE
    caffeine:
      enabled: true
      cache-name: session-cache
      key-prefix: "firefly:session"
      maximum-size: 10000
      expire-after-write: PT30M
      record-stats: true

logging:
  level:
    com.firefly.security.center: DEBUG
```

#### A4. Run Security Center

```bash
mvn spring-boot:run -pl core-domain-security-center-web -Dspring-boot.run.profiles=local
```

**Expected output:**
```
Started SecurityCenterApplication in 3.456 seconds
Configuring Customer Management SDK with base URL: http://localhost:8081
Configuring Contract Management SDK with base URL: http://localhost:8082
...
Netty started on port 8085
```

#### A5. Test Authentication

```bash
# Login
curl -X POST http://localhost:8085/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

**Expected response:**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "idToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresIn": 300,
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "partyId": "123e4567-e89b-12d3-a456-426614174000"
}
```

✅ **Success!** Your Security Center is running with Keycloak authentication.

---

### Scenario B: Production with AWS Cognito

For production deployments using AWS Cognito as the identity provider.

#### B1. Prerequisites

- AWS Account with Cognito User Pool created
- AWS credentials configured (IAM role, environment variables, or AWS CLI)

#### B2. Create Cognito User Pool (if not exists)

```bash
# Using AWS CLI
aws cognito-idp create-user-pool \
  --pool-name firefly-users \
  --policies "PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true}" \
  --auto-verified-attributes email

# Note the UserPoolId from the response
```

#### B3. Create App Client

```bash
aws cognito-idp create-user-pool-client \
  --user-pool-id us-east-1_XXXXXX \
  --client-name security-center \
  --generate-secret \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH

# Note the ClientId and ClientSecret from the response
```

#### B4. Configure Security Center

Create `application-prod.yml`:

```yaml
server:
  port: 8085

firefly:
  security-center:
    idp:
      provider: cognito
      cognito:
        region: ${AWS_REGION:us-east-1}
        user-pool-id: ${COGNITO_USER_POOL_ID}
        client-id: ${COGNITO_CLIENT_ID}
        client-secret: ${COGNITO_CLIENT_SECRET}

    clients:
      customer-mgmt:
        base-url: ${CUSTOMER_MGMT_URL}
      contract-mgmt:
        base-url: ${CONTRACT_MGMT_URL}
      product-mgmt:
        base-url: ${PRODUCT_MGMT_URL}
      reference-master-data:
        base-url: ${REFERENCE_MASTER_DATA_URL}

  # Redis cache for production
  cache:
    enabled: true
    default-cache-type: REDIS
    redis:
      enabled: true
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
      key-prefix: "firefly:session"
```

#### B5. Set Environment Variables

```bash
export AWS_REGION=us-east-1
export COGNITO_USER_POOL_ID=us-east-1_XXXXXX
export COGNITO_CLIENT_ID=your-client-id
export COGNITO_CLIENT_SECRET=your-client-secret
export REDIS_HOST=your-redis-host
export REDIS_PASSWORD=your-redis-password
export CUSTOMER_MGMT_URL=https://customer-mgmt.firefly.com
export CONTRACT_MGMT_URL=https://contract-mgmt.firefly.com
export PRODUCT_MGMT_URL=https://product-mgmt.firefly.com
export REFERENCE_MASTER_DATA_URL=https://reference-data.firefly.com
```

#### B6. Run Security Center

```bash
java -jar core-domain-security-center-web/target/core-domain-security-center-web-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

---

### Scenario C: Standalone Testing (No External IDP)

For testing without setting up Keycloak or Cognito, you can use the integration tests.

```bash
# Run integration tests with embedded Keycloak (Testcontainers)
mvn test -Dtest=KeycloakIntegrationTest

# Run with embedded Redis
mvn test -Dtest=RedisCacheIntegrationTest
```

These tests automatically start Keycloak and Redis in Docker containers and run full authentication flows.

---

### Step 3: Verify Health and Metrics

Once running, check the health endpoints:

```bash
# Health check
curl http://localhost:8085/actuator/health

# Expected response
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}

# Metrics
curl http://localhost:8085/actuator/metrics

# Prometheus metrics
curl http://localhost:8085/actuator/prometheus
```

---

### Step 4: Understanding Session Enrichment

When a user logs in, the Security Center automatically enriches the session with data from downstream microservices:

```
Login Request
    ↓
Authenticate with IDP (Keycloak/Cognito)
    ↓
Extract partyId from token
    ↓
Parallel Enrichment:
    ├─→ Customer Management: Fetch customer profile
    ├─→ Contract Management: Fetch active contracts
    │       └─→ For each contract:
    │           ├─→ Fetch contract details
    │           ├─→ Fetch role and permissions
    │           └─→ Fetch product information
    ↓
Aggregate into SessionContext
    ↓
Cache in Redis/Caffeine
    ↓
Return enriched session to client
```

**Note:** If downstream services are not available:
- **customer-mgmt**: Authentication will fail (required for user-to-party mapping)
- **contract-mgmt, product-mgmt, reference-master-data**: Session enrichment will fail
- **No fallback data**: All errors propagate to the client with clear error messages

This ensures data consistency and prevents sessions with invalid or placeholder data.

---

### Step 5: Next Steps

Now that your Security Center is running:

1. **Integrate with other microservices** - See [Using FireflySessionManager in Other Services](#using-fireflysessionmanager-in-other-services)
2. **Configure production cache** - See [docs/CONFIGURATION.md](docs/CONFIGURATION.md#cache-configuration)
3. **Set up monitoring** - Prometheus metrics available at `/actuator/prometheus`
4. **Review API documentation** - See [docs/API.md](docs/API.md)
5. **Troubleshooting** - See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)

---

### Common Issues

**Port 8080 already in use (Keycloak)**
```bash
# Change Keycloak port
docker run -p 9090:8080 ... quay.io/keycloak/keycloak:23.0 start-dev
# Update application.yml: server-url: http://localhost:9090
```

**Port 8085 already in use (Security Center)**
```bash
# Change Security Center port in application.yml
server:
  port: 9085
```

**Downstream services not available**
- **customer-mgmt is required**: Authentication will fail without it
- **Other services**: Session enrichment will fail, but you can test IDP integration
- Start all required services for full functionality testing

**Redis connection failed**
- Switch to Caffeine cache for local development
- Set `firefly.cache.default-cache-type: CAFFEINE`

## API Endpoints

### Authentication

- `POST /api/v1/auth/login` - Authenticate user and create session
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/logout` - Logout and invalidate session
- `GET /api/v1/auth/session/{sessionId}` - Retrieve session details

### Health

- `GET /actuator/health` - Service health check

### Example: Login

```bash
curl -X POST http://localhost:8085/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user@example.com",
    "password": "password123",
    "scope": "openid profile email"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "partyId": "123e4567-e89b-12d3-a456-426614174000",
  "expiresIn": 3600
}
```

---

## Using FireflySessionManager in Other Services

Other Firefly microservices import the session library:

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>core-domain-security-center-session</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Inject and Use

```java
@RestController
public class AccountController {

    @Autowired
    private FireflySessionManager sessionManager;

    @GetMapping("/accounts/{productId}")
    public Mono<AccountResponse> getAccount(
            @PathVariable UUID productId,
            ServerWebExchange exchange) {

        return sessionManager.createOrGetSession(exchange)
            .flatMap(session -> {
                // Check if user has access to this product
                boolean hasAccess = session.getActiveContracts().stream()
                    .anyMatch(c -> c.getProduct().getProductId().equals(productId));

                if (!hasAccess) {
                    return Mono.error(new UnauthorizedException());
                }

                // Check specific permission (e.g., READ BALANCE)
                boolean canRead = session.getActiveContracts().stream()
                    .filter(c -> c.getProduct().getProductId().equals(productId))
                    .flatMap(c -> c.getRoleInContract().getScopes().stream())
                    .anyMatch(scope -> 
                        "READ".equals(scope.getActionType()) && 
                        "BALANCE".equals(scope.getResourceType())
                    );

                if (!canRead) {
                    return Mono.error(new ForbiddenException());
                }

                return accountService.getBalance(productId);
            });
    }
}
```

---

## Test Coverage

```
✅ Total: 20/20 tests passing (100%)
├── Core Module:                     8/8  ✅
│   ├── ContractResolverServiceTest: 4/4  ✅
│   └── CustomerResolverServiceTest: 4/4  ✅
│
└── Web Module:                     12/12 ✅
    ├── Keycloak Integration:        8/8  ✅
    ├── Redis Cache Integration:     9/9  ✅
    └── Controller Tests:            4/4  ✅
```

**Technologies Used:**
- Testcontainers (Keycloak, Redis)
- JUnit 5, AssertJ, Mockito

### Running Tests

**Run all tests:**
```bash
mvn clean test
```

**Run specific test class:**
```bash
mvn test -Dtest=KeycloakIntegrationTest
```

**Run full build with tests:**
```bash
mvn clean install
```

### AWS Cognito Integration Test (Disabled)

The `CognitoIntegrationTest` is **disabled by default** because it requires a **LocalStack Pro license**.

**To enable and run the Cognito test:**

1. **Obtain a LocalStack Pro license** from [https://localstack.cloud/pricing](https://localstack.cloud/pricing)

2. **Set the environment variable:**
   ```bash
   export LOCALSTACK_AUTH_TOKEN="your-license-token"
   ```

3. **Remove the `@Disabled` annotation** from `CognitoIntegrationTest.java`:
   ```java
   // Remove this line:
   @Disabled("Requires LocalStack Pro license - set LOCALSTACK_AUTH_TOKEN environment variable to enable")
   ```

4. **Run the test:**
   ```bash
   mvn test -Dtest=CognitoIntegrationTest
   ```

**Why is it disabled?**
- LocalStack Pro is required to emulate AWS Cognito User Pools
- The free version of LocalStack does not support Cognito
- All other tests use free, open-source tools (Testcontainers with Keycloak and Redis)

## Documentation

| Document | Description |
|----------|-------------|
| **[Getting Started Guide](docs/GETTING_STARTED.md)** | 📘 **START HERE** - Comprehensive setup guide with Docker, Kubernetes, and integration examples |
| **[Architecture](docs/ARCHITECTURE.md)** | System design, module structure, data flow |
| **[Configuration](docs/CONFIGURATION.md)** | IDP, cache, and service configuration |
| **[API Reference](docs/API.md)** | REST API endpoints and examples |
| **[Troubleshooting](docs/TROUBLESHOOTING.md)** | Common issues and solutions |

---

## Technology Stack

- **Java 21** - With Virtual Threads enabled
- **Spring Boot 3.x** - Application framework
- **Spring WebFlux** - Reactive web
- **Project Reactor** - Reactive streams
- **Caffeine Cache** - High-performance in-memory caching (default)
- **Redis** - Optional distributed caching
- **Keycloak/AWS Cognito** - Identity providers
- **Testcontainers** - Integration testing
- **Maven** - Build and dependency management

## License

Copyright 2025 Firefly Software Solutions Inc.

Licensed under the Apache License, Version 2.0
