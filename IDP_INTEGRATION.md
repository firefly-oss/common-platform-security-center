# IDP Integration Architecture

## Overview

The Security Center now provides **centralized authentication and authorization** for the entire Firefly platform by integrating:

1. **Identity Providers** (Keycloak, AWS Cognito, etc.) via `lib-idp-adapter`
2. **Session Management** via `FireflySessionManager`
3. **Authorization** via contracts, roles, and scopes

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│                     Client Application                     │
└────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────┐
│          Security Center (common-platform-security-center)  │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐  │
│  │          AuthenticationController                    │  │
│  │    /api/v1/auth/login, /logout, /refresh            │  │
│  └─────────────────────────────────────────────────────┘  │
│                           │                                 │
│  ┌─────────────────────────────────────────────────────┐  │
│  │          AuthenticationService                       │  │
│  │    • Coordinates IDP + Session                       │  │
│  │    • Maps IDP user → Firefly partyId                 │  │
│  └─────────────────────────────────────────────────────┘  │
│          │                          │                       │
│          ▼                          ▼                       │
│  ┌──────────────┐        ┌──────────────────────────┐     │
│  │  IdpAdapter  │        │  FireflySessionManager   │     │
│  │   (Keycloak/ │        │  • Contracts              │     │
│  │    Cognito)  │        │  • Roles & Scopes         │     │
│  └──────────────┘        │  • Products               │     │
│                          └──────────────────────────┘     │
└────────────────────────────────────────────────────────────┘
```

## Authentication Flow

### 1. Login Flow

```
Client                  Security Center             IDP            Customer-Mgmt
  │                           │                      │                    │
  ├─POST /auth/login─────────►│                      │                    │
  │ {username, password}       │                      │                    │
  │                           ├─login()──────────────►│                    │
  │                           │                       │                    │
  │                           │◄────tokens────────────┤                    │
  │                           │ {access, refresh, id} │                    │
  │                           │                       │                    │
  │                           ├─getUserInfo()────────►│                    │
  │                           │◄────userInfo──────────┤                    │
  │                           │                       │                    │
  │                           ├─mapToPartyId()───────────────────────────►│
  │                           │◄────partyId──────────────────────────────┤│
  │                           │                       │                    │
  │                           ├─createSession()       │                    │
  │                           │  (aggregates contracts, roles, products)   │
  │                           │                       │                    │
  │◄─────response─────────────┤                       │                    │
  │ {tokens, sessionId}        │                       │                    │
```

### 2. Logout Flow

```
Client                  Security Center             IDP            Cache
  │                           │                      │               │
  ├─POST /logout─────────────►│                      │               │
  │ {tokens, sessionId}        │                      │               │
  │                           ├─logout()─────────────►│               │
  │                           │  (revoke IDP tokens)  │               │
  │                           │                       │               │
  │                           ├─invalidateSession()──────────────────►│
  │                           │  (evict from cache)   │               │
  │                           │                       │               │
  │◄────204 No Content────────┤                       │               │
```

## API Endpoints

### Authentication Endpoints

#### POST /api/v1/auth/login
Authenticate user and create session.

**Request:**
```json
{
  "username": "john.doe",
  "password": "secret123",
  "scope": "openid profile email"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "idToken": "eyJhbGc...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "sessionId": "session_123e4567-e89b-12d3-a456-426614174000_1234567890",
  "partyId": "123e4567-e89b-12d3-a456-426614174000"
}
```

#### POST /api/v1/auth/logout
Logout user from both IDP and Firefly.

**Request:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "sessionId": "session_..."
}
```

**Response:** 204 No Content

#### POST /api/v1/auth/refresh
Refresh IDP tokens.

**Request:**
```json
{
  "refreshToken": "eyJhbGc..."
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "idToken": "eyJhbGc...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "sessionId": "session_...",
  "partyId": "..."
}
```

#### POST /api/v1/auth/introspect
Validate IDP token.

**Query Param:** `accessToken`

**Response:**
```json
{
  "active": true,
  "scope": "openid profile email",
  "username": "john.doe",
  "exp": 1234567890,
  "iat": 1234564290
}
```

## IDP Adapter Integration

### Supported IDPs

1. **Keycloak** - `lib-idp-keycloak-impl` ✅ Fully implemented
2. **AWS Cognito** - `lib-idp-aws-cognito-impl` ✅ Fully implemented
3. **Custom IDP** - Implement `IdpAdapter` interface

### Configuration

**application.yml:**
```yaml
firefly:
  security-center:
    idp:
      provider: keycloak  # or cognito, custom
      
    # Keycloak configuration
    keycloak:
      server-url: ${KEYCLOAK_URL:http://localhost:8080}
      realm: firefly
      client-id: security-center
      client-secret: ${KEYCLOAK_SECRET}
      
    # AWS Cognito configuration
    cognito:
      region: ${COGNITO_REGION:us-east-1}
      user-pool-id: ${COGNITO_USER_POOL_ID}
      client-id: ${COGNITO_CLIENT_ID}
      client-secret: ${COGNITO_CLIENT_SECRET}
      connection-timeout: ${COGNITO_CONNECTION_TIMEOUT:30000}
      request-timeout: ${COGNITO_REQUEST_TIMEOUT:60000}
```

## User Mapping

### IDP User → Firefly partyId

The `UserMappingService` interface allows custom mapping strategies.

**Default Implementation:**
The Security Center includes a `DefaultUserMappingService` that:
1. Queries customer-mgmt by email
2. Falls back to query by username
3. Falls back to deterministic UUID generation from IDP subject

**Custom Implementation Example:**
```java
@Service
public class CustomUserMappingService implements UserMappingService {
    
    private final WebClient customerMgmtWebClient;
    
    @Override
    public Mono<UUID> mapToPartyId(UserInfoResponse userInfo, String username) {
        // Query customer-mgmt by email
        return customerMgmtWebClient
            .get()
            .uri("/parties/by-email/{email}", userInfo.getEmail())
            .retrieve()
            .bodyToMono(PartyDTO.class)
            .map(PartyDTO::getPartyId)
            .onErrorResume(error -> {
                // Fallback: query by username
                return customerMgmtWebClient
                    .get()
                    .uri("/parties/by-username/{username}", username)
                    .retrieve()
                    .bodyToMono(PartyDTO.class)
                    .map(PartyDTO::getPartyId);
            });
    }
}
```

## Complete Flow Example

### 1. User Login
```bash
curl -X POST http://localhost:8085/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john.doe",
    "password": "secret123"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "sessionId": "session_abc123",
  "partyId": "party-uuid"
}
```

### 2. Access Protected Resource
```bash
curl -X GET http://localhost:8081/api/v1/accounts/product-123 \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "X-Party-Id: party-uuid" \
  -H "X-Session-Id: session_abc123"
```

### 3. Logout
```bash
curl -X POST http://localhost:8085/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc...",
    "sessionId": "session_abc123"
  }'
```

## Components Created

### Files Added to Security Center

1. **AuthenticationService.java** - Core authentication logic
2. **UserMappingService.java** - IDP user to partyId mapping interface
3. **AuthenticationController.java** - REST endpoints for auth
4. **Updated core/pom.xml** - Added lib-idp-adapter dependency

### IDP Adapter Repositories

- **lib-idp-adapter**: Core interface and DTOs
- **lib-idp-keycloak-impl**: Keycloak implementation ✅
- **lib-idp-aws-cognito-impl**: AWS Cognito implementation ✅

## Configuration Details

### AWS Cognito Setup

1. **Create User Pool** in AWS Cognito console
2. **Create App Client** with:
   - Auth flows: `USER_PASSWORD_AUTH` enabled
   - Generate client secret (optional)
   - Token expiration configured
3. **Create Groups** for roles
4. **Configure Security Center**:
   ```yaml
   firefly:
     security-center:
       idp:
         provider: cognito
         cognito:
           region: us-east-1
           user-pool-id: us-east-1_XXXXXXXXX
           client-id: your-client-id
           client-secret: your-client-secret
   ```

### Keycloak Setup

1. **Create Realm** in Keycloak
2. **Create Client** with:
   - Client Protocol: openid-connect
   - Access Type: confidential
   - Service Accounts Enabled
3. **Create Roles**
4. **Configure Security Center**:
   ```yaml
   firefly:
     security-center:
       idp:
         provider: keycloak
         keycloak:
           server-url: http://localhost:8080
           realm: firefly
           client-id: security-center
           client-secret: your-secret
   ```

## Next Steps

1. **Choose IDP provider** (Keycloak or AWS Cognito)
2. **Configure IDP** according to setup instructions above
3. **Configure application.yml** with IDP settings
4. **Deploy Security Center** microservice
5. **Test authentication flow** end-to-end
6. **Add JWT validation** to other microservices
7. **Implement custom UserMappingService** if needed

## Security Considerations

1. **Token Storage**: Clients should store tokens securely (httpOnly cookies, secure storage)
2. **Token Transmission**: Always use HTTPS in production
3. **Token Validation**: Microservices should validate JWT signatures
4. **Session Timeout**: Configure appropriate TTLs for both IDP and Firefly sessions
5. **Logout Everywhere**: Ensure both IDP and Firefly sessions are invalidated

## Conclusion

The Security Center now provides **centralized authentication and authorization** for Firefly:

✅ **Authentication** via IdpAdapter (Keycloak, Cognito, etc.)  
✅ **Session Management** via FireflySessionManager  
✅ **Authorization** via contracts, roles, and scopes  
✅ **Login/Logout** endpoints integrated  
✅ **Token Refresh** support  
✅ **Extensible** architecture for multiple IDPs  

**All components are properly integrated and ready for use!**
