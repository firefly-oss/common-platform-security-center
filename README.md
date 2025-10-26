# Firefly Security Center - Session Management Microservice

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

**Central session management and security orchestration for the Firefly Core Banking Platform**

## Overview

The Firefly Security Center is a critical microservice that provides **centralized session management** and **security context aggregation** for the entire Firefly Core Banking Platform. It implements the `FireflySessionManager` interface which other microservices import to access customer sessions, contracts, roles, and permissions.

### Key Responsibilities

- **Session Lifecycle Management**: Create, retrieve, refresh, and invalidate customer sessions
- **Context Aggregation**: Aggregate data from customer-mgmt, contract-mgmt, product-mgmt, and reference-master-data
- **Authorization Support**: Provide complete context for role-based access control decisions
- **Performance Optimization**: Intelligent caching strategy (Caffeine/Redis)

## Architecture

### Module Structure

```
common-platform-security-center/
├── common-platform-security-center-interfaces/   # DTOs and data contracts
├── common-platform-security-center-session/      # ★ EXPORTABLE LIBRARY ★
│   └── FireflySessionManager interface
├── common-platform-security-center-core/         # Business logic implementation
├── common-platform-security-center-web/          # REST API and Spring Boot app
└── common-platform-security-center-sdk/          # Client SDK for other services
```

### Data Flow

```
1. Extract partyId from X-Party-Id header
2. Fetch customer info from customer-mgmt
3. Fetch all active contracts for partyId from contract-mgmt
4. For each contract:
   a. Fetch role details from reference-master-data
   b. Fetch role scopes (permissions) from reference-master-data
   c. Fetch product info from product-mgmt
5. Aggregate into SessionContext
6. Cache and return
```

### Entity Relationships

```
Party → ContractParty → Contract → Product
                ↓
          ContractRole → RoleScopes (permissions)
```

## FireflySessionManager - Exportable Library

Other microservices import `common-platform-security-center-session`:

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>common-platform-security-center-session</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Usage Example

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
                // Validate access via contracts
                boolean hasAccess = session.getActiveContracts().stream()
                    .anyMatch(c -> c.getProduct().getProductId().equals(productId));

                if (!hasAccess) {
                    return Mono.error(new UnauthorizedException());
                }

                // Check specific permission
                boolean canRead = session.getActiveContracts().stream()
                    .filter(c -> c.getProduct().getProductId().equals(productId))
                    .flatMap(c -> c.getRoleInContract().getScopes().stream())
                    .anyMatch(s -> "READ".equals(s.getActionType()) && 
                                   "BALANCE".equals(s.getResourceType()));

                return accountService.getBalance(productId);
            });
    }
}
```

## Implementation Status

### ✅ Phase 1: Completed
- Root POM with module structure
- `-interfaces` module with all DTOs (SessionContextDTO, ContractInfoDTO, RoleInfoDTO, etc.)
- `-session` module with FireflySessionManager interface

### 🚧 Phase 2: TODO - Core Implementation
Create `-core` module with:
- `SessionAggregationService` - Orchestrates aggregation
- `ContractResolverService` - Fetches contracts and resolves roles/products
- `RoleResolverService` - Fetches role and scope information
- `CustomerResolverService` - Fetches customer/party information  
- `FireflySessionManagerImpl` - Main implementation with caching
- HTTP clients for downstream services

### 🚧 Phase 3: TODO - REST API
Create `-web` module with:
- REST controllers for session endpoints
- Spring Boot application
- Configuration properties

### 🚧 Phase 4: TODO - SDK
Create `-sdk` module with OpenAPI spec

## Configuration

```yaml
firefly:
  security-center:
    session:
      timeout-minutes: 30
      cache:
        type: caffeine
        caffeine:
          maximum-size: 10000
          expire-after-write-minutes: 30

    clients:
      customer-mgmt:
        base-url: ${CUSTOMER_MGMT_URL:http://localhost:8081}
      contract-mgmt:
        base-url: ${CONTRACT_MGMT_URL:http://localhost:8082}
      product-mgmt:
        base-url: ${PRODUCT_MGMT_URL:http://localhost:8083}
      reference-master-data:
        base-url: ${REFERENCE_DATA_URL:http://localhost:8084}
```

## Next Steps

1. Implement `-core` module service implementations
2. Implement `-web` module REST API
3. Generate `-sdk` module
4. Add comprehensive tests
5. Add Docker/Kubernetes deployment configs

## License

Copyright 2025 Firefly Software Solutions Inc. Licensed under the Apache License 2.0.
