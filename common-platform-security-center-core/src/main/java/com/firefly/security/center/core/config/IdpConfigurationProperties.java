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

package com.firefly.security.center.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Identity Provider (IDP) integration.
 * 
 * <p>Supports multiple IDP providers:
 * <ul>
 *   <li>Keycloak</li>
 *   <li>AWS Cognito</li>
 *   <li>Custom implementations</li>
 * </ul>
 * 
 * <p><strong>Configuration Example:</strong></p>
 * <pre>
 * firefly:
 *   security-center:
 *     idp:
 *       provider: keycloak
 *       keycloak:
 *         server-url: http://localhost:8080
 *         realm: firefly
 *         client-id: security-center
 *         client-secret: ${KEYCLOAK_SECRET}
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "firefly.security-center.idp")
@Data
public class IdpConfigurationProperties {

    /**
     * IDP provider type: keycloak, cognito, custom
     */
    private String provider = "keycloak";

    /**
     * Keycloak-specific configuration
     */
    private KeycloakConfig keycloak = new KeycloakConfig();

    /**
     * AWS Cognito-specific configuration
     */
    private CognitoConfig cognito = new CognitoConfig();

    @Data
    public static class KeycloakConfig {
        private String serverUrl = "http://localhost:8080";
        private String realm = "firefly";
        private String clientId;
        private String clientSecret;
        private String adminUsername;
        private String adminPassword;
    }

    @Data
    public static class CognitoConfig {
        private String region = "us-east-1";
        private String userPoolId;
        private String clientId;
        private String clientSecret;
        private String domain;
    }
}
