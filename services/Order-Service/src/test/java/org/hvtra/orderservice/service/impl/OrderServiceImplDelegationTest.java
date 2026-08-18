package org.hvtra.orderservice.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OrderServiceImplDelegationTest {

    @Test
    void orderServiceImplStillUsesCanonicalConstructor() {
        assertDoesNotThrow(() -> new OrderServiceImpl(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }
}
