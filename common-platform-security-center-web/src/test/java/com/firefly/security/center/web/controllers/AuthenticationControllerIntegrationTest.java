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

package com.firefly.security.center.web.controllers;

import com.firefly.idp.adapter.IdpAdapter;
import com.firefly.idp.dtos.*;
import com.firefly.security.center.core.services.AuthenticationService;
import com.firefly.security.center.core.services.UserMappingService;
import com.firefly.security.center.session.FireflySessionManager;
import com.firefly.security.center.web.config.TestWebClientConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AuthenticationController
 * 
 * <p>Tests the complete authentication flow including:
 * <ul>
 *   <li>Login with valid credentials</li>
 *   <li>Login with invalid credentials</li>
 *   <li>Logout</li>
 *   <li>Token refresh</li>
 *   <li>Token introspection</li>
 * </ul>
 */
@WebFluxTest(
    controllers = AuthenticationController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration.class
    }
)
@Import(TestWebClientConfiguration.class)
@ActiveProfiles("test")
class AuthenticationControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private IdpAdapter idpAdapter;

    @MockBean
    private FireflySessionManager sessionManager;

    @MockBean
    private UserMappingService userMappingService;

    @Test
    void testLoginSuccess() {
        // Arrange
        AuthenticationService.AuthenticationResponse mockResponse = AuthenticationService.AuthenticationResponse.builder()
                .accessToken("mock_access_token")
                .refreshToken("mock_refresh_token")
                .idToken("mock_id_token")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .sessionId("session_123")
                .partyId(UUID.randomUUID())
                .build();

        when(authenticationService.login(any(LoginRequest.class)))
                .thenReturn(Mono.just(mockResponse));

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "username": "test_user",
                            "password": "test_password"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("mock_access_token")
                .jsonPath("$.refreshToken").isEqualTo("mock_refresh_token")
                .jsonPath("$.sessionId").isEqualTo("session_123")
                .jsonPath("$.tokenType").isEqualTo("Bearer");
    }

    @Test
    void testLoginFailure() {
        // Arrange
        when(authenticationService.login(any(LoginRequest.class)))
                .thenReturn(Mono.error(new AuthenticationService.AuthenticationException("Invalid credentials")));

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "username": "bad_user",
                            "password": "bad_password"
                        }
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testLogoutSuccess() {
        // Arrange
        when(authenticationService.logout(any(AuthenticationService.AuthLogoutRequest.class)))
                .thenReturn(Mono.empty());

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "accessToken": "mock_access_token",
                            "refreshToken": "mock_refresh_token",
                            "sessionId": "session_123"
                        }
                        """)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void testTokenRefreshSuccess() {
        // Arrange
        AuthenticationService.AuthenticationResponse mockResponse = AuthenticationService.AuthenticationResponse.builder()
                .accessToken("new_access_token")
                .refreshToken("new_refresh_token")
                .idToken("new_id_token")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .sessionId("session_123")
                .partyId(UUID.randomUUID())
                .build();

        when(authenticationService.refresh(any(RefreshRequest.class)))
                .thenReturn(Mono.just(mockResponse));

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "refreshToken": "mock_refresh_token"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("new_access_token")
                .jsonPath("$.refreshToken").isEqualTo("new_refresh_token");
    }

    @Test
    void testIntrospectSuccess() {
        // Arrange
        IntrospectionResponse mockIntrospection = IntrospectionResponse.builder()
                .active(true)
                .scope("openid profile email")
                .username("test_user")
                .exp(System.currentTimeMillis() / 1000 + 3600)
                .iat(System.currentTimeMillis() / 1000)
                .build();

        when(authenticationService.introspect(any(String.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(mockIntrospection)));

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/auth/introspect?accessToken=mock_access_token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(true)
                .jsonPath("$.username").isEqualTo("test_user");
    }
}
