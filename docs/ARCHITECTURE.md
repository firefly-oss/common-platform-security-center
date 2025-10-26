# Security Center - Architecture

## Overview

The Firefly Security Center provides centralized session management and security orchestration for the Firefly Core Banking Platform using a modular, reactive architecture.

## Module Structure

```
common-platform-security-center/
├── common-platform-security-center-interfaces/   # DTOs and contracts
├── common-platform-security-center-session/      # ★ Exportable library
├── common-platform-security-center-core/         # Business logic
├── common-platform-security-center-web/          # REST API
└── common-platform-security-center-sdk/          # Client SDK
```

### Module Responsibilities

#### 1. Interfaces Module
- Data Transfer Objects (DTOs)
- Shared contracts between modules
- No dependencies on other modules

**Key Classes:**
- `SessionContextDTO` - Complete session information
- `ContractInfoDTO` - Contract and role details
- `CustomerInfoDTO` - Customer/party information
- `ProductInfoDTO` - Product details
- `RoleInfoDTO` - Role and permission scopes

#### 2. Session Module ★ **Exportable**
- `FireflySessionManager` interface
- Imported by other microservices
- Minimal dependencies

**Usage by other services:**
```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>common-platform-security-center-session</artifactId>
</dependency>
```

#### 3. Core Module
- Business logic implementation
- IDP adapter integration
- Session enrichment services
- Caching layer integration

**Key Components:**
- `AuthenticationService` - Authentication orchestration
- `FireflySessionManagerImpl` - Session management implementation
- `ContractResolverService` - Contract resolution
- `CustomerResolverService` - Customer data resolution
- `ProductResolverService` - Product information resolution
- `IdpAutoConfiguration` - IDP provider selection

#### 4. Web Module
- REST API with Spring WebFlux
- Authentication controller
- Integration tests
- Spring Boot application

**Endpoints:**
- `POST /api/v1/auth/login` - User authentication
- `POST /api/v1/auth/refresh` - Token refresh
- `POST /api/v1/auth/logout` - User logout
- `GET /api/v1/auth/session/{sessionId}` - Retrieve session
- `GET /actuator/health` - Health checks

#### 5. SDK Module
- Client SDK for downstream services
- Reactive WebClient-based
- Type-safe API clients

**Supported Services:**
- Customer Management
- Contract Management
- Product Management
- Reference Master Data

## IDP Integration Architecture

### Adapter Pattern

The Security Center uses a pluggable adapter pattern for Identity Provider integration:

```
┌─────────────────────────────────────┐
│   AuthenticationService             │
│   (Core Business Logic)             │
└──────────────┬──────────────────────┘
               │
               │ uses
               ▼
┌─────────────────────────────────────┐
│   IdpAdapter Interface              │
│   • authenticate()                  │
│   • refreshToken()                  │
│   • logout()                        │
│   • getUserInfo()                   │
└──────────────┬──────────────────────┘
               │
       ┌───────┴───────┐
       ▼               ▼
┌──────────────┐  ┌──────────────┐
│ Keycloak     │  │ AWS Cognito  │
│ Adapter      │  │ Adapter      │
└──────────────┘  └──────────────┘
```

### IDP Selection

Configured via `firefly.security-center.idp.provider`:

```yaml
firefly:
  security-center:
    idp:
      provider: keycloak  # or "cognito"
```

The `IdpAutoConfiguration` class conditionally loads the correct adapter bean based on this property.

## Session Enrichment Flow

```
1. User authenticates via IDP
   └─> Returns access token, refresh token, ID token

2. Map IDP user to partyId:
   ├─> Call UserMappingService.mapToPartyId()
   ├─> Try email lookup via EmailContactsApi (customer-mgmt)
   ├─> If not found, try username lookup via PartiesApi (customer-mgmt)
   └─> If not found, throw IllegalStateException (authentication fails)

3. Parallel enrichment:
   ├─> Fetch customer info (customer-mgmt)
   ├─> Fetch active contracts (contract-mgmt)
   └─> For each contract:
       ├─> Fetch role details (reference-master-data)
       ├─> Fetch role scopes/permissions (reference-master-data)
       └─> Fetch product info (product-mgmt)

4. Aggregate into SessionContext

5. Cache session (Redis/Caffeine)

6. Return enriched session
```

### IDP User to Party Mapping

The Security Center maps IDP users to Firefly partyIds using the `UserMappingService`:

```
┌─────────────────────────────────────┐
│   AuthenticationService             │
└──────────────┬──────────────────────┘
               │
               │ uses
               ▼
┌─────────────────────────────────────┐
│   UserMappingService                │
│   (DefaultUserMappingService)       │
└──────────────┬──────────────────────┘
               │
       ┌───────┴───────┐
       ▼               ▼
┌──────────────┐  ┌──────────────┐
│ PartiesApi   │  │EmailContacts │
│ (username)   │  │Api (email)   │
└──────────────┘  └──────────────┘
```

**Mapping Strategy:**

1. **Email Lookup** (Primary)
   - Searches all parties' email contacts using `EmailContactsApi`
   - Filters by email address from IDP user info
   - Returns partyId if found

2. **Username Lookup** (Secondary)
   - Searches parties by `sourceSystem` field using `PartiesApi`
   - Format: `"idp:username"` (e.g., `"idp:john.doe"`)
   - Returns partyId if found

3. **Error Handling** (No Fallbacks)
   - If both lookups fail, throws `IllegalStateException`
   - Authentication fails with clear error message
   - **Important**: Parties MUST exist in customer-mgmt before authentication

**Customization:**

To implement custom mapping logic (e.g., auto-provisioning), create a custom `UserMappingService`:

```java
@Service
public class CustomUserMappingService implements UserMappingService {
    @Override
    public Mono<UUID> mapToPartyId(UserInfoResponse userInfo, String username) {
        // Custom logic: auto-provision party if not found
        return findExistingParty(userInfo)
            .switchIfEmpty(createNewParty(userInfo));
    }
}
```

### Data Model

```
SessionContext
├── sessionId: String
├── partyId: UUID
├── customerInfo: CustomerInfo
│   ├── partyId: UUID
│   ├── partyKind: String (NATURAL_PERSON or LEGAL_ENTITY)
│   ├── tenantId: UUID
│   ├── fullName: String
│   ├── preferredLanguage: String
│   ├── email: String
│   ├── phoneNumber: String
│   ├── taxIdNumber: String
│   └── isActive: Boolean
├── activeContracts: List<ContractInfo>
│   └── ContractInfo
│       ├── contractId: UUID
│       ├── contractNumber: String
│       ├── contractStatus: String
│       ├── startDate: LocalDateTime
│       ├── endDate: LocalDateTime
│       ├── contractPartyId: UUID
│       ├── product: ProductInfo
│       │   ├── productId: UUID
│       │   ├── productName: String
│       │   ├── productType: String
│       │   ├── description: String
│       │   └── isActive: Boolean
│       ├── roleInContract: RoleInfo
│       │   ├── roleId: UUID
│       │   ├── roleCode: String
│       │   ├── name: String
│       │   ├── description: String
│       │   ├── isActive: Boolean
│       │   ├── scopes: List<RoleScopeInfo>
│       │   │   └── RoleScopeInfo
│       │   │       ├── scopeId: UUID
│       │   │       ├── roleId: UUID
│       │   │       ├── scopeCode: String
│       │   │       ├── scopeName: String
│       │   │       ├── description: String
│       │   │       ├── actionType: String (READ, WRITE, DELETE, EXECUTE, APPROVE)
│       │   │       ├── resourceType: String (PRODUCT, TRANSACTION, ACCOUNT, BALANCE)
│       │   │       └── isActive: Boolean
│       │   ├── dateCreated: LocalDateTime
│       │   └── dateUpdated: LocalDateTime
│       ├── dateJoined: LocalDateTime
│       ├── dateLeft: LocalDateTime
│       ├── isActive: Boolean
│       ├── createdAt: LocalDateTime
│       └── updatedAt: LocalDateTime
├── createdAt: LocalDateTime
├── lastAccessedAt: LocalDateTime
├── expiresAt: LocalDateTime
├── ipAddress: String
├── userAgent: String
├── status: SessionStatus (ACTIVE, EXPIRED, INVALIDATED, LOCKED)
└── metadata: SessionMetadata
    ├── channel: String (web, mobile, api)
    ├── sourceApplication: String
    ├── deviceInfo: String
    └── geolocation: String
```

## Cache Architecture

### Cache Abstraction

Uses `lib-common-cache` for cache backend abstraction:

```
┌─────────────────────────────────────┐
│   FireflySessionManagerImpl         │
└──────────────┬──────────────────────┘
               │
               │ uses
               ▼
┌─────────────────────────────────────┐
│   CacheManager                      │
│   (lib-common-cache)                │
└──────────────┬──────────────────────┘
               │
       ┌───────┴───────┐
       ▼               ▼
┌──────────────┐  ┌──────────────┐
│ Caffeine     │  │ Redis        │
│ (Default)    │  │ (Optional)   │
└──────────────┘  └──────────────┘
```

### Cache Configuration

**Caffeine (Default):**
```yaml
firefly:
  cache:
    default-cache-type: CAFFEINE
    caffeine:
      enabled: true
      max-size: 10000
      expire-after-write-minutes: 30
```

**Redis (Optional - for distributed deployments):**
```yaml
firefly:
  cache:
    default-cache-type: REDIS
    redis:
      enabled: true
      host: localhost
      port: 6379
      key-prefix: "firefly:session"
    caffeine:
      enabled: true
```

### Session TTL

- Sessions are cached with TTL matching token expiration
- Automatic eviction on logout
- Refresh extends TTL

## Technology Stack

### Core Framework
- **Spring Boot 3.x** - Application framework
- **Spring WebFlux** - Reactive web framework
- **Project Reactor** - Reactive streams
- **Spring Data Redis** - Redis integration

### IDP Integration
- **lib-idp-keycloak-impl** - Keycloak adapter
- **lib-idp-aws-cognito-impl** - AWS Cognito adapter
- **AWS SDK for Java 2.x** - AWS service integration
- **Keycloak Admin Client** - Keycloak API client

### Caching
- **lib-common-cache** - Firefly's cache abstraction
- **Lettuce** - Redis client
- **Caffeine** - In-memory cache

### Testing
- **JUnit 5** - Testing framework
- **Testcontainers** - Container-based integration tests
- **LocalStack** - AWS service mocking
- **AssertJ** - Fluent assertions
- **Mockito** - Mocking framework

## Reactive Architecture

The entire stack is built on reactive principles using Project Reactor:

```java
// Example: Session creation flow
public Mono<SessionContext> createSession(String username, String password) {
    return idpAdapter.authenticate(username, password)           // Mono<AuthResponse>
        .flatMap(auth -> enrichSession(auth))                    // Mono<SessionContext>
        .flatMap(session -> cacheSession(session))               // Mono<SessionContext>
        .doOnError(error -> log.error("Auth failed", error));    // Error handling
}

private Mono<SessionContext> enrichSession(AuthResponse auth) {
    return Mono.zip(
        customerService.getCustomer(auth.getPartyId()),          // Parallel
        contractService.getContracts(auth.getPartyId())          // execution
    ).map(tuple -> buildSession(auth, tuple.getT1(), tuple.getT2()));
}
```

### Benefits
- Non-blocking I/O
- High concurrency
- Backpressure support
- Composable operations

## Security Considerations

### Token Security
- Tokens stored encrypted in Redis (configurable TLS)
- No tokens in application logs
- Secure token transmission (HTTPS in production)

### Authentication Flow
1. User credentials sent to IDP (not stored)
2. IDP validates and returns tokens
3. Tokens cached with TTL
4. Access token used for API authorization

### Authorization
- Fine-grained permissions via role scopes
- Contract-based access control
- Resource and action-level permissions

## Scalability

### Horizontal Scaling
- Stateless application instances
- Shared Redis cache for sessions
- Load balancer compatible

### Performance Optimizations
- Reactive, non-blocking architecture
- Parallel session enrichment
- Redis connection pooling
- Efficient caching strategy

## Monitoring and Observability

### Health Checks
- Spring Boot Actuator health endpoint
- IDP connectivity check
- Cache backend health
- Downstream service health

### Metrics (Available)
- Session creation rate
- Authentication success/failure
- Cache hit/miss ratio
- API response times

### Logging
- Structured logging with context
- Request correlation IDs
- Performance tracking
- Error tracking with stack traces

## Extension Points

### Adding New IDP Providers

1. Implement `IdpAdapter` interface
2. Create configuration properties class
3. Add conditional bean configuration
4. Update `firefly.security-center.idp.provider` enum

Example structure:
```java
@Component
@ConditionalOnProperty(name = "firefly.security-center.idp.provider", 
                       havingValue = "okta")
public class OktaIdpAdapter implements IdpAdapter {
    // Implementation
}
```

### Adding New Enrichment Services

Extend session enrichment by adding new resolver services:

```java
@Service
public class AccountResolverService {
    public Mono<List<AccountInfo>> resolveAccounts(UUID partyId) {
        // Fetch account data
    }
}
```

Update `FireflySessionManagerImpl` to include new data.

## Best Practices

### For Developers
1. Follow reactive patterns (avoid blocking calls)
2. Use proper error handling with `onErrorResume`
3. Implement proper backpressure
4. Cache appropriately
5. Write integration tests with Testcontainers

### For Operations
1. Monitor cache hit rates
2. Set up proper Redis clustering for HA
3. Configure TLS for Redis in production
4. Use HTTPS for all IDP communication
5. Set up proper log aggregation
6. Configure health check endpoints in load balancer

### For Security
1. Rotate IDP client secrets regularly
2. Use strong Redis passwords
3. Enable Redis TLS in production
4. Monitor failed authentication attempts
5. Set appropriate token TTLs
6. Implement rate limiting on auth endpoints
