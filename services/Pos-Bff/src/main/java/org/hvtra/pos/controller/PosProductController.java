package org.hvtra.pos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.pos.dto.request.PosProductSearchRequest;
import org.hvtra.pos.dto.response.PageResponse;
import org.hvtra.pos.dto.response.PosProductVariantResponse;
import org.hvtra.pos.service.PosProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/pos/products")
@Tag(name = "POS Product BFF", description = "API cung cấp dữ liệu sản phẩm tối ưu cho giao diện POS")
public class PosProductController {

    private final PosProductService posProductService;

    public PosProductController(PosProductService posProductService) {
        this.posProductService = posProductService;
    }

    @Operation(summary = "Tìm kiếm danh sách sản phẩm / biến thể cho màn hình bán hàng POS (có phân trang)")
    @GetMapping(value = {"", "/search-view-pos"})
    public PageResponse<PosProductVariantResponse> searchVariants(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "productName", required = false) String productName,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size) {
        PosProductSearchRequest request = new PosProductSearchRequest(keyword, sku, productName, page, size);
        return posProductService.searchVariants(request);
    }

    @Operation(summary = "Lấy chi tiết biến thể sản phẩm cho màn hình bán hàng POS theo variantId")
    @GetMapping(value = {"/{variantId}", "/{variantId}/pos-view"})
    public PosProductVariantResponse getPosView(@PathVariable("variantId") UUID variantId) {
        return posProductService.getPosView(variantId);
    }
}
