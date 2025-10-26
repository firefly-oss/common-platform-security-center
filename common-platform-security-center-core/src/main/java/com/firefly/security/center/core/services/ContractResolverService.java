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

import com.firefly.security.center.interfaces.dtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Service for resolving contracts with their associated roles and products
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContractResolverService {

    private final WebClient contractMgmtWebClient;
    private final WebClient referenceMasterDataWebClient;
    private final WebClient productMgmtWebClient;

    /**
     * Resolves all active contracts for a party, including:
     * - Contract details
     * - Role information from reference-master-data
     * - Role scopes (permissions) from reference-master-data
     * - Product information from product-mgmt
     *
     * @param partyId The party identifier
     * @return Mono<List<ContractInfoDTO>> with complete contract information
     */
    public Mono<List<ContractInfoDTO>> resolveActiveContracts(UUID partyId) {
        log.debug("Resolving active contracts for partyId: {}", partyId);

        return fetchContractPartiesForParty(partyId)
                .flatMap(this::enrichContractWithDetails)
                .collectList()
                .doOnSuccess(contracts -> 
                    log.info("Resolved {} active contracts for partyId: {}", contracts.size(), partyId))
                .doOnError(error -> 
                    log.error("Failed to resolve contracts for partyId: {}", partyId, error));
    }

    /**
     * Fetches all ContractParty records for a given partyId from contract-mgmt
     */
    private Flux<ContractInfoDTO> fetchContractPartiesForParty(UUID partyId) {
        return contractMgmtWebClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/contract-parties")
                        .queryParam("partyId", partyId.toString())
                        .queryParam("isActive", true)
                        .build())
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ContractInfoDTO>() {})
                .doOnError(error -> 
                    log.error("Failed to fetch contract parties for partyId: {}", partyId, error))
                .onErrorResume(error -> {
                    log.warn("No contracts found for partyId: {}, returning empty list", partyId);
                    return Flux.empty();
                });
    }

    /**
     * Enriches a contract with role, scopes, and product information
     */
    private Mono<ContractInfoDTO> enrichContractWithDetails(ContractInfoDTO contractInfo) {
        UUID contractId = contractInfo.getContractId();
        log.debug("Enriching contract: {}", contractId);

        return Mono.zip(
            fetchContract(contractId),
            fetchRoleWithScopes(contractInfo.getRoleInContract().getRoleId()),
            fetchProduct(contractInfo.getProduct().getProductId())
        ).map(tuple -> {
            ContractInfoDTO fullContract = tuple.getT1();
            RoleInfoDTO roleWithScopes = tuple.getT2();
            ProductInfoDTO productInfo = tuple.getT3();

            return contractInfo.toBuilder()
                    .contractNumber(fullContract.getContractNumber())
                    .contractStatus(fullContract.getContractStatus())
                    .startDate(fullContract.getStartDate())
                    .endDate(fullContract.getEndDate())
                    .roleInContract(roleWithScopes)
                    .product(productInfo)
                    .build();
        }).onErrorResume(error -> {
            log.warn("Failed to fully enrich contract: {}, returning partial data", contractId, error);
            return Mono.just(contractInfo);
        });
    }

    /**
     * Fetches full contract details from contract-mgmt
     */
    private Mono<ContractInfoDTO> fetchContract(UUID contractId) {
        return contractMgmtWebClient
                .get()
                .uri("/contracts/{contractId}", contractId)
                .retrieve()
                .bodyToMono(ContractInfoDTO.class)
                .doOnError(error -> 
                    log.error("Failed to fetch contract: {}", contractId, error));
    }

    /**
     * Fetches role information with all its scopes from reference-master-data
     */
    private Mono<RoleInfoDTO> fetchRoleWithScopes(UUID roleId) {
        log.debug("Fetching role with scopes for roleId: {}", roleId);

        return Mono.zip(
            fetchRole(roleId),
            fetchRoleScopes(roleId)
        ).map(tuple -> {
            RoleInfoDTO role = tuple.getT1();
            List<RoleScopeInfoDTO> scopes = tuple.getT2();

            return role.toBuilder()
                    .scopes(scopes)
                    .build();
        });
    }

    /**
     * Fetches role details from reference-master-data
     */
    private Mono<RoleInfoDTO> fetchRole(UUID roleId) {
        return referenceMasterDataWebClient
                .get()
                .uri("/contract-roles/{roleId}", roleId)
                .retrieve()
                .bodyToMono(RoleInfoDTO.class)
                .doOnError(error -> 
                    log.error("Failed to fetch role: {}", roleId, error))
                .onErrorResume(error -> 
                    Mono.just(createFallbackRole(roleId)));
    }

    /**
     * Fetches all scopes (permissions) for a role from reference-master-data
     */
    private Mono<List<RoleScopeInfoDTO>> fetchRoleScopes(UUID roleId) {
        return referenceMasterDataWebClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/contract-role-scopes")
                        .queryParam("roleId", roleId.toString())
                        .queryParam("isActive", true)
                        .build())
                .retrieve()
                .bodyToFlux(RoleScopeInfoDTO.class)
                .collectList()
                .doOnError(error -> 
                    log.error("Failed to fetch role scopes for roleId: {}", roleId, error))
                .onErrorResume(error -> {
                    log.warn("No scopes found for roleId: {}", roleId);
                    return Mono.just(Collections.emptyList());
                });
    }

    /**
     * Fetches product information from product-mgmt
     */
    private Mono<ProductInfoDTO> fetchProduct(UUID productId) {
        return productMgmtWebClient
                .get()
                .uri("/products/{productId}", productId)
                .retrieve()
                .bodyToMono(ProductInfoDTO.class)
                .doOnError(error -> 
                    log.error("Failed to fetch product: {}", productId, error))
                .onErrorResume(error -> 
                    Mono.just(createFallbackProduct(productId)));
    }

    private RoleInfoDTO createFallbackRole(UUID roleId) {
        return RoleInfoDTO.builder()
                .roleId(roleId)
                .roleCode("UNKNOWN")
                .name("Unknown Role")
                .scopes(Collections.emptyList())
                .isActive(false)
                .build();
    }

    private ProductInfoDTO createFallbackProduct(UUID productId) {
        return ProductInfoDTO.builder()
                .productId(productId)
                .productName("Unknown Product")
                .productStatus("UNKNOWN")
                .build();
    }
}
