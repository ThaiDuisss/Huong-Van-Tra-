package org.hvtra.iventoryservice.shared.security;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Phạm vi xem danh sách phiếu (nhập kho, nhập kệ, yêu cầu sản xuất) theo role của người đang đăng nhập.
 * <ul>
 *     <li>SUPER_ADMIN, MANAGER: xem toàn bộ.</li>
 *     <li>QC: phiếu chờ kiểm định theo từng loại phiếu (phiếu nhập kho: APPROVED, RECEIVING; phiếu nhập kệ:
 *     RECEIVING) hoặc phiếu do chính họ QC ({@code qc_by}).</li>
 *     <li>INVENTORY_STAFF (và role khác không được liệt kê): chỉ phiếu do chính họ tạo ({@code created_by}).</li>
 * </ul>
 * Người có nhiều role được xem hợp các phạm vi.
 *
 * @param seeAll   xem toàn bộ
 * @param qcScope  áp phạm vi QC
 * @param ownScope áp phạm vi phiếu tự tạo
 * @param userId   id người đang đăng nhập (có thể null nếu principal không có id hợp lệ)
 */
public record DocumentVisibility(boolean seeAll, boolean qcScope, boolean ownScope, UUID userId) {

    public static DocumentVisibility current() {
        if (SecurityUtils.hasAnyRole(Role.SUPER_ADMIN, Role.MANAGER)) {
            return new DocumentVisibility(true, false, false, null);
        }
        boolean qc = SecurityUtils.hasRole(Role.QC);
        boolean own = SecurityUtils.hasRole(Role.INVENTORY_STAFF) || !qc;
        return new DocumentVisibility(false, qc, own, parseUuid(SecurityUtils.currentUserId()));
    }

    /**
     * Điều kiện Criteria cho phạm vi xem.
     *
     * @param createdBy cột người tạo
     * @param qcVisible điều kiện "QC được xem"; null nếu loại phiếu không có bước QC (QC không xem được phiếu nào)
     */
    public Predicate toPredicate(CriteriaBuilder cb, Expression<UUID> createdBy, Supplier<Predicate> qcVisible) {
        if (seeAll) {
            return cb.conjunction();
        }
        List<Predicate> allowed = new ArrayList<>();
        if (ownScope && userId != null) {
            allowed.add(cb.equal(createdBy, userId));
        }
        if (qcScope && qcVisible != null) {
            allowed.add(qcVisible.get());
        }
        return allowed.isEmpty() ? cb.disjunction() : cb.or(allowed.toArray(Predicate[]::new));
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
