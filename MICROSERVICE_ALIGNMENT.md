# Microservice Alignment Report

This document details the alignment of the Security Center with other Firefly microservices.

## Overview

The Security Center has been aligned with the following microservices:
- `common-platform-customer-mgmt` (Port: 8080)
- `common-platform-contract-mgmt` (Port: 8081)
- `common-platform-product-mgmt` (Port: 8082)
- `common-platform-reference-master-data` (Port: 8083)
- `common-platform-security-center` (Port: 8085)

---

## 1. Application Configuration Alignment

### ✅ Spring Application Metadata

**Pattern from other services:**
```yaml
spring:
  application:
    name: common-platform-{service-name}
    version: 1.0.0
    description: {Service Description}
    team:
      name: Firefly Software Solutions Inc
      email: dev@getfirefly.io
```

**Security Center (UPDATED):**
```yaml
spring:
  application:
    name: common-platform-security-center
    version: 1.0.0
    description: Security Center - Authentication and Session Management
    team:
      name: Firefly Software Solutions Inc
      email: dev@getfirefly.io
```

**Changes Made:**
- ✅ Changed `name` from `security-center` to `common-platform-security-center`
- ✅ Added `version: 1.0.0`
- ✅ Added `description`
- ✅ Added `team` metadata

---

### ✅ Server Configuration

**Pattern from other services:**
```yaml
server:
  address: ${SERVER_ADDRESS:localhost}
  port: ${SERVER_PORT:8080}
  shutdown: graceful
```

**Security Center (UPDATED):**
```yaml
server:
  address: ${SERVER_ADDRESS:localhost}
  port: ${SERVER_PORT:8085}
  shutdown: graceful
```

**Changes Made:**
- ✅ Added `address` configuration with environment variable
- ✅ Changed `port` to use `${SERVER_PORT:8085}` pattern
- ✅ Added `shutdown: graceful`

---

### ✅ Virtual Threads

**Pattern from other services:**
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Security Center (UPDATED):**
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Changes Made:**
- ✅ Added virtual threads configuration (Java 21 feature)

---

### ✅ SpringDoc/OpenAPI Configuration

**Pattern from other services:**
```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    tagsSorter: alpha
    operationsSorter: method
    docExpansion: none
    filter: true
  packages-to-scan: com.firefly.{domain}.{service}.web.controllers
  paths-to-match: /api/**
```

**Security Center (UPDATED):**
```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    tagsSorter: alpha
    operationsSorter: method
    docExpansion: none
    filter: true
  packages-to-scan: com.firefly.security.center.web.controllers
  paths-to-match: /api/**
```

**Changes Made:**
- ✅ Added complete SpringDoc configuration
- ✅ Configured Swagger UI
- ✅ Set package scanning to controllers

---

### ✅ Profile-Specific Configuration

**Pattern from other services:**
```yaml
---
spring:
  config:
    activate:
      on-profile: dev

logging:
  level:
    root: INFO
    com.firefly: DEBUG

---
spring:
  config:
    activate:
      on-profile: testing

---
spring:
  config:
    activate:
      on-profile: prod

logging:
  level:
    root: WARN
    com.firefly: INFO

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

**Security Center (UPDATED):**
- ✅ Added `dev` profile with DEBUG logging
- ✅ Added `testing` profile with INFO logging
- ✅ Added `prod` profile with WARN logging and disabled Swagger

---

## 2. Port Alignment

### Microservice Port Mapping

| Service | Port | Environment Variable | Status |
|---------|------|---------------------|--------|
| Customer Management | 8080 | `SERVER_PORT` | ✅ Verified |
| Contract Management | 8081 | `SERVER_PORT` | ✅ Verified |
| Product Management | 8082 | `SERVER_PORT` | ✅ Verified |
| Reference Master Data | 8083 | `SERVER_PORT` | ✅ Verified |
| **Security Center** | **8085** | `SERVER_PORT` | ✅ **Updated** |

### Client Configuration (UPDATED)

**Before:**
```yaml
clients:
  customer-mgmt:
    base-url: ${CUSTOMER_MGMT_URL:http://localhost:8081}  # WRONG!
  contract-mgmt:
    base-url: ${CONTRACT_MGMT_URL:http://localhost:8082}  # WRONG!
  product-mgmt:
    base-url: ${PRODUCT_MGMT_URL:http://localhost:8083}  # WRONG!
  reference-master-data:
    base-url: ${REFERENCE_MASTER_DATA_URL:http://localhost:8084}  # WRONG!
```

**After (CORRECTED):**
```yaml
clients:
  customer-mgmt:
    base-url: ${CUSTOMER_MGMT_URL:http://localhost:8080}  # ✅ CORRECT
  contract-mgmt:
    base-url: ${CONTRACT_MGMT_URL:http://localhost:8081}  # ✅ CORRECT
  product-mgmt:
    base-url: ${PRODUCT_MGMT_URL:http://localhost:8082}  # ✅ CORRECT
  reference-master-data:
    base-url: ${REFERENCE_MASTER_DATA_URL:http://localhost:8083}  # ✅ CORRECT
```

---

## 3. SDK Module Alignment

### SDK Package Structure

**Pattern from other services:**

| Service | API Package | Model Package | Invoker Package |
|---------|-------------|---------------|-----------------|
| Customer Mgmt | `com.firefly.core.customer.sdk.api` | `com.firefly.core.customer.sdk.model` | `com.firefly.core.customer.sdk.invoker` |
| Contract Mgmt | `com.firefly.core.contract.sdk.api` | `com.firefly.core.contract.sdk.model` | `com.firefly.core.contract.sdk.invoker` |
| Product Mgmt | `com.firefly.common.product.sdk.api` | `com.firefly.common.product.sdk.model` | `com.firefly.common.product.sdk.invoker` |
| Reference Master Data | `com.firefly.common.reference.master.data.sdk.api` | `com.firefly.common.reference.master.data.sdk.model` | `com.firefly.common.reference.master.data.sdk.invoker` |
| **Security Center** | `com.firefly.security.center.sdk.api` | `com.firefly.security.center.sdk.model` | `com.firefly.security.center.sdk.invoker` |

### SDK POM Configuration (UPDATED)

**Changes Made:**
- ✅ Added OpenAPI Generator Maven Plugin
- ✅ Configured package names: `com.firefly.security.center.sdk.*`
- ✅ Added all required dependencies (Jackson, OkHttp, Swagger annotations)
- ✅ Created OpenAPI spec at `src/main/resources/api-spec/openapi.yml`
- ✅ Configured webclient library with reactive support

**OpenAPI Generator Configuration:**
```xml
<apiPackage>com.firefly.security.center.sdk.api</apiPackage>
<modelPackage>com.firefly.security.center.sdk.model</modelPackage>
<invokerPackage>com.firefly.security.center.sdk.invoker</invokerPackage>
<configOptions>
    <java20>true</java20>
    <useTags>true</useTags>
    <dateLibrary>java8-localdatetime</dateLibrary>
    <sourceFolder>src/gen/java/main</sourceFolder>
    <library>webclient</library>
    <reactive>true</reactive>
    <returnResponse>true</returnResponse>
</configOptions>
```

---

## 4. OpenAPI Specification

### Created OpenAPI Spec

**File:** `common-platform-security-center-sdk/src/main/resources/api-spec/openapi.yml`

**Endpoints Documented:**
- ✅ `POST /api/v1/auth/login` - Login
- ✅ `POST /api/v1/auth/logout` - Logout
- ✅ `POST /api/v1/auth/refresh` - Refresh Token
- ✅ `POST /api/v1/auth/introspect` - Introspect Token
- ✅ `POST /api/v1/sessions` - Create or Get Session
- ✅ `GET /api/v1/sessions/{sessionId}` - Get Session by ID
- ✅ `DELETE /api/v1/sessions/{sessionId}` - Invalidate Session
- ✅ `GET /api/v1/sessions/party/{partyId}` - Get Session by Party ID
- ✅ `DELETE /api/v1/sessions/party/{partyId}` - Invalidate Sessions by Party ID
- ✅ `POST /api/v1/sessions/{sessionId}/refresh` - Refresh Session
- ✅ `GET /api/v1/sessions/{sessionId}/validate` - Validate Session
- ✅ `GET /api/v1/sessions/permission-check` - Check Permission

**Schemas Documented:**
- ✅ LoginRequest, RefreshRequest, LogoutRequest
- ✅ AuthenticationResponse, IntrospectionResponse
- ✅ SessionContextDTO, CustomerInfoDTO, ContractInfoDTO
- ✅ ProductInfoDTO, RoleInfoDTO, RoleScopeInfoDTO

---

## 5. Summary of Changes

### Files Modified

1. ✅ `common-platform-security-center-web/src/main/resources/application.yml`
   - Updated application name to `common-platform-security-center`
   - Added version, description, team metadata
   - Added virtual threads configuration
   - Added SpringDoc configuration
   - Fixed client port mappings (8081→8080, 8082→8081, 8083→8082, 8084→8083)
   - Added profile-specific configurations (dev, testing, prod)

2. ✅ `common-platform-security-center-sdk/pom.xml`
   - Complete rewrite to match other SDK modules
   - Added OpenAPI Generator plugin
   - Added all required dependencies
   - Configured package structure

3. ✅ `common-platform-security-center-sdk/src/main/resources/api-spec/openapi.yml`
   - **NEW FILE** - Complete OpenAPI 3.0 specification
   - Documents all endpoints and schemas

---

## 6. Verification Checklist

- ✅ Application name follows pattern: `common-platform-{service-name}`
- ✅ Port configuration uses environment variables
- ✅ Virtual threads enabled (Java 21)
- ✅ SpringDoc/Swagger configured
- ✅ Profile-specific configurations (dev, testing, prod)
- ✅ Client URLs point to correct ports
- ✅ SDK module has OpenAPI generator
- ✅ SDK package names follow pattern
- ✅ OpenAPI spec created and complete
- ✅ Graceful shutdown configured
- ✅ Management endpoints configured

---

## 7. Build Fix Applied

### Issue: Missing JSR-305 Dependency

**Problem**: The OpenAPI generator created code with `@Nullable` and `@Nonnull` annotations from `javax.annotation`, but the `javax.annotation-api` dependency doesn't include these annotations.

**Error**:
```
cannot find symbol
  symbol:   class Nullable
  location: package javax.annotation
```

**Solution**: Added `com.google.code.findbugs:jsr305` dependency to provide JSR-305 annotations.

**Change in `common-platform-security-center-sdk/pom.xml`**:
```xml
<!-- For @Nullable and @Nonnull annotations -->
<dependency>
    <groupId>com.google.code.findbugs</groupId>
    <artifactId>jsr305</artifactId>
    <version>3.0.2</version>
</dependency>
```

**Result**: Build now succeeds! ✅

---

## 8. Build Verification ✅

### Build Command
```bash
mvn clean install -DskipTests
```

### Build Summary
```
[INFO] Reactor Summary for Firefly Security Center 1.0.0-SNAPSHOT:
[INFO]
[INFO] Firefly Security Center ............................ SUCCESS [  0.076 s]
[INFO] Firefly Security Center - Interfaces ............... SUCCESS [  0.849 s]
[INFO] Firefly Security Center - Session Library .......... SUCCESS [  0.282 s]
[INFO] Firefly Security Center - Core ..................... SUCCESS [  0.890 s]
[INFO] Firefly Security Center - Web ...................... SUCCESS [  0.726 s]
[INFO] common-platform-security-center-sdk ................ SUCCESS [  0.914 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

**Total Build Time**: 3.852 seconds
**All Modules**: ✅ SUCCESS

### Generated SDK Structure
```
common-platform-security-center-sdk/target/generated-sources/src/gen/java/main/com/firefly/security/center/sdk/
├── api/
│   ├── AuthenticationApi.java
│   └── SessionsApi.java
├── model/
│   ├── AuthenticationResponse.java
│   ├── ContractInfoDTO.java
│   ├── CustomerInfoDTO.java
│   ├── IntrospectionResponse.java
│   ├── LoginRequest.java
│   ├── LogoutRequest.java
│   ├── ProductInfoDTO.java
│   ├── RefreshRequest.java
│   ├── RoleInfoDTO.java
│   ├── RoleScopeInfoDTO.java
│   └── SessionContextDTO.java
└── invoker/
    ├── ApiClient.java
    ├── ServerConfiguration.java
    ├── ServerVariable.java
    ├── JavaTimeFormatter.java
    ├── StringUtil.java
    ├── RFC3339DateFormat.java
    └── auth/
        ├── Authentication.java
        ├── HttpBasicAuth.java
        ├── HttpBearerAuth.java
        └── ApiKeyAuth.java
```

---

## 9. Usage

The generated SDK can now be used by other microservices:

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>common-platform-security-center-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Example usage:
```java
// Create API client
ApiClient apiClient = new ApiClient();
apiClient.setBasePath("http://localhost:8085");

// Use Authentication API
AuthenticationApi authApi = new AuthenticationApi(apiClient);
LoginRequest loginRequest = new LoginRequest()
    .username("user@example.com")
    .password("password");

Mono<AuthenticationResponse> response = authApi.login(loginRequest);

// Use Sessions API
SessionsApi sessionsApi = new SessionsApi(apiClient);
Mono<SessionContextDTO> session = sessionsApi.getSessionById("session-id");
```

