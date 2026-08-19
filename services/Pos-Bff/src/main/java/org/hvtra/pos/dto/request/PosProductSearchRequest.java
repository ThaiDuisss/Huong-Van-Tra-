package org.hvtra.pos.dto.request;

public record PosProductSearchRequest(
        String keyword,
        String sku,
        String productName
) {
}
