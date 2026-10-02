package org.hvtra.catalog.controller;

import org.hvtra.catalog.dto.request.SellableConfigRequest;
import org.hvtra.catalog.service.BarcodeService;
import org.hvtra.catalog.service.ProductVariantService;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductVariantControllerSellableConfigTest {
    @Mock
    private ProductVariantService service;

    @Mock
    private BarcodeService barcodeService;

    @InjectMocks
    private ProductVariantController controller;

    private final UUID variantId = UUID.randomUUID();
    private final SellableConfigRequest request =
            new SellableConfigRequest(new BigDecimal("100.00"), new BigDecimal("20.00"), new BigDecimal("50.00"));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateSellableConfig_allowsSuperAdmin() {
        authenticateWith("ROLE_SUPER_ADMIN");

        controller.updateSellableConfig(variantId, request);

        verify(service).updateSellableConfig(variantId, request);
    }

    @Test
    void updateSellableConfig_allowsKeycloakStyleRoleName() {
        authenticateWith("ROLE_superadmin");

        controller.updateSellableConfig(variantId, request);

        verify(service).updateSellableConfig(variantId, request);
    }

    @Test
    void updateSellableConfig_rejectsAdmin() {
        authenticateWith("ROLE_ADMIN", "ROLE_BRANCH_MANAGER");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> controller.updateSellableConfig(variantId, request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(service, never()).updateSellableConfig(any(), any());
    }

    @Test
    void updateSellableConfig_rejectsWhenUnauthenticated() {
        BusinessException exception = assertThrows(
                BusinessException.class, () -> controller.updateSellableConfig(variantId, request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(service, never()).updateSellableConfig(any(), any());
    }

    private void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user-1",
                null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
