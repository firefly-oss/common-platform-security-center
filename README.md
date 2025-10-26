# Firefly Security Center

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/Tests-43%2F43%20Passing-brightgreen.svg)](#test-coverage)

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
- **⚡ High Performance** - Redis-backed caching with intelligent TTL management
- **🔄 Reactive Architecture** - Non-blocking, built on Spring WebFlux and Project Reactor
- **🔌 Exportable Library** - Other services import `FireflySessionManager` for session access
- **✅ Production Ready** - 100% test coverage with Testcontainers

### Who Uses It

**All Firefly microservices** depend on the Security Center for:
- User authentication
- Authorization decisions (contract-based access control)
- Customer and product context
- Role and permission resolution

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
│    (common-platform-security-center-session)            │
│       FireflySessionManager Interface                   │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│        Security Center - Core Business Logic            │
│    (common-platform-security-center-core)               │
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
common-platform-security-center/
├── common-platform-security-center-interfaces/
│   └── DTOs and data contracts
│
├── common-platform-security-center-session/      ⭐ EXPORTABLE
│   └── FireflySessionManager interface
│       (Imported by all other microservices)
│
├── common-platform-security-center-core/
│   ├── AuthenticationService
│   ├── Session Enrichment (Customer, Contract, Product)
│   ├── IDP Adapters (Keycloak, Cognito)
│   └── Caching Integration
│
├── common-platform-security-center-web/
│   ├── REST API Controllers
│   ├── Spring Boot Application
│   └── Integration Tests
│
└── common-platform-security-center-sdk/
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

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker (for integration tests)
- LocalStack PRO token (for Cognito tests)

### 1. Build and Test

```bash
cd common-platform-security-center

# Set LocalStack token for Cognito tests
export LOCALSTACK_AUTH_TOKEN="your-token"

# Build and run all tests
mvn clean install

# ✅ Result: BUILD SUCCESS, 43/43 tests passing
```

### 2. Configure Identity Provider

Choose **Keycloak** or **AWS Cognito** as your identity provider.

#### Option A: Keycloak (Recommended for Development)

Create `application.yml`:

```yaml
firefly:
  security-center:
    idp:
      provider: keycloak

keycloak:
  server-url: http://localhost:8080
  realm: your-realm
  client-id: your-client
  client-secret: your-secret
```

#### Option B: AWS Cognito (Production)

Create `application.yml`:

```yaml
firefly:
  security-center:
    idp:
      provider: cognito
      cognito:
        region: us-east-1
        user-pool-id: us-east-1_XXXXXX
        client-id: your-client-id
        client-secret: your-client-secret  # Optional
```

### 3. Configure Cache

#### Redis (Production)

```yaml
firefly:
  cache:
    default-cache-type: REDIS
    redis:
      enabled: true
      host: localhost
      port: 6379
      password: your-redis-password  # Optional
```

#### Caffeine (Development/Testing)

```yaml
firefly:
  cache:
    default-cache-type: CAFFEINE
    caffeine:
      enabled: true
```

### 4. Configure Downstream Services

```yaml
firefly:
  security-center:
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

### 5. Run the Application

```bash
mvn spring-boot:run -pl common-platform-security-center-web
```

The service will start on `http://localhost:8080`

### 6. Test Authentication

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user@example.com",
    "password": "password123"
  }'
```

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
curl -X POST http://localhost:8080/api/v1/auth/login \
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
    <artifactId>common-platform-security-center-session</artifactId>
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
✅ Total: 43/43 tests passing (100%)
├── Core Module:                    17/17 ✅
│   ├── ContractResolverService:      8/8  ✅
│   └── CustomerResolverService:      9/9  ✅
│
└── Web Module:                     26/26 ✅
    ├── Cognito Integration:         6/6  ✅
    ├── Keycloak Integration:        7/7  ✅
    ├── Redis Cache Integration:     9/9  ✅
    └── Controller Tests:            4/4  ✅
```

**Technologies Used:**
- Testcontainers (Keycloak, Redis)
- LocalStack PRO (AWS Cognito)
- JUnit 5, AssertJ, Mockito

## Documentation

| Document | Description |
|----------|-------------|
| **[Architecture](docs/ARCHITECTURE.md)** | System design, module structure, data flow |
| **[Configuration](docs/CONFIGURATION.md)** | IDP, cache, and service configuration |
| **[API Reference](docs/API.md)** | REST API endpoints and examples |
| **[Troubleshooting](docs/TROUBLESHOOTING.md)** | Common issues and solutions |

---

## Technology Stack

- **Spring Boot 3.x** - Application framework
- **Spring WebFlux** - Reactive web
- **Project Reactor** - Reactive streams
- **Redis/Caffeine** - Session caching
- **Keycloak/Cognito** - Identity providers
- **Testcontainers** - Integration testing

## License

Copyright 2025 Firefly Software Solutions Inc.

Licensed under the Apache License, Version 2.0
