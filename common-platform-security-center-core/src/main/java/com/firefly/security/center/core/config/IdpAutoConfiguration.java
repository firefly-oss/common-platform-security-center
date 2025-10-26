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

import com.firefly.idp.adapter.IdpAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for Identity Provider (IDP) adapters.
 * 
 * <p>Dynamically configures the IDP adapter bean based on the configured provider type.
 * Supported providers include:
 * <ul>
 *   <li>Keycloak - via lib-idp-keycloak-impl</li>
 *   <li>AWS Cognito - via lib-idp-aws-cognito-impl</li>
 *   <li>Custom - user-provided IdpAdapter bean</li>
 * </ul>
 * 
 * <p>The adapter is selected via the <code>firefly.security-center.idp.provider</code> property.
 * 
 * <p><strong>Configuration Example:</strong></p>
 * <pre>
 * firefly:
 *   security-center:
 *     idp:
 *       provider: keycloak  # or cognito, custom
 * </pre>
 * 
 * <p><strong>How It Works:</strong></p>
 * <ul>
 *   <li>For Keycloak: Auto-configures when lib-idp-keycloak-impl is on classpath AND provider=keycloak</li>
 *   <li>For Cognito: Auto-configures when lib-idp-aws-cognito-impl is on classpath AND provider=cognito</li>
 *   <li>Only one IDP implementation is loaded at runtime based on configuration</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(IdpConfigurationProperties.class)
@Slf4j
public class IdpAutoConfiguration {

    /**
     * Enable Keycloak IDP adapter when on classpath and provider=keycloak
     */
    @Configuration
    @ConditionalOnClass(name = "com.firefly.idp.adapter.impl.IdpAdapterImpl")
    @ConditionalOnProperty(prefix = "firefly.security-center.idp", name = "provider", havingValue = "keycloak")
    @ComponentScan(basePackages = {"com.firefly.idp.adapter", "com.firefly.idp.properties", "com.firefly.idp.config"})
    static class KeycloakIdpConfiguration {
        public KeycloakIdpConfiguration() {
            log.info("✅ Loading Keycloak IDP adapter configuration");
        }
    }

    /**
     * Enable AWS Cognito IDP adapter when on classpath and provider=cognito
     */
    @Configuration
    @ConditionalOnClass(name = "com.firefly.idp.cognito.adapter.CognitoIdpAdapter")
    @ConditionalOnProperty(prefix = "firefly.security-center.idp", name = "provider", havingValue = "cognito")
    @ComponentScan(basePackages = "com.firefly.idp.cognito")
    static class CognitoIdpConfiguration {
        public CognitoIdpConfiguration() {
            log.info("✅ Loading AWS Cognito IDP adapter configuration");
        }
    }

    /**
     * Fallback bean when no IDP adapter is configured.
     * This will cause a runtime error if no adapter implementation is on the classpath.
     */
    @Bean
    @ConditionalOnMissingBean(IdpAdapter.class)
    @ConditionalOnProperty(prefix = "firefly.security-center.idp", name = "provider")
    public IdpAdapter fallbackIdpAdapter(IdpConfigurationProperties properties) {
        String provider = properties.getProvider();
        log.error("No IdpAdapter bean found for provider: {}. " +
                "Please ensure the corresponding IDP implementation library is on the classpath " +
                "(e.g., lib-idp-keycloak-impl for Keycloak, lib-idp-aws-cognito-impl for Cognito).", 
                provider);
        
        throw new IllegalStateException(
                String.format("IDP adapter not configured for provider '%s'. " +
                        "Add the appropriate dependency (lib-idp-keycloak-impl or lib-idp-aws-cognito-impl) " +
                        "to your project.", provider));
    }
}
