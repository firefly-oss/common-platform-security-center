# Security Center Integration Guide

## Overview

This guide explains how to integrate the Security Center microservice into the Firefly platform and use it from other microservices.

## Architecture Integration

### Data Flow
```
Client Request (with X-Party-Id) 
    ↓
Security Center
    ├── Check FireflyCacheManager (lib-common-cache)
    ├── If not cached, aggregate from:
    │   ├── customer-mgmt SDK → Customer info
    │   ├── contract-mgmt SDK → Contracts for partyId
    │   ├── reference-master-data SDK → Roles & Scopes
    │   └── product-mgmt SDK → Product info
    ├── Build SessionContextDTO
    ├── Cache with TTL (30 min default)
    └── Return complete session
```

## Module Dependencies

### 1. Security Center Modules

```xml
<!-- Root POM -->
<modules>
    <module>common-platform-security-center-interfaces</module>
    <module>common-platform-security-center-session</module>  <!-- ★ Exportable -->
    <module>common-platform-security-center-core</module>
    <module>common-platform-security-center-web</module>
    <module>common-platform-security-center-sdk</module>
</modules>
```

### 2. External Dependencies

**Core Module Dependencies:**
- `lib-common-cache` (Firefly cache library)
- `common-platform-customer-mgmt-sdk`
- `common-platform-contract-mgmt-sdk`
- `common-platform-product-mgmt-sdk`
- `common-platform-reference-master-data-sdk`
- Spring WebFlux

**Session Module (Exportable):**
- `common-platform-security-center-interfaces`
- `lib-common-cache`
- Spring WebFlux

## Using Security Center from Other Microservices

### Step 1: Add Dependency

Add the `-session` module to your microservice POM:

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>common-platform-security-center-session</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Step 2: Inject FireflySessionManager

```java
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    @Autowired
    private FireflySessionManager sessionManager;
    
    @Autowired
    private AccountService accountService;
    
    // Your endpoints here
}
```

### Step 3: Use in Controller Methods

```java
@GetMapping("/{productId}/balance")
public Mono<BalanceResponse> getBalance(
        @PathVariable UUID productId,
        ServerWebExchange exchange) {
    
    return sessionManager.createOrGetSession(exchange)
        .flatMap(session -> {
            // 1. Check if party has access to this product
            boolean hasAccess = session.getActiveContracts().stream()
                .anyMatch(contract -> 
                    contract.getProduct().getProductId().equals(productId) &&
                    Boolean.TRUE.equals(contract.getIsActive()));
            
            if (!hasAccess) {
                return Mono.error(new UnauthorizedException(
                    "No active contract found for product: " + productId));
            }
            
            // 2. Check specific permission (READ permission on BALANCE resource)
            boolean canRead = session.getActiveContracts().stream()
                .filter(c -> productId.equals(c.getProduct().getProductId()))
                .flatMap(c -> c.getRoleInContract().getScopes().stream())
                .anyMatch(scope -> 
                    "READ".equalsIgnoreCase(scope.getActionType()) &&
                    "BALANCE".equalsIgnoreCase(scope.getResourceType()) &&
                    Boolean.TRUE.equals(scope.getIsActive()));
            
            if (!canRead) {
                return Mono.error(new ForbiddenException(
                    "Insufficient permissions: READ access to BALANCE required"));
            }
            
            // 3. Proceed with business logic
            return accountService.getBalance(productId, session);
        });
}
```

### Step 4: Use Helper Methods

```java
// Simple access check
@GetMapping("/{productId}")
public Mono<AccountDetails> getAccount(
        @PathVariable UUID productId,
        @RequestParam UUID partyId) {
    
    return sessionManager.hasAccessToProduct(partyId, productId)
        .flatMap(hasAccess -> {
            if (!hasAccess) {
                return Mono.error(new UnauthorizedException());
            }
            return accountService.getAccountDetails(productId);
        });
}

// Permission check
@PostMapping("/{productId}/transfer")
public Mono<TransferResponse> transfer(
        @PathVariable UUID productId,
        @RequestBody TransferRequest request) {
    
    return sessionManager.hasPermission(
            request.getPartyId(), 
            productId, 
            "WRITE",  // actionType
            "TRANSACTION"  // resourceType
        )
        .flatMap(hasPermission -> {
            if (!hasPermission) {
                return Mono.error(new ForbiddenException(
                    "Insufficient permissions for transfer"));
            }
            return transferService.executeTransfer(request);
        });
}
```

## Cache Management

### Cache Strategy

The Security Center uses **lib-common-cache** with `FireflyCacheManager`:

- **Cache Key Format**: `firefly:session:session_{partyId}_{timestamp}`
- **Default TTL**: 30 minutes
- **Cache Provider**: Caffeine (in-memory) by default
- **Optional**: Redis for distributed caching

### Cache Operations

```java
@Autowired
private FireflySessionManager sessionManager;

// Invalidate specific session
public Mono<Void> logout(String sessionId) {
    return sessionManager.invalidateSession(sessionId);
}

// Invalidate all sessions for a party
public Mono<Void> invalidateAllUserSessions(UUID partyId) {
    return sessionManager.invalidateSessionsByPartyId(partyId);
}

// Refresh session (evict + reload)
public Mono<SessionContextDTO> refreshUserSession(String sessionId) {
    return sessionManager.refreshSession(sessionId);
}
```

### Configuring Cache

**application.yml:**

```yaml
firefly:
  cache:
    enabled: true
    default-cache-type: CAFFEINE  # or REDIS or AUTO
    
    caffeine:
      cache-name: session-cache
      enabled: true
      key-prefix: "firefly:session"
      maximum-size: 10000
      expire-after-write: PT30M  # 30 minutes
      record-stats: true
    
    # Optional: Enable Redis for distributed caching
    redis:
      enabled: false
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      key-prefix: "firefly:session"
```

## REST API Endpoints

### Security Center Endpoints

```bash
# Create/Get session
POST /api/v1/sessions
Headers: X-Party-Id: {uuid}

# Get by session ID
GET /api/v1/sessions/{sessionId}

# Get by party ID
GET /api/v1/sessions/party/{partyId}

# Invalidate session
DELETE /api/v1/sessions/{sessionId}

# Invalidate all for party
DELETE /api/v1/sessions/party/{partyId}

# Refresh session
POST /api/v1/sessions/{sessionId}/refresh

# Validate session
GET /api/v1/sessions/{sessionId}/validate

# Check product access
GET /api/v1/sessions/access-check?partyId={uuid}&productId={uuid}

# Check specific permission
GET /api/v1/sessions/permission-check?partyId={uuid}&productId={uuid}&actionType=READ&resourceType=BALANCE
```

## Environment Configuration

### Required Environment Variables

```bash
# Downstream service URLs
export CUSTOMER_MGMT_URL=http://customer-mgmt:8081
export CONTRACT_MGMT_URL=http://contract-mgmt:8082
export PRODUCT_MGMT_URL=http://product-mgmt:8083
export REFERENCE_MASTER_DATA_URL=http://reference-master-data:8084

# Optional: Redis for distributed caching
export REDIS_HOST=redis
export REDIS_PORT=6379
export REDIS_PASSWORD=your-password
```

### Docker Compose Example

```yaml
version: '3.8'

services:
  security-center:
    image: firefly/security-center:latest
    ports:
      - "8085:8085"
    environment:
      - CUSTOMER_MGMT_URL=http://customer-mgmt:8081
      - CONTRACT_MGMT_URL=http://contract-mgmt:8082
      - PRODUCT_MGMT_URL=http://product-mgmt:8083
      - REFERENCE_MASTER_DATA_URL=http://reference-master-data:8084
      - REDIS_HOST=redis
      - REDIS_PORT=6379
    depends_on:
      - customer-mgmt
      - contract-mgmt
      - product-mgmt
      - reference-master-data
      - redis
    networks:
      - firefly-network

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    networks:
      - firefly-network

networks:
  firefly-network:
    driver: bridge
```

## Performance Considerations

### Cache Hit Rates

- **First request**: Cache miss - aggregates from 4 services (~200-500ms)
- **Subsequent requests**: Cache hit - returns immediately (~1-5ms)
- **Cache TTL**: 30 minutes (configurable)

### Optimization Tips

1. **Use Redis for distributed caching** in multi-instance deployments
2. **Monitor cache hit rates** via actuator metrics
3. **Adjust TTL** based on your use case
4. **Pre-warm cache** for frequently accessed parties
5. **Consider WebClient connection pooling** for downstream services

## Monitoring

### Health Checks

```bash
# Overall health
GET /actuator/health

# Cache health
GET /actuator/health/cache
```

### Metrics

```bash
# Prometheus metrics
GET /actuator/prometheus

# Cache statistics
GET /actuator/metrics/cache.size
GET /actuator/metrics/cache.gets
GET /actuator/metrics/cache.puts
GET /actuator/metrics/cache.evictions
```

## Testing

### Unit Testing

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private FireflySessionManager sessionManager;
    
    @Mock
    private AccountService accountService;
    
    @InjectMocks
    private AccountController controller;
    
    @Test
    void shouldGetBalance_whenHasPermission() {
        // Setup mock session
        SessionContextDTO session = createMockSession();
        when(sessionManager.createOrGetSession(any()))
            .thenReturn(Mono.just(session));
        
        // Test your logic
        StepVerifier.create(controller.getBalance(productId, exchange))
            .expectNextMatches(response -> response.getBalance() != null)
            .verifyComplete();
    }
}
```

## Troubleshooting

### Common Issues

**Issue**: Session not found in cache
- **Solution**: Check FireflyCacheManager configuration and cache enabled flag

**Issue**: Downstream service timeout
- **Solution**: Verify service URLs and network connectivity

**Issue**: Permission denied
- **Solution**: Check role scopes in reference-master-data

**Issue**: Cache grows too large
- **Solution**: Reduce maximum-size or TTL in cache configuration

## Next Steps

1. Deploy Security Center microservice
2. Configure all downstream service URLs
3. Add `-session` dependency to your microservices
4. Implement authorization logic using `FireflySessionManager`
5. Monitor cache hit rates and adjust configuration
6. Consider Redis for production distributed caching

## Support

For issues or questions:
- Check logs: `kubectl logs -f security-center-pod`
- Monitor metrics: `/actuator/prometheus`
- Review trace logs with correlation IDs
