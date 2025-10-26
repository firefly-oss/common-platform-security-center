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

import com.firefly.idp.dtos.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of UserMappingService.
 * 
 * <p>This implementation queries the customer-mgmt service to map
 * IDP users to Firefly partyIds using email as the lookup key.
 * 
 * <p><strong>Lookup Strategy:</strong></p>
 * <ol>
 *   <li>Try to find party by email from IDP user info</li>
 *   <li>If email lookup fails, try username lookup</li>
 *   <li>If both fail, generate a new UUID (this should trigger user creation)</li>
 * </ol>
 * 
 * <p>To override this behavior, provide your own {@code @Service} 
 * implementation of {@link UserMappingService}.
 */
@Service
@ConditionalOnMissingBean(UserMappingService.class)
@RequiredArgsConstructor
@Slf4j
public class DefaultUserMappingService implements UserMappingService {

    private final WebClient customerMgmtClient;

    @Override
    public Mono<UUID> mapToPartyId(UserInfoResponse userInfo, String username) {
        log.debug("Mapping IDP user to partyId - email: {}, username: {}", 
                userInfo.getEmail(), username);

        // First, try to find party by email
        if (userInfo.getEmail() != null && !userInfo.getEmail().isBlank()) {
            return findPartyByEmail(userInfo.getEmail())
                    .onErrorResume(error -> {
                        log.warn("Email lookup failed, trying username lookup: {}", error.getMessage());
                        return findPartyByUsername(username != null ? username : userInfo.getPreferredUsername());
                    })
                    .onErrorResume(error -> {
                        log.warn("Username lookup failed, using fallback: {}", error.getMessage());
                        return fallbackMapping(userInfo, username);
                    });
        }

        // If no email, try username
        if (username != null || userInfo.getPreferredUsername() != null) {
            return findPartyByUsername(username != null ? username : userInfo.getPreferredUsername())
                    .onErrorResume(error -> {
                        log.warn("Username lookup failed, using fallback: {}", error.getMessage());
                        return fallbackMapping(userInfo, username);
                    });
        }

        // Fallback
        return fallbackMapping(userInfo, username);
    }

    /**
     * Find party by email address
     */
    private Mono<UUID> findPartyByEmail(String email) {
        log.debug("Looking up party by email: {}", email);
        
        return customerMgmtClient
                .get()
                .uri("/api/v1/parties/by-email/{email}", email)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Object partyId = response.get("partyId");
                    if (partyId instanceof String) {
                        return UUID.fromString((String) partyId);
                    }
                    throw new IllegalStateException("Invalid partyId format in response");
                })
                .doOnSuccess(partyId -> log.info("Found party by email: {}", partyId))
                .doOnError(error -> log.debug("Party not found by email: {}", email));
    }

    /**
     * Find party by username
     */
    private Mono<UUID> findPartyByUsername(String username) {
        if (username == null) {
            return Mono.error(new IllegalArgumentException("Username is null"));
        }
        
        log.debug("Looking up party by username: {}", username);
        
        return customerMgmtClient
                .get()
                .uri("/api/v1/parties/by-username/{username}", username)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Object partyId = response.get("partyId");
                    if (partyId instanceof String) {
                        return UUID.fromString((String) partyId);
                    }
                    throw new IllegalStateException("Invalid partyId format in response");
                })
                .doOnSuccess(partyId -> log.info("Found party by username: {}", partyId))
                .doOnError(error -> log.debug("Party not found by username: {}", username));
    }

    /**
     * Fallback mapping when customer-mgmt lookups fail
     * 
     * In a real system, this might:
     * - Create a new party in customer-mgmt
     * - Return a default/guest partyId
     * - Throw an exception requiring manual user registration
     * 
     * For now, we generate a UUID based on the IDP subject ID to ensure consistency
     */
    private Mono<UUID> fallbackMapping(UserInfoResponse userInfo, String username) {
        log.warn("Using fallback mapping for user - sub: {}, email: {}, username: {}", 
                userInfo.getSub(), userInfo.getEmail(), username);
        
        // Use sub (subject) from IDP to generate deterministic UUID
        if (userInfo.getSub() != null) {
            UUID partyId = UUID.nameUUIDFromBytes(
                    ("idp-user-" + userInfo.getSub()).getBytes()
            );
            log.warn("Generated deterministic partyId from IDP sub: {}", partyId);
            return Mono.just(partyId);
        }
        
        // Last resort: random UUID
        UUID partyId = UUID.randomUUID();
        log.warn("Generated random partyId for unmapped user: {}", partyId);
        return Mono.just(partyId);
    }
}
