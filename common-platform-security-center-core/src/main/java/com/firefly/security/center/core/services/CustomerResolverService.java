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

import com.firefly.security.center.interfaces.dtos.CustomerInfoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service for resolving customer/party information from customer-mgmt
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerResolverService {

    private final WebClient customerMgmtWebClient;

    /**
     * Fetches customer/party information from customer-mgmt service
     *
     * @param partyId The party identifier
     * @return Mono<CustomerInfoDTO> with customer information
     */
    public Mono<CustomerInfoDTO> resolveCustomerInfo(UUID partyId) {
        log.debug("Fetching customer info for partyId: {}", partyId);

        return customerMgmtWebClient
                .get()
                .uri("/parties/{partyId}", partyId)
                .retrieve()
                .bodyToMono(CustomerInfoDTO.class)
                .doOnSuccess(customer -> 
                    log.debug("Successfully fetched customer info for partyId: {}", partyId))
                .doOnError(error -> 
                    log.error("Failed to fetch customer info for partyId: {}", partyId, error))
                .onErrorResume(error -> {
                    log.warn("Using fallback customer info for partyId: {}", partyId);
                    return Mono.just(createFallbackCustomerInfo(partyId));
                });
    }

    private CustomerInfoDTO createFallbackCustomerInfo(UUID partyId) {
        return CustomerInfoDTO.builder()
                .partyId(partyId)
                .partyKind("UNKNOWN")
                .fullName("Unknown Customer")
                .isActive(false)
                .build();
    }
}
