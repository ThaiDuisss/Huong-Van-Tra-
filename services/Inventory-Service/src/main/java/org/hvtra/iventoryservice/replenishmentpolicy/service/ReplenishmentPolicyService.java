package org.hvtra.iventoryservice.replenishmentpolicy.service;

import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.replenishmentpolicy.dto.request.ReplenishmentPolicyRequest;
import org.hvtra.iventoryservice.replenishmentpolicy.dto.response.ReplenishmentPolicyMatrixRowResponse;
import org.hvtra.iventoryservice.replenishmentpolicy.dto.response.ReplenishmentPolicyResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface ReplenishmentPolicyService {

    PageResponse<ReplenishmentPolicyResponse> findAll(
            UUID inventoryItemId,
            InventoryLocationType locationType,
            InventoryItemType itemType,
            Boolean enabled,
            String search,
            Pageable pageable
    );

    PageResponse<ReplenishmentPolicyMatrixRowResponse> findMatrix(
            InventoryItemType itemType,
            String search,
            Pageable pageable
    );

    ReplenishmentPolicyResponse findById(UUID id);

    ReplenishmentPolicyResponse create(ReplenishmentPolicyRequest request);

    ReplenishmentPolicyResponse update(UUID id, ReplenishmentPolicyRequest request);

    ReplenishmentPolicyResponse toggleEnabled(UUID id, boolean enabled);

    void delete(UUID id);

    /**
     * Lấy ngưỡng tồn tối thiểu hiệu lực theo (inventoryItemId, locationType):
     * ưu tiên policy đang bật của loại vị trí (WAREHOUSE / SHELF), nếu chưa có thì fallback về InventoryItem.reorderPoint.
     */
    BigDecimal resolveEffectiveMinimumStock(UUID inventoryItemId, InventoryLocationType locationType);
}
