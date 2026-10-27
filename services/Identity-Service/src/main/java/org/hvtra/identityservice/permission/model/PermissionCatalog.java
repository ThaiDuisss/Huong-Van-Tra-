package org.hvtra.identityservice.permission.model;

import org.hvtra.identityservice.account.model.StaffRole;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Danh mục permission mặc định của hệ thống và bộ gán mặc định cho từng vai trò nghiệp vụ.
 * Chỉ dùng khi khởi tạo (bootstrap): tạo những permission còn thiếu và thêm vào composite role còn thiếu,
 * không bao giờ gỡ gì đã có. Sau đó quản trị viên chỉnh qua giao diện.
 */
public final class PermissionCatalog {

    public static final String PERMISSION_MANAGE = "permission:manage";
    public static final String ROLE_MANAGE = "role:manage";
    public static final String USER_MANAGE = "user:manage";
    public static final String ORDER_CREATE = "order:create";

    /** Attribute của client role dùng lưu tên hiển thị. */
    public static final String ATTRIBUTE_DISPLAY_NAME = "displayName";

    public record DefaultPermission(String name, String displayName, String description) {
    }

    public static final List<DefaultPermission> DEFAULT_PERMISSIONS = List.of(
            new DefaultPermission("order:view", "Xem đơn hàng", "Xem danh sách, chi tiết và lịch sử đơn bán"),
            new DefaultPermission(ORDER_CREATE, "Tạo đơn bán", "Bán hàng tại quầy POS, tạo và thanh toán đơn"),
            new DefaultPermission("order:cancel", "Hủy đơn", "Hủy đơn hàng chưa hoàn tất"),
            new DefaultPermission("order:return", "Xử lý trả hàng", "Tạo và duyệt phiếu khách trả hàng"),
            new DefaultPermission("order:refund", "Hoàn tiền", "Thực hiện hoàn tiền cho phiếu trả hàng"),
            new DefaultPermission("inventory:view", "Xem tồn kho", "Xem tồn kho, lô hàng, lịch sử biến động"),
            new DefaultPermission("inventory:receive", "Nhập kho", "Lập phiếu nhập và xác nhận nhận hàng"),
            new DefaultPermission("inventory:approve", "Duyệt phiếu kho", "Phê duyệt phiếu nhập, phiếu điều chỉnh"),
            new DefaultPermission("inventory:adjust", "Kiểm kê và điều chỉnh", "Kiểm kê, điều chỉnh tồn, xử lý hàng hỏng"),
            new DefaultPermission("inventory:transfer", "Chuyển kho", "Chuyển hàng giữa kho tổng, kho cửa hàng và kệ"),
            new DefaultPermission("product:view", "Xem sản phẩm", "Xem danh mục sản phẩm, mặt hàng, giá"),
            new DefaultPermission("product:manage", "Quản lý sản phẩm", "Tạo, sửa sản phẩm, mặt hàng, đơn vị, bảng giá"),
            new DefaultPermission("production:view", "Xem sản xuất", "Xem lệnh sản xuất, nguyên liệu, BOM"),
            new DefaultPermission("production:manage", "Quản lý sản xuất", "Tạo và duyệt lệnh sản xuất, nhà cung cấp"),
            new DefaultPermission("customer:view", "Xem khách hàng", "Tra cứu khách hàng tại quầy"),
            new DefaultPermission("customer:manage", "Quản lý khách hàng", "Tạo, sửa khách hàng, hạng thành viên"),
            new DefaultPermission("promotion:manage", "Quản lý khuyến mãi", "Chương trình giảm giá, mã giảm giá, sự kiện"),
            new DefaultPermission("report:view", "Xem báo cáo", "Dashboard và các báo cáo thống kê"),
            new DefaultPermission(USER_MANAGE, "Quản lý tài khoản", "Tạo, sửa, khóa tài khoản và gán vai trò cho nhân viên"),
            new DefaultPermission(ROLE_MANAGE, "Quản lý vai trò", "Gán quyền cho vai trò nghiệp vụ"),
            new DefaultPermission(PERMISSION_MANAGE, "Quản lý quyền", "Tạo, sửa, xóa định nghĩa quyền")
    );

    private static final Set<String> MANAGER_PERMISSIONS = Set.of(
            "order:view", ORDER_CREATE, "order:cancel", "order:return", "order:refund",
            "inventory:view", "inventory:approve", "inventory:transfer",
            "product:view", "product:manage",
            "production:view", "production:manage",
            "customer:view", "customer:manage",
            "promotion:manage", "report:view"
    );

    /** Gán mặc định cho từng vai trò; SUPER_ADMIN cố ý không có ở đây (không quản lý qua API). */
    public static final Map<StaffRole, Set<String>> DEFAULT_ROLE_PERMISSIONS = defaults();

    private static Map<StaffRole, Set<String>> defaults() {
        Map<StaffRole, Set<String>> map = new LinkedHashMap<>();
        Set<String> admin = new java.util.LinkedHashSet<>(MANAGER_PERMISSIONS);
        admin.addAll(Set.of("inventory:receive", "inventory:adjust", USER_MANAGE, ROLE_MANAGE, PERMISSION_MANAGE));
        map.put(StaffRole.ADMIN, Set.copyOf(admin));
        map.put(StaffRole.MANAGER, MANAGER_PERMISSIONS);
        map.put(StaffRole.INVENTORY_STAFF, Set.of(
                "inventory:view", "inventory:receive", "inventory:adjust", "inventory:transfer",
                "product:view", "production:view"));
        map.put(StaffRole.SALE_STAFF, Set.of(
                "order:view", ORDER_CREATE, "order:return", "inventory:view", "product:view", "customer:view"));
        map.put(StaffRole.QC, Set.of("inventory:view", "inventory:adjust", "production:view", "order:return"));
        map.put(StaffRole.ACCOUNTANT, Set.of("order:view", "order:refund", "inventory:view", "report:view"));
        map.put(StaffRole.DELIVERY, Set.of("order:view"));
        return Map.copyOf(map);
    }

    private PermissionCatalog() {
    }
}
