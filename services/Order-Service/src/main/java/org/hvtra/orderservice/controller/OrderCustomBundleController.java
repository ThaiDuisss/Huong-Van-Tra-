package org.hvtra.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.orderservice.domain.enums.BundlePackingStatus;
import org.hvtra.orderservice.dto.request.AddDraftBundleItemRequest;
import org.hvtra.orderservice.dto.request.FulfillBackorderRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftBundleItemRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.service.OrderCustomBundleService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Trà đóng theo yêu cầu (Custom Tea Bundle) trên POS. */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderCustomBundleController {

    private static final String UUID_PATH = "{orderId:[0-9a-fA-F\\-]{36}}";

    private final OrderCustomBundleService orderCustomBundleService;

    @Operation(summary = "them tra dong theo yeu cau vao hoa don nhap POS")
    @UserAction(value = "ORDER_DRAFT_BUNDLE_ADD", entityType = "Order", entityId = "#result.id")
    @PostMapping("/pos/draft/bundle-items")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse addDraftBundleItem(@Valid @RequestBody AddDraftBundleItemRequest request) {
        return orderCustomBundleService.addDraftBundleItem(request);
    }

    @Operation(summary = "sua nguyen lieu cua tra dong theo yeu cau trong hoa don nhap POS")
    @UserAction(value = "ORDER_DRAFT_BUNDLE_UPDATE", entityType = "Order", entityId = "#orderId", detail = "#itemId")
    @PutMapping("/pos/draft/" + UUID_PATH + "/bundle-items/{itemId}")
    public OrderResponse updateDraftBundleItem(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateDraftBundleItemRequest request
    ) {
        return orderCustomBundleService.updateDraftBundleItem(orderId, itemId, request);
    }

    @Operation(summary = "don co tra dong theo yeu cau da thu tien, chua giao")
    @GetMapping("/custom-bundles")
    public Page<OrderResponse> getAwaitingHandover(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BundlePackingStatus packingStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return orderCustomBundleService.getAwaitingHandover(keyword, packingStatus, page, size);
    }

    @Operation(summary = "lay lai trang thai dong goi tu kho (tao yeu cau con thieu)")
    @PostMapping("/" + UUID_PATH + "/custom-bundle/refresh")
    public OrderResponse refreshPacking(@PathVariable UUID orderId) {
        return orderCustomBundleService.refreshPacking(orderId);
    }

    @Operation(summary = "giao tra dong theo yeu cau: kiem tra da dong goi, thu phan con lai, hoan tat don")
    @UserAction(value = "ORDER_BUNDLE_HANDOVER", entityType = "Order", entityId = "#orderId")
    @PostMapping("/" + UUID_PATH + "/custom-bundle/handover")
    public OrderResponse handover(
            @PathVariable UUID orderId,
            @RequestBody(required = false) FulfillBackorderRequest request
    ) {
        return orderCustomBundleService.handover(orderId, request);
    }
}
