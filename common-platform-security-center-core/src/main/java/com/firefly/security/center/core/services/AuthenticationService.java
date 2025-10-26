/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.security.center.core.services;

import com.firefly.idp.adapter.IdpAdapter;
import com.firefly.idp.dtos.*;
import com.firefly.security.center.interfaces.dtos.SessionContextDTO;
import com.firefly.security.center.session.FireflySessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Authentication service that orchestrates IDP authentication and Firefly session management.
 * 
 * <p>This service bridges the gap between:
 * <ul>
 *   <li>Identity Provider (Keycloak, AWS Cognito, etc.) - handles authentication</li>
 *   <li>Firefly Session Manager - handles session context and authorization</li>
 * </ul>
 * 
 * <p><strong>Authentication Flow:</strong></p>
 * <pre>
 * 1. User submits credentials to /auth/login
 * 2. Forward credentials to IDP (via IdpAdapter)
 * 3. IDP authenticates and returns tokens
 * 4. Extract user info from IDP
 * 5. Map IDP user to Firefly partyId
 * 6. Create Firefly session with contracts, roles, products
 * 7. Return tokens + sessionId to client
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final IdpAdapter idpAdapter;
    private final FireflySessionManager sessionManager;
    
    @Autowired(required = false)
    private UserMappingService userMappingService;

    /**
     * Authenticate user via IDP and create Firefly session
     *
     * @param request Login credentials
     * @return TokenResponse with IDP tokens and Firefly sessionId
     */
    public Mono<AuthenticationResponse> login(LoginRequest request) {
        log.info("Authenticating user: {}", request.getUsername());

        return idpAdapter.login(request)
                .flatMap(tokenResponse -> {
                    if (tokenResponse.getStatusCode().is2xxSuccessful() && tokenResponse.getBody() != null) {
                        TokenResponse tokens = tokenResponse.getBody();
                        
                        // Get user info from IDP
                        return idpAdapter.getUserInfo(tokens.getAccessToken())
                                .flatMap(userInfoResponse -> {
                                    if (userInfoResponse.getStatusCode().is2xxSuccessful()) {
                                        UserInfoResponse userInfo = userInfoResponse.getBody();
                                        
                                        // Map IDP user to Firefly partyId
                                        return mapUserToPartyId(userInfo, request.getUsername())
                                                .flatMap(partyId -> {
                                                    // Create Firefly session
                                                    return sessionManager.getSessionByPartyId(partyId)
                                                            .map(session -> AuthenticationResponse.builder()
                                                                    .accessToken(tokens.getAccessToken())
                                                                    .refreshToken(tokens.getRefreshToken())
                                                                    .idToken(tokens.getIdToken())
                                                                    .tokenType(tokens.getTokenType())
                                                                    .expiresIn(tokens.getExpiresIn())
                                                                    .sessionId(session.getSessionId())
                                                                    .partyId(partyId)
                                                                    .build());
                                                });
                                    }
                                    return Mono.error(new AuthenticationException("Failed to retrieve user info from IDP"));
                                });
                    }
                    return Mono.error(new AuthenticationException("IDP authentication failed"));
                })
                .doOnSuccess(response -> 
                        log.info("Successfully authenticated user: {}, session: {}", 
                                request.getUsername(), response.getSessionId()))
                .doOnError(error -> 
                        log.error("Authentication failed for user: {}", request.getUsername(), error));
    }

    /**
     * Logout user from both IDP and Firefly session
     *
     * @param logoutRequest Tokens and session info
     * @return Void indicating completion
     */
    public Mono<Void> logout(AuthLogoutRequest logoutRequest) {
        log.info("Logging out session: {}", logoutRequest.getSessionId());

        // Invalidate IDP session
        Mono<Void> idpLogout = idpAdapter.logout(LogoutRequest.builder()
                .accessToken(logoutRequest.getAccessToken())
                .refreshToken(logoutRequest.getRefreshToken())
                .build());

        // Invalidate Firefly session
        Mono<Void> fireflyLogout = sessionManager.invalidateSession(logoutRequest.getSessionId());

        return Mono.when(idpLogout, fireflyLogout)
                .doOnSuccess(v -> log.info("Successfully logged out session: {}", logoutRequest.getSessionId()))
                .doOnError(error -> log.error("Logout failed for session: {}", logoutRequest.getSessionId(), error));
    }

    /**
     * Refresh IDP tokens and update Firefly session
     *
     * @param request Refresh token
     * @return New TokenResponse with updated tokens
     */
    public Mono<AuthenticationResponse> refresh(RefreshRequest request) {
        log.debug("Refreshing tokens");

        return idpAdapter.refresh(request)
                .flatMap(tokenResponse -> {
                    if (tokenResponse.getStatusCode().is2xxSuccessful() && tokenResponse.getBody() != null) {
                        TokenResponse tokens = tokenResponse.getBody();
                        
                        // Get updated user info
                        return idpAdapter.getUserInfo(tokens.getAccessToken())
                                .flatMap(userInfoResponse -> {
                                    if (userInfoResponse.getStatusCode().is2xxSuccessful()) {
                                        UserInfoResponse userInfo = userInfoResponse.getBody();
                                        
                                        // Map to partyId and refresh session
                                        return mapUserToPartyId(userInfoResponse.getBody(), null)
                                                .flatMap(partyId -> sessionManager.getSessionByPartyId(partyId)
                                                        .map(session -> AuthenticationResponse.builder()
                                                                .accessToken(tokens.getAccessToken())
                                                                .refreshToken(tokens.getRefreshToken())
                                                                .idToken(tokens.getIdToken())
                                                                .tokenType(tokens.getTokenType())
                                                                .expiresIn(tokens.getExpiresIn())
                                                                .sessionId(session.getSessionId())
                                                                .partyId(partyId)
                                                                .build()));
                                    }
                                    return Mono.error(new AuthenticationException("Failed to retrieve user info"));
                                });
                    }
                    return Mono.error(new AuthenticationException("Token refresh failed"));
                })
                .doOnSuccess(response -> log.debug("Successfully refreshed tokens"))
                .doOnError(error -> log.error("Token refresh failed", error));
    }

    /**
     * Introspect IDP token
     */
    public Mono<ResponseEntity<IntrospectionResponse>> introspect(String accessToken) {
        return idpAdapter.introspect(accessToken);
    }

    /**
     * Map IDP user to Firefly partyId
     * 
     * This can be customized via UserMappingService to support different mapping strategies:
     * - Username-based mapping
     * - Email-based mapping
     * - External ID mapping
     * - Custom attribute mapping
     */
    private Mono<UUID> mapUserToPartyId(UserInfoResponse userInfo, String username) {
        if (userMappingService != null) {
            return userMappingService.mapToPartyId(userInfo, username);
        }
        
        // Default: try to parse username as UUID or use a lookup service
        // In production, this should query customer-mgmt to find partyId by username/email
        log.warn("No UserMappingService configured, using fallback mapping");
        
        // For now, return a placeholder - in real implementation, query customer-mgmt
        return Mono.just(UUID.randomUUID())
                .doOnNext(partyId -> log.warn("Using generated partyId: {} for user: {}", 
                        partyId, username != null ? username : userInfo.getSub()));
    }

    /**
     * Authentication response with both IDP tokens and Firefly session info
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AuthenticationResponse {
        private String accessToken;
        private String refreshToken;
        private String idToken;
        private String tokenType;
        private Long expiresIn;
        private String sessionId;
        private UUID partyId;
    }

    /**
     * Logout request for both IDP and Firefly session
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AuthLogoutRequest {
        private String accessToken;
        private String refreshToken;
        private String sessionId;
    }

    /**
     * Authentication exception
     */
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
        
        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
