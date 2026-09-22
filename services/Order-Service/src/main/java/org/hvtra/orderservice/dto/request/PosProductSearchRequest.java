package org.hvtra.orderservice.dto.request;

public record PosProductSearchRequest(
        String keyword,
        String sku,
        String productName,
        int page,
        int size
) {
    public PosProductSearchRequest(String keyword, String sku, String productName) {
        this(keyword, sku, productName, 0, 20);
    }
}

