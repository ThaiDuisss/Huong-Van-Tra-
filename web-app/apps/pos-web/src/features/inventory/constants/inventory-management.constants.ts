import {
    ArchiveRestore,
    Boxes,
    ClipboardCheck,
    ClipboardList,
    Factory,
    FileText,
    PackagePlus,
    RefreshCw,
    ShieldCheck,
    Trash2,
    Truck,
    type LucideIcon
} from "lucide-react";

import type {
    InventoryActionType,
    InventoryDocumentType,
    InventoryRecordStatus,
    InventoryRole,
    InventoryWorkspaceConfig,
    InventoryWorkspaceId
} from "../types/inventory-management.types";

export const inventoryRoleLabels: Record<InventoryRole, string> = {
    INVENTORY_MANAGER: "Quản lý kho",
    WAREHOUSE_STAFF: "Nhân viên kho",
    PRODUCTION_STAFF: "Nhân viên sản xuất",
    BRANCH_STAFF: "Nhân viên chi nhánh",
    AUDITOR: "Kiểm kê viên"
};

export const inventoryRoleDescriptions: Record<InventoryRole, string> = {
    INVENTORY_MANAGER:
        "Duyệt chứng từ, điều phối bổ sung hàng và xử lý ngoại lệ tồn kho.",
    WAREHOUSE_STAFF:
        "Tạo chứng từ nhập, chuyển kho, trả nhà cung cấp và tiêu hủy.",
    PRODUCTION_STAFF:
        "Theo dõi lệnh sản xuất, cấp phát nguyên liệu và hoàn thành mẻ trà.",
    BRANCH_STAFF:
        "Tiếp nhận chuyển kho, ghi nhận khách trả hàng và yêu cầu bổ sung.",
    AUDITOR:
        "Thực hiện kiểm kê, đối soát chênh lệch và ghi nhận bằng chứng."
};

export const inventoryStatusLabels: Record<InventoryRecordStatus, string> = {
    DRAFT: "Nháp",
    PENDING_APPROVAL: "Chờ duyệt",
    APPROVED: "Đã duyệt",
    IN_PROGRESS: "Đang xử lý",
    COMPLETED: "Hoàn tất",
    REJECTED: "Từ chối",
    CANCELLED: "Đã hủy",
    WARNING: "Cần chú ý",
    BLOCKED: "Tạm khóa"
};

export const inventoryDocumentTypeLabels: Record<
    InventoryDocumentType,
    string
> = {
    STOCK_RECEIPT: "Phiếu nhập kho",
    PRODUCTION_ORDER: "Lệnh sản xuất",
    BUNDLE_ORDER: "Phiếu phối bundle",
    TRANSFER_ORDER: "Phiếu chuyển kho",
    STOCK_COUNT: "Phiếu kiểm kê",
    CUSTOMER_RETURN: "Phiếu khách trả hàng",
    SUPPLIER_RETURN: "Phiếu trả nhà cung cấp",
    DISPOSAL: "Phiếu tiêu hủy",
    REPLENISHMENT: "Đề xuất bổ sung"
};

export const inventoryActionLabels: Record<InventoryActionType, string> = {
    CREATE: "Tạo mới",
    APPROVE: "Duyệt",
    COMPLETE: "Hoàn tất",
    CANCEL: "Hủy",
    EXPORT: "Xuất dữ liệu"
};

const managerRoles: InventoryRole[] = ["INVENTORY_MANAGER"];
const warehouseRoles: InventoryRole[] = [
    "INVENTORY_MANAGER",
    "WAREHOUSE_STAFF"
];
const productionRoles: InventoryRole[] = [
    "INVENTORY_MANAGER",
    "PRODUCTION_STAFF"
];
const branchRoles: InventoryRole[] = [
    "INVENTORY_MANAGER",
    "BRANCH_STAFF"
];
const auditorRoles: InventoryRole[] = ["INVENTORY_MANAGER", "AUDITOR"];

export const inventoryWorkspaceConfigs: InventoryWorkspaceConfig[] = [
    {
        id: "roles",
        title: "Mô phỏng người dùng và phân quyền",
        description:
            "Chuyển vai trò thủ công để kiểm tra hành động hiển thị theo nghiệp vụ. Đây không phải lớp bảo mật frontend.",
        allowedActions: {
            EXPORT: managerRoles
        }
    },
    {
        id: "documents",
        title: "Hệ thống chứng từ kho",
        description:
            "Theo dõi toàn bộ chứng từ phát sinh trong vận hành kho và trạng thái phê duyệt.",
        createLabel: "Tạo chứng từ",
        documentType: "STOCK_RECEIPT",
        allowedActions: {
            CREATE: warehouseRoles,
            APPROVE: managerRoles,
            COMPLETE: warehouseRoles,
            CANCEL: managerRoles,
            EXPORT: warehouseRoles
        }
    },
    {
        id: "raw-material-receipts",
        title: "Nhập nguyên liệu",
        description:
            "Ghi nhận nguyên liệu trà, bao bì và phụ liệu nhập từ nhà cung cấp.",
        createLabel: "Tạo phiếu nhập",
        documentType: "STOCK_RECEIPT",
        allowedActions: {
            CREATE: warehouseRoles,
            APPROVE: managerRoles,
            COMPLETE: warehouseRoles,
            CANCEL: managerRoles,
            EXPORT: warehouseRoles
        }
    },
    {
        id: "production-orders",
        title: "Production Order",
        description:
            "Quản lý cấp phát nguyên liệu, tiến độ sản xuất và nhập thành phẩm.",
        createLabel: "Tạo lệnh sản xuất",
        documentType: "PRODUCTION_ORDER",
        allowedActions: {
            CREATE: productionRoles,
            APPROVE: managerRoles,
            COMPLETE: productionRoles,
            CANCEL: managerRoles,
            EXPORT: productionRoles
        }
    },
    {
        id: "tea-bundles",
        title: "Custom Tea Bundle",
        description:
            "Tạo bundle trà theo yêu cầu, cấp phát batch và theo dõi đóng gói.",
        createLabel: "Tạo bundle",
        documentType: "BUNDLE_ORDER",
        allowedActions: {
            CREATE: productionRoles,
            APPROVE: managerRoles,
            COMPLETE: productionRoles,
            CANCEL: managerRoles,
            EXPORT: productionRoles
        }
    },
    {
        id: "branch-transfers",
        title: "Chuyển kho trung tâm đến chi nhánh",
        description:
            "Điều phối hàng từ kho trung tâm đến chi nhánh và xác nhận tiếp nhận.",
        createLabel: "Tạo phiếu chuyển",
        documentType: "TRANSFER_ORDER",
        allowedActions: {
            CREATE: warehouseRoles,
            APPROVE: managerRoles,
            COMPLETE: branchRoles,
            CANCEL: managerRoles,
            EXPORT: warehouseRoles
        }
    },
    {
        id: "stock-counts",
        title: "Kiểm kê kho",
        description:
            "Lập phiên kiểm kê, đối chiếu chênh lệch và lưu bằng chứng kiểm kê.",
        createLabel: "Tạo phiên kiểm kê",
        documentType: "STOCK_COUNT",
        allowedActions: {
            CREATE: auditorRoles,
            APPROVE: managerRoles,
            COMPLETE: auditorRoles,
            CANCEL: managerRoles,
            EXPORT: auditorRoles
        }
    },
    {
        id: "customer-returns",
        title: "Khách hàng trả hàng",
        description:
            "Tiếp nhận hàng trả, phân loại nhập lại, cách ly hoặc chuyển xử lý hỏng.",
        createLabel: "Ghi nhận trả hàng",
        documentType: "CUSTOMER_RETURN",
        allowedActions: {
            CREATE: branchRoles,
            APPROVE: managerRoles,
            COMPLETE: branchRoles,
            CANCEL: managerRoles,
            EXPORT: branchRoles
        }
    },
    {
        id: "supplier-returns",
        title: "Trả hàng nhà cung cấp",
        description:
            "Quản lý hàng không đạt chuẩn, lập phiếu trả và theo dõi xác nhận NCC.",
        createLabel: "Tạo phiếu trả NCC",
        documentType: "SUPPLIER_RETURN",
        allowedActions: {
            CREATE: warehouseRoles,
            APPROVE: managerRoles,
            COMPLETE: warehouseRoles,
            CANCEL: managerRoles,
            EXPORT: warehouseRoles
        }
    },
    {
        id: "waste-handling",
        title: "Hàng sắp hết hạn, hết hạn, hỏng và tiêu hủy",
        description:
            "Theo dõi batch rủi ro, ghi nhận nguyên nhân và xử lý tiêu hủy có phê duyệt.",
        createLabel: "Tạo phiếu xử lý",
        documentType: "DISPOSAL",
        allowedActions: {
            CREATE: warehouseRoles,
            APPROVE: managerRoles,
            COMPLETE: warehouseRoles,
            CANCEL: managerRoles,
            EXPORT: warehouseRoles
        }
    },
    {
        id: "replenishment-alerts",
        title: "Cảnh báo tồn kho và bổ sung hàng",
        description:
            "Xem cảnh báo dưới định mức, đề xuất bổ sung và trạng thái đặt hàng.",
        createLabel: "Tạo đề xuất bổ sung",
        documentType: "REPLENISHMENT",
        allowedActions: {
            CREATE: branchRoles,
            APPROVE: managerRoles,
            COMPLETE: warehouseRoles,
            CANCEL: managerRoles,
            EXPORT: branchRoles
        }
    }
];

export const inventoryWorkspaceIcons: Record<
    InventoryWorkspaceId,
    LucideIcon
> = {
    roles: ShieldCheck,
    documents: FileText,
    "raw-material-receipts": PackagePlus,
    "production-orders": Factory,
    "tea-bundles": Boxes,
    "branch-transfers": Truck,
    "stock-counts": ClipboardCheck,
    "customer-returns": ArchiveRestore,
    "supplier-returns": RefreshCw,
    "waste-handling": Trash2,
    "replenishment-alerts": ClipboardList
};

export const inventoryStatusOptions: Array<
    "ALL" | InventoryRecordStatus
> = [
    "ALL",
    "DRAFT",
    "PENDING_APPROVAL",
    "APPROVED",
    "IN_PROGRESS",
    "COMPLETED",
    "WARNING",
    "BLOCKED",
    "CANCELLED"
];

export const inventoryReasonCodeLabels: Record<string, string> = {
    SUPPLIER_DELIVERY: "Nhập từ nhà cung cấp",
    PRODUCTION_USAGE: "Cấp phát sản xuất",
    CUSTOMER_RETURN: "Khách trả hàng",
    EXPIRED: "Hết hạn",
    DAMAGED: "Hàng hỏng",
    STOCK_COUNT_DIFF: "Chênh lệch kiểm kê",
    REPLENISHMENT: "Bổ sung dưới định mức"
};
