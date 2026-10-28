package org.hvtra.orderservice.dto.request;

import java.util.UUID;

public record PosProductSearchRequest(
        String keyword,
        String sku,
        String productName,
        UUID productId,
        int page,
        int size
) {
    public PosProductSearchRequest(String keyword, String sku, String productName) {
        this(keyword, sku, productName, null, 0, 20);
    }
}

