# Security Center - API Documentation

## Base URL

```
http://localhost:8080/api/v1/auth
```

## Authentication Endpoints

### 1. Login

Authenticate a user and create a new session.

**Endpoint:** `POST /login`

**Request:**
```json
{
  "username": "user@example.com",
  "password": "password123",
  "scope": "openid profile email"
}
```

**Response:** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "idToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "partyId": "123e4567-e89b-12d3-a456-426614174000"
}
```

**Error Responses:**

- `401 Unauthorized` - Invalid credentials
```json
{
  "error": "AUTHENTICATION_FAILED",
  "message": "Invalid username or password"
}
```

- `400 Bad Request` - Missing required fields
```json
{
  "error": "INVALID_REQUEST",
  "message": "Username and password are required"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user@example.com",
    "password": "password123",
    "scope": "openid profile email"
  }'
```

---

### 2. Refresh Token

Refresh an access token using a refresh token.

**Endpoint:** `POST /refresh`

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response:** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "idToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Error Responses:**

- `401 Unauthorized` - Invalid or expired refresh token
```json
{
  "error": "INVALID_TOKEN",
  "message": "Refresh token is invalid or expired"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
  }'
```

---

### 3. Logout

Logout a user and invalidate their session.

**Endpoint:** `POST /logout`

**Request:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:** `200 OK`
```json
{
  "message": "Logout successful"
}
```

**Error Responses:**

- `401 Unauthorized` - Invalid token
```json
{
  "error": "INVALID_TOKEN",
  "message": "Access token is invalid"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "sessionId": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

---

### 4. Get Session

Retrieve session details by session ID.

**Endpoint:** `GET /session/{sessionId}`

**Headers:**
```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response:** `200 OK`
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "partyId": "123e4567-e89b-12d3-a456-426614174000",
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "idToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresAt": "2025-10-26T20:54:00Z",
  "customer": {
    "partyId": "123e4567-e89b-12d3-a456-426614174000",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com"
  },
  "activeContracts": [
    {
      "contractId": "789e0123-e45b-67d8-a901-234567890abc",
      "contractNumber": "CNT-2025-001",
      "status": "ACTIVE",
      "product": {
        "productId": "456e7890-e12b-34d5-a678-901234567def",
        "productName": "Premium Checking Account",
        "productType": "CHECKING"
      },
      "roleInContract": {
        "roleId": "role-123",
        "roleName": "PRIMARY_HOLDER",
        "scopes": [
          {
            "actionType": "READ",
            "resourceType": "BALANCE"
          },
          {
            "actionType": "WRITE",
            "resourceType": "TRANSACTION"
          }
        ]
      }
    }
  ]
}
```

**Error Responses:**

- `404 Not Found` - Session not found
```json
{
  "error": "SESSION_NOT_FOUND",
  "message": "Session not found or expired"
}
```

- `401 Unauthorized` - Missing or invalid authorization header
```json
{
  "error": "UNAUTHORIZED",
  "message": "Authorization header is required"
}
```

**cURL Example:**
```bash
curl -X GET http://localhost:8080/api/v1/auth/session/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

## Health Check Endpoint

### Health Check

Check the health status of the Security Center service.

**Endpoint:** `GET /actuator/health`

**Response:** `200 OK`
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500000000000,
        "free": 250000000000,
        "threshold": 10485760
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.0"
      }
    },
    "idpProvider": {
      "status": "UP",
      "details": {
        "provider": "keycloak",
        "url": "http://localhost:8080"
      }
    }
  }
}
```

**Degraded Status:** `200 OK` (Service still operational)
```json
{
  "status": "UP",
  "components": {
    "redis": {
      "status": "DOWN",
      "details": {
        "error": "Connection refused"
      }
    }
  }
}
```

**cURL Example:**
```bash
curl -X GET http://localhost:8080/actuator/health
```

---

## Data Models

### AuthenticationRequest

```typescript
interface AuthenticationRequest {
  username: string;       // Required: User's email or username
  password: string;       // Required: User's password
  scope?: string;         // Optional: OAuth 2.0 scopes (default: "openid profile email")
}
```

### AuthenticationResponse

```typescript
interface AuthenticationResponse {
  accessToken: string;    // JWT access token
  refreshToken: string;   // JWT refresh token
  idToken: string;        // JWT ID token
  tokenType: string;      // Token type (always "Bearer")
  expiresIn: number;      // Token expiration in seconds
  sessionId: string;      // UUID of created session
  partyId: string;        // UUID of authenticated party
}
```

### RefreshTokenRequest

```typescript
interface RefreshTokenRequest {
  refreshToken: string;   // Required: Valid refresh token
}
```

### RefreshTokenResponse

```typescript
interface RefreshTokenResponse {
  accessToken: string;    // New JWT access token
  refreshToken: string;   // New JWT refresh token
  idToken: string;        // New JWT ID token
  tokenType: string;      // Token type (always "Bearer")
  expiresIn: number;      // Token expiration in seconds
}
```

### LogoutRequest

```typescript
interface LogoutRequest {
  accessToken: string;    // Required: Current access token
  refreshToken: string;   // Required: Current refresh token
  sessionId: string;      // Required: Session UUID to invalidate
}
```

### SessionContext

```typescript
interface SessionContext {
  sessionId: string;
  partyId: string;
  accessToken: string;
  refreshToken: string;
  idToken: string;
  expiresAt: string;      // ISO 8601 timestamp
  customer: CustomerInfo;
  activeContracts: ContractInfo[];
}

interface CustomerInfo {
  partyId: string;
  firstName: string;
  lastName: string;
  email: string;
}

interface ContractInfo {
  contractId: string;
  contractNumber: string;
  status: string;
  product: ProductInfo;
  roleInContract: RoleInfo;
}

interface ProductInfo {
  productId: string;
  productName: string;
  productType: string;
}

interface RoleInfo {
  roleId: string;
  roleName: string;
  scopes: RoleScope[];
}

interface RoleScope {
  actionType: string;     // READ, WRITE, DELETE, etc.
  resourceType: string;   // BALANCE, TRANSACTION, etc.
}
```

---

## HTTP Status Codes

| Code | Description |
|------|-------------|
| 200  | Success |
| 400  | Bad Request - Invalid input |
| 401  | Unauthorized - Authentication failed |
| 404  | Not Found - Resource not found |
| 500  | Internal Server Error |
| 503  | Service Unavailable - Downstream service error |

---

## Error Response Format

All error responses follow this format:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error message",
  "timestamp": "2025-10-26T19:54:00Z",
  "path": "/api/v1/auth/login"
}
```

### Common Error Codes

| Code | Description |
|------|-------------|
| `AUTHENTICATION_FAILED` | Invalid credentials |
| `INVALID_TOKEN` | Token is invalid or expired |
| `INVALID_REQUEST` | Missing required fields |
| `SESSION_NOT_FOUND` | Session does not exist or expired |
| `UNAUTHORIZED` | Missing authorization |
| `IDP_ERROR` | Identity provider error |
| `CACHE_ERROR` | Cache operation failed |
| `INTERNAL_ERROR` | Unexpected server error |

---

## Rate Limiting

Currently not implemented. Recommended for production:

- **Login**: 5 attempts per minute per IP
- **Refresh**: 10 requests per minute per token
- **Session retrieval**: 60 requests per minute per token

---

## CORS Configuration

In production, configure allowed origins:

```yaml
spring:
  webflux:
    cors:
      allowed-origins: "https://app.example.com"
      allowed-methods: "GET,POST"
      allowed-headers: "*"
      allow-credentials: true
```

---

## Authentication Flow Example

```mermaid
sequenceDiagram
    Client->>Security Center: POST /login
    Security Center->>IDP: Authenticate
    IDP-->>Security Center: Tokens
    Security Center->>Customer Mgmt: Get customer
    Security Center->>Contract Mgmt: Get contracts
    Security Center->>Redis: Cache session
    Security Center-->>Client: AuthenticationResponse
    
    Client->>Security Center: GET /session/{id}
    Security Center->>Redis: Retrieve session
    Redis-->>Security Center: SessionContext
    Security Center-->>Client: SessionContext
    
    Client->>Security Center: POST /logout
    Security Center->>IDP: Revoke tokens
    Security Center->>Redis: Delete session
    Security Center-->>Client: Success
```

---

## SDK Usage (For Other Microservices)

```java
@Autowired
private FireflySessionManager sessionManager;

// Create or retrieve session from request
public Mono<SessionContext> handleRequest(ServerWebExchange exchange) {
    return sessionManager.createOrGetSession(exchange)
        .flatMap(session -> {
            // Use session for authorization
            boolean hasAccess = checkAccess(session);
            if (!hasAccess) {
                return Mono.error(new UnauthorizedException());
            }
            return processRequest(session);
        });
}
```

---

## Best Practices

1. **Always use HTTPS** in production
2. **Store tokens securely** on the client (HttpOnly cookies recommended)
3. **Implement token refresh** before expiration
4. **Handle 401 errors** by refreshing or re-authenticating
5. **Use correlation IDs** for request tracing
6. **Implement exponential backoff** for retries
7. **Validate tokens** on every request
8. **Monitor failed authentication attempts**
