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

import com.firefly.common.product.sdk.api.ProductApi;
import com.firefly.common.product.sdk.model.ProductDTO;
import com.firefly.common.reference.master.data.sdk.api.ContractRoleApi;
import com.firefly.common.reference.master.data.sdk.api.ContractRoleScopeApi;
import com.firefly.common.reference.master.data.sdk.model.ContractRoleDTO;
import com.firefly.common.reference.master.data.sdk.model.ContractRoleScopeDTO;
import com.firefly.core.contract.sdk.api.ContractsApi;
import com.firefly.core.contract.sdk.api.GlobalContractPartiesApi;
import com.firefly.core.contract.sdk.model.ContractDTO;
import com.firefly.core.contract.sdk.model.ContractPartyDTO;
import com.firefly.core.contract.sdk.model.PaginationResponse;
import com.firefly.security.center.interfaces.dtos.ContractInfoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ContractResolverService.
 * Tests contract resolution and enrichment with mocked SDK APIs.
 */
@ExtendWith(MockitoExtension.class)
class ContractResolverServiceTest {

    @Mock
    private ContractsApi contractsApi;

    @Mock
    private GlobalContractPartiesApi globalContractPartiesApi;

    @Mock
    private ProductApi productApi;

    @Mock
    private ContractRoleApi contractRoleApi;

    @Mock
    private ContractRoleScopeApi contractRoleScopeApi;

    @InjectMocks
    private ContractResolverService contractResolverService;

    private UUID testPartyId;
    private UUID testContractId;
    private UUID testRoleId;
    private UUID testProductId;
    private UUID testScopeId;

    @BeforeEach
    void setUp() {
        testPartyId = UUID.randomUUID();
        testContractId = UUID.randomUUID();
        testRoleId = UUID.randomUUID();
        testProductId = UUID.randomUUID();
        testScopeId = UUID.randomUUID();
    }

    @Test
    void resolveActiveContracts_success() {
        // Given
        ContractPartyDTO contractParty = createContractPartyDTO();
        PaginationResponse contractPartiesResponse = createPaginationResponse(List.of(contractParty));
        ContractDTO contract = createContractDTO();
        ContractRoleDTO role = createContractRoleDTO();
        ContractRoleScopeDTO scope = createContractRoleScopeDTO();
        ProductDTO product = createProductDTO();

        when(globalContractPartiesApi.getContractPartiesByPartyId(testPartyId, true))
                .thenReturn(Mono.just(contractPartiesResponse));
        when(contractsApi.getContractById(testContractId)).thenReturn(Mono.just(contract));
        when(contractRoleApi.getContractRole(testRoleId)).thenReturn(Mono.just(role));
        when(contractRoleScopeApi.getActiveScopesByRoleId(testRoleId)).thenReturn(Mono.just(scope));
        when(productApi.getProduct(testProductId)).thenReturn(Mono.just(product));

        // When & Then
        StepVerifier.create(contractResolverService.resolveActiveContracts(testPartyId))
                .assertNext(contracts -> {
                    assertThat(contracts).hasSize(1);
                    ContractInfoDTO contractInfo = contracts.get(0);
                    
                    assertThat(contractInfo.getContractId()).isEqualTo(testContractId);
                    assertThat(contractInfo.getContractNumber()).isEqualTo("CONT-001");
                    assertThat(contractInfo.getContractStatus()).isEqualTo("ACTIVE");
                    
                    assertThat(contractInfo.getRoleInContract()).isNotNull();
                    assertThat(contractInfo.getRoleInContract().getRoleId()).isEqualTo(testRoleId);
                    assertThat(contractInfo.getRoleInContract().getRoleCode()).isEqualTo("BORROWER");
                    assertThat(contractInfo.getRoleInContract().getScopes()).hasSize(1);
                    
                    assertThat(contractInfo.getProduct()).isNotNull();
                    assertThat(contractInfo.getProduct().getProductId()).isEqualTo(testProductId);
                    assertThat(contractInfo.getProduct().getProductName()).isEqualTo("Personal Loan");
                })
                .verifyComplete();

        verify(globalContractPartiesApi).getContractPartiesByPartyId(testPartyId, true);
        verify(contractsApi).getContractById(testContractId);
        verify(contractRoleApi).getContractRole(testRoleId);
        verify(contractRoleScopeApi).getActiveScopesByRoleId(testRoleId);
        verify(productApi).getProduct(testProductId);
    }

    @Test
    void resolveActiveContracts_multipleContracts_success() {
        // Given
        UUID contract2Id = UUID.randomUUID();
        UUID role2Id = UUID.randomUUID();
        UUID product2Id = UUID.randomUUID();
        
        ContractPartyDTO contractParty1 = createContractPartyDTO();
        ContractPartyDTO contractParty2 = new ContractPartyDTO();
        contractParty2.setContractId(contract2Id);
        contractParty2.setPartyId(testPartyId);
        contractParty2.setRoleInContractId(role2Id);
        contractParty2.setIsActive(true);
        
        PaginationResponse response = createPaginationResponse(List.of(contractParty1, contractParty2));

        when(globalContractPartiesApi.getContractPartiesByPartyId(testPartyId, true))
                .thenReturn(Mono.just(response));
        when(contractsApi.getContractById(testContractId)).thenReturn(Mono.just(createContractDTO()));
        when(contractsApi.getContractById(contract2Id)).thenReturn(Mono.just(createContractDTO(contract2Id)));
        when(contractRoleApi.getContractRole(testRoleId)).thenReturn(Mono.just(createContractRoleDTO()));
        when(contractRoleApi.getContractRole(role2Id)).thenReturn(Mono.just(createContractRoleDTO(role2Id)));
        when(contractRoleScopeApi.getActiveScopesByRoleId(any())).thenReturn(Mono.just(createContractRoleScopeDTO()));
        when(productApi.getProduct(any())).thenReturn(Mono.just(createProductDTO()));

        // When & Then
        StepVerifier.create(contractResolverService.resolveActiveContracts(testPartyId))
                .assertNext(contracts -> {
                    assertThat(contracts).hasSize(2);
                })
                .verifyComplete();
    }

    @Test
    void resolveActiveContracts_noContracts_returnsEmpty() {
        // Given
        PaginationResponse emptyResponse = createPaginationResponse(List.of());

        when(globalContractPartiesApi.getContractPartiesByPartyId(testPartyId, true))
                .thenReturn(Mono.just(emptyResponse));

        // When & Then
        StepVerifier.create(contractResolverService.resolveActiveContracts(testPartyId))
                .assertNext(contracts -> {
                    assertThat(contracts).isEmpty();
                })
                .verifyComplete();

        verify(globalContractPartiesApi).getContractPartiesByPartyId(testPartyId, true);
        verifyNoInteractions(contractsApi, contractRoleApi, contractRoleScopeApi, productApi);
    }

    @Test
    void resolveActiveContracts_contractPartiesFetchFails_returnsEmpty() {
        // Given
        when(globalContractPartiesApi.getContractPartiesByPartyId(testPartyId, true))
                .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

        // When & Then
        StepVerifier.create(contractResolverService.resolveActiveContracts(testPartyId))
                .assertNext(contracts -> {
                    assertThat(contracts).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void resolveActiveContracts_contractEnrichmentFails_returnsPartialData() {
        // Given
        ContractPartyDTO contractParty = createContractPartyDTO();
        PaginationResponse response = createPaginationResponse(List.of(contractParty));

        when(globalContractPartiesApi.getContractPartiesByPartyId(testPartyId, true))
                .thenReturn(Mono.just(response));
        when(contractsApi.getContractById(testContractId))
                .thenReturn(Mono.error(new RuntimeException("Contract not found")));

        // When & Then
        StepVerifier.create(contractResolverService.resolveActiveContracts(testPartyId))
                .assertNext(contracts -> {
                    assertThat(contracts).hasSize(1);
                    // Should return partial data (just IDs)
                    assertThat(contracts.get(0).getContractId()).isEqualTo(testContractId);
                })
                .verifyComplete();
    }

    @Test
    void resolveActiveContracts_roleEnrichmentFails_usesFallback() {
        // Given
        ContractPartyDTO contractParty = createContractPartyDTO();
        PaginationResponse response = createPaginationResponse(List.of(contractParty));
        ContractDTO contract = createContractDTO();
        ProductDTO product = createProductDTO();

        when(globalContractPartiesApi.getContractPartiesByPartyId(testPartyId, true))
                .thenReturn(Mono.just(response));
        when(contractsApi.getContractById(testContractId)).thenReturn(Mono.just(contract));
        when(contractRoleApi.getContractRole(testRoleId))
                .thenReturn(Mono.error(new RuntimeException("Role not found")));
        when(contractRoleScopeApi.getActiveScopesByRoleId(testRoleId))
                .thenReturn(Mono.error(new RuntimeException("Scopes not found")));
        when(productApi.getProduct(testProductId)).thenReturn(Mono.just(product));

        // When & Then
        StepVerifier.create(contractResolverService.resolveActiveContracts(testPartyId))
                .assertNext(contracts -> {
                    assertThat(contracts).hasSize(1);
                    ContractInfoDTO contractInfo = contracts.get(0);
                    
                    assertThat(contractInfo.getRoleInContract()).isNotNull();
                    assertThat(contractInfo.getRoleInContract().getRoleCode()).isEqualTo("UNKNOWN");
                    assertThat(contractInfo.getRoleInContract().getName()).isEqualTo("Unknown Role");
                })
                .verifyComplete();
    }

    @Test
    void resolveActiveContracts_productFetchFails_usesFallback() {
        // Given
        ContractPartyDTO contractParty = createContractPartyDTO();
        PaginationResponse response = createPaginationResponse(List.of(contractParty));
        ContractDTO contract = createContractDTO();
        ContractRoleDTO role = createContractRoleDTO();
        ContractRoleScopeDTO scope = createContractRoleScopeDTO();

        when(globalContractPartiesApi.getContractPartiesByPartyId(testPartyId, true))
                .thenReturn(Mono.just(response));
        when(contractsApi.getContractById(testContractId)).thenReturn(Mono.just(contract));
        when(contractRoleApi.getContractRole(testRoleId)).thenReturn(Mono.just(role));
        when(contractRoleScopeApi.getActiveScopesByRoleId(testRoleId)).thenReturn(Mono.just(scope));
        when(productApi.getProduct(testProductId))
                .thenReturn(Mono.error(new RuntimeException("Product not found")));

        // When & Then
        StepVerifier.create(contractResolverService.resolveActiveContracts(testPartyId))
                .assertNext(contracts -> {
                    assertThat(contracts).hasSize(1);
                    ContractInfoDTO contractInfo = contracts.get(0);
                    
                    assertThat(contractInfo.getProduct()).isNotNull();
                    assertThat(contractInfo.getProduct().getProductName()).isEqualTo("Unknown Product");
                    assertThat(contractInfo.getProduct().getProductStatus()).isEqualTo("UNKNOWN");
                })
                .verifyComplete();
    }

    @Test
    void resolveActiveContracts_scopesFetchFails_continuesWithoutScopes() {
        // Given
        ContractPartyDTO contractParty = createContractPartyDTO();
        PaginationResponse response = createPaginationResponse(List.of(contractParty));
        ContractDTO contract = createContractDTO();
        ContractRoleDTO role = createContractRoleDTO();
        ProductDTO product = createProductDTO();

        when(globalContractPartiesApi.getContractPartiesByPartyId(testPartyId, true))
                .thenReturn(Mono.just(response));
        when(contractsApi.getContractById(testContractId)).thenReturn(Mono.just(contract));
        when(contractRoleApi.getContractRole(testRoleId)).thenReturn(Mono.just(role));
        when(contractRoleScopeApi.getActiveScopesByRoleId(testRoleId))
                .thenReturn(Mono.error(new RuntimeException("Scopes service down")));
        when(productApi.getProduct(testProductId)).thenReturn(Mono.just(product));

        // When & Then
        StepVerifier.create(contractResolverService.resolveActiveContracts(testPartyId))
                .assertNext(contracts -> {
                    assertThat(contracts).hasSize(1);
                    ContractInfoDTO contractInfo = contracts.get(0);
                    
                    assertThat(contractInfo.getRoleInContract().getScopes()).isEmpty();
                })
                .verifyComplete();
    }

    // ========== Helper Methods ==========

    private ContractPartyDTO createContractPartyDTO() {
        ContractPartyDTO dto = new ContractPartyDTO();
        dto.setContractId(testContractId);
        dto.setPartyId(testPartyId);
        dto.setRoleInContractId(testRoleId);
        dto.setIsActive(true);
        return dto;
    }

    private PaginationResponse createPaginationResponse(List<?> content) {
        PaginationResponse response = new PaginationResponse();
        response.setContent((List<Object>) content);
        response.setTotalElements((long) content.size());
        response.setTotalPages(1);
        // PaginationResponse uses 'currentPage' field
        try {
            java.lang.reflect.Field currentPageField = PaginationResponse.class.getDeclaredField("currentPage");
            currentPageField.setAccessible(true);
            currentPageField.set(response, 0);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set pagination fields", e);
        }
        return response;
    }

    private ContractDTO createContractDTO() {
        return createContractDTO(testContractId);
    }

    private ContractDTO createContractDTO(UUID contractId) {
        ContractDTO dto = new ContractDTO();
        // contractId is read-only, set via field reflection in tests or use JsonCreator
        dto.setContractNumber("CONT-001");
        dto.setContractStatus(ContractDTO.ContractStatusEnum.ACTIVE);
        dto.setStartDate(LocalDateTime.now().minusDays(30));
        dto.setEndDate(LocalDateTime.now().plusDays(335));
        dto.setProductId(testProductId);
        // Use reflection to set contractId (read-only field)
        try {
            java.lang.reflect.Field field = ContractDTO.class.getDeclaredField("contractId");
            field.setAccessible(true);
            field.set(dto, contractId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set contractId", e);
        }
        return dto;
    }

    private ContractRoleDTO createContractRoleDTO() {
        return createContractRoleDTO(testRoleId);
    }

    private ContractRoleDTO createContractRoleDTO(UUID roleId) {
        ContractRoleDTO dto = new ContractRoleDTO();
        dto.setRoleCode("BORROWER");
        dto.setName("Borrower");
        dto.setDescription("Primary borrower role");
        dto.setIsActive(true);
        // Use reflection to set roleId (read-only field)
        try {
            java.lang.reflect.Field field = ContractRoleDTO.class.getDeclaredField("roleId");
            field.setAccessible(true);
            field.set(dto, roleId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set roleId", e);
        }
        return dto;
    }

    private ContractRoleScopeDTO createContractRoleScopeDTO() {
        ContractRoleScopeDTO dto = new ContractRoleScopeDTO();
        dto.setScopeCode("READ_CONTRACT");
        dto.setScopeName("Read Contract");
        dto.setDescription("Permission to read contract details");
        // Use reflection to set scopeId (read-only field)
        try {
            java.lang.reflect.Field field = ContractRoleScopeDTO.class.getDeclaredField("scopeId");
            field.setAccessible(true);
            field.set(dto, testScopeId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set scopeId", e);
        }
        return dto;
    }

    private ProductDTO createProductDTO() {
        ProductDTO dto = new ProductDTO();
        dto.setProductCode("PERSONAL_LOAN");
        dto.setProductName("Personal Loan");
        dto.setProductStatus(ProductDTO.ProductStatusEnum.ACTIVE);
        // Use reflection to set productId (read-only field)
        try {
            java.lang.reflect.Field field = ProductDTO.class.getDeclaredField("productId");
            field.setAccessible(true);
            field.set(dto, testProductId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set productId", e);
        }
        return dto;
    }
}
