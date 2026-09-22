package org.hvtra.orderservice.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.exception.InventoryReservationFailedException;
import org.hvtra.orderservice.exception.OutOfStockException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderInventoryReservationService {

    private final InventoryClient inventoryClient;

    public void applyReservation(Order order, UUID productVariantId, BigDecimal targetQuantity, UUID locationId) {
        try {
            inventoryClient.applyReservation(InventoryClient.ApplyInventoryReservationRequest.builder()
                    .orderId(order.getId())
                    .branchId(order.getBranchId())
                    .productVariantId(productVariantId)
                    .locationId(locationId)
                    .targetQuantity(targetQuantity)
                    .expiresAt(order.getExpiresAt())
                    .build());
        } catch (FeignException.Conflict ex) {
            throw new OutOfStockException("Không đủ tồn kho");
        } catch (FeignException.NotFound ex) {
            throw new InventoryReservationFailedException("Không tìm thấy tồn kho để giữ chỗ");
        } catch (FeignClientException ex) {
            if (isOutOfStock(ex)) {
                throw new OutOfStockException("Không đủ tồn kho");
            }
            if (ex.getStatus() == 404) {
                throw new InventoryReservationFailedException("Không tìm thấy tồn kho để giữ chỗ");
            }
            throw new InventoryReservationFailedException("Không thể giữ chỗ tồn kho: " + ex.getMessage());
        } catch (FeignException ex) {
            throw new InventoryReservationFailedException("Không thể giữ chỗ tồn kho: " + ex.getMessage());
        }
    }

    public void releaseItemReservation(UUID orderId, UUID productVariantId, UUID locationId) {
        try {
            inventoryClient.releaseItem(InventoryClient.ReleaseInventoryReservationItemRequest.builder()
                    .orderId(orderId)
                    .productVariantId(productVariantId)
                    .locationId(locationId)
                    .build());
        } catch (FeignClientException ex) {
            throw new InventoryReservationFailedException("Không thể nhả giữ chỗ tồn kho: " + ex.getMessage());
        } catch (FeignException ex) {
            throw new InventoryReservationFailedException("Không thể nhả giữ chỗ tồn kho: " + ex.getMessage());
        }
    }

    public void releaseOrderReservation(UUID orderId) {
        try {
            inventoryClient.releaseOrder(InventoryClient.ReleaseInventoryReservationOrderRequest.builder()
                    .orderId(orderId)
                    .build());
        } catch (FeignClientException ex) {
            throw new InventoryReservationFailedException("Không thể nhả giữ chỗ tồn kho của đơn: " + ex.getMessage());
        } catch (FeignException ex) {
            throw new InventoryReservationFailedException("Không thể nhả giữ chỗ tồn kho của đơn: " + ex.getMessage());
        }
    }

    private boolean isOutOfStock(FeignClientException ex) {
        return ex.getStatus() == 409
                || (ex.getMessage() != null && ex.getMessage().contains("OUT_OF_STOCK"))
                || (ex.getResponseBody() != null && ex.getResponseBody().contains("OUT_OF_STOCK"));
    }
}
