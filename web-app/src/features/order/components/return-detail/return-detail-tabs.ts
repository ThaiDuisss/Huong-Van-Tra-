export type DetailTab = "overview" | "items" | "inspection" | "refund";

export function getVisibleReturnDetailTabs(
    itemCount: number,
    overviewOnly: boolean
): { key: DetailTab; label: string }[] {
    if (overviewOnly) {
        return [{ key: "overview", label: "Tổng quan" }];
    }

    return [
        { key: "overview", label: "Tổng quan" },
        { key: "items", label: `Sản phẩm (${itemCount})` },
        { key: "inspection", label: "Kiểm hàng" },
        { key: "refund", label: "Hoàn tiền" }
    ];
}

export function resolveReturnDetailTab(requestedTab: DetailTab, overviewOnly: boolean): DetailTab {
    return overviewOnly ? "overview" : requestedTab;
}
