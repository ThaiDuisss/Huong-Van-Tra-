import type { CustomerSort, CustomerStatus } from "../types/customer.types";
import type {
    CustomerFilterDraft,
    CustomerPrimaryTab,
    CustomerQuickViewId
} from "../types/customer-list.types";

export const EMPTY_CUSTOMER_FILTER: CustomerFilterDraft = {
    status: "",
    tierIds: [],
    createdFrom: "",
    createdTo: "",
    lastPurchaseFrom: "",
    lastPurchaseTo: "",
    includeNeverPurchased: false,
    hasPurchased: "",
    birthMonth: "",
    birthYear: ""
};

export const CUSTOMER_SORT_OPTIONS: Array<{
    value: CustomerSort;
    label: string;
}> = [
    { value: "lastPurchaseDesc", label: "Lần mua gần nhất" },
    { value: "lastPurchaseAsc", label: "Lần mua lâu nhất" },
    { value: "createdDesc", label: "Khách hàng mới nhất" },
    { value: "createdAsc", label: "Khách hàng cũ nhất" },
    { value: "nameAsc", label: "Tên A - Z" },
    { value: "nameDesc", label: "Tên Z - A" }
];

export const CUSTOMER_PRIMARY_TABS: Array<{
    id: CustomerPrimaryTab;
    label: string;
}> = [
    { id: "all", label: "Tất cả" },
    { id: "care", label: "Cần chăm sóc" },
    { id: "loyal", label: "Khách thân thiết" },
    { id: "new", label: "Khách mới" },
    { id: "inactive", label: "Không hoạt động" }
];

export const CUSTOMER_SECONDARY_TABS: Record<
    CustomerPrimaryTab,
    Array<{ id: string; label: string }>
> = {
    all: [
        { id: "all", label: "Tất cả" },
        { id: "has-tier", label: "Có hạng thành viên" },
        { id: "no-tier", label: "Chưa có hạng" },
        { id: "purchased", label: "Đã từng mua" },
        { id: "never-purchased", label: "Chưa từng mua" }
    ],
    care: [
        { id: "all", label: "Tất cả" },
        { id: "vip-drop", label: "VIP giảm mua" },
        { id: "birthday", label: "Sinh nhật" },
        { id: "one-order", label: "Mới mua 1 lần" },
        { id: "long-time", label: "Lâu chưa quay lại" }
    ],
    loyal: [
        { id: "all", label: "Tất cả" },
        { id: "diamond", label: "Kim cương" },
        { id: "platinum", label: "Bạch kim" },
        { id: "gold", label: "Vàng" },
        { id: "silver", label: "Bạc" }
    ],
    new: [
        { id: "all", label: "Tất cả" },
        { id: "today", label: "Hôm nay" },
        { id: "week", label: "7 ngày" },
        { id: "first-order", label: "Đã mua lần đầu" },
        { id: "no-order", label: "Chưa mua" }
    ],
    inactive: [
        { id: "all", label: "Tất cả" },
        { id: "30-days", label: "30 ngày" },
        { id: "60-days", label: "60 ngày" },
        { id: "90-days", label: "90 ngày" },
        { id: "never-purchased", label: "Chưa từng mua" }
    ]
};

export const CUSTOMER_DEFAULT_SORT_BY_TAB: Record<CustomerPrimaryTab, CustomerSort> = {
    all: "lastPurchaseDesc",
    care: "lastPurchaseAsc",
    loyal: "lastPurchaseDesc",
    new: "createdDesc",
    inactive: "lastPurchaseAsc"
};

export const CUSTOMER_STATUS_LABELS: Record<CustomerStatus, string> = {
    ACTIVE: "Hoạt động",
    INACTIVE: "Không hoạt động",
    SUSPENDED: "Tạm khóa"
};

export const CUSTOMER_STATUS_CLASS_NAMES: Record<CustomerStatus, string> = {
    ACTIVE: "bg-[#e9f8ed] text-[#159734]",
    INACTIVE: "bg-[#f1f3f5] text-[#657282]",
    SUSPENDED: "bg-[#fff0ed] text-[#c4422e]"
};

export const CUSTOMER_QUICK_VIEWS: Array<{
    id: CustomerQuickViewId;
    label: string;
}> = [
    { id: "all", label: "Tất cả" },
    { id: "new", label: "Mới tạo" },
    { id: "recent", label: "Mua gần đây" },
    { id: "vip", label: "Khách thân thiết" },
    { id: "never-purchased", label: "Chưa từng mua" },
    { id: "birthday", label: "Sinh nhật tháng này" },
    { id: "inactive", label: "Không hoạt động" }
];

export const CUSTOMER_BUTTON_BASE =
    "inline-flex h-10 items-center justify-center gap-2 whitespace-nowrap rounded-lg px-3.5 text-[13px] font-extrabold transition-colors";
export const CUSTOMER_OUTLINE_BUTTON =
    "border border-[#bfd5c6] bg-white text-[#159734] hover:bg-[#f7fff9]";
export const CUSTOMER_COMPACT_BUTTON =
    "border border-[#d9e3df] bg-white text-[#273a2b] hover:bg-[#f7fbf8]";
export const CUSTOMER_FIELD_CLASS_NAME =
    "h-9 w-full rounded-lg border border-[#d9e3df] bg-white px-2.5 text-xs font-semibold text-[#273a2b] outline-none focus:border-[#159734] focus:ring-2 focus:ring-[#159734]/10";
