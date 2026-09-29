import { useEffect, useState } from "react";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import { getDateRangeFromTimeNode, type TimeNode } from "../../../components/shared/TimeHierarchyFilter.utils";
import orderStyles from "../../order/pages/OrderListPage.module.css";
import { CustomerReturnQcQueue } from "../components/CustomerReturnQcQueue";
import { InspectionFilterBar, InspectionTable } from "../components/CustomerReturnInspectionComponents";
import { ListMessage, ListPagination } from "../components/CustomerReturnListUi";
import { useCustomerReturnQueryState } from "../hooks/useCustomerReturnQueryState";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { customerReturnApi } from "../services/customerReturn.api";
import type { CustomerReturnInspectionListResult } from "../types/customer-return-inspection.types";
import styles from "./CustomerReturnListPage.module.css";

export function CustomerReturnListPage() {
    const { query, resetQuery, setQuery } = useCustomerReturnQueryState();
    const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(null);
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(() => window.innerWidth < 1024);
    const [returnPage, setReturnPage] = useState(0);
    const [result, setResult] = useState<CustomerReturnInspectionListResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [reloadKey, setReloadKey] = useState(0);
    const debouncedReturnCode = useDebouncedValue(query.returnCode.trim());
    const debouncedOrderCode = useDebouncedValue(query.orderCode.trim());
    const timeRange = getDateRangeFromTimeNode(selectedTimeNode);

    useEffect(() => {
        if (query.returnCode.trim() !== debouncedReturnCode || query.orderCode.trim() !== debouncedOrderCode) return;
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void customerReturnApi.listInspections({
                ...query,
                fromDate: timeRange?.fromDate,
                orderCode: debouncedOrderCode,
                returnCode: debouncedReturnCode,
                size: 10,
                toDate: timeRange?.toDate
            })
                .then((response) => { if (active) setResult(response); })
                .catch((loadError: unknown) => {
                    if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải danh sách kiểm định trả hàng.");
                })
                .finally(() => { if (active) setLoading(false); });
        });
        return () => { active = false; };
    }, [query, debouncedReturnCode, debouncedOrderCode, timeRange?.fromDate, timeRange?.toDate, reloadKey]);

    const changeTime = (node: TimeNode | null) => {
        setSelectedTimeNode(node);
        setReturnPage(0);
        setQuery({ page: 0 });
    };
    const inspections = result?.content ?? [];

    return (
        <section className={`${styles.page} ${isTimeCollapsed ? styles.collapsed : ""}`}>
            <TimeSidebar
                activeDate={timeRange?.selectedDate ?? undefined}
                collapsed={isTimeCollapsed}
                enableCalendar
                onClear={() => changeTime(null)}
                onSelect={changeTime}
                onToggleCollapse={() => setIsTimeCollapsed((value) => !value)}
                selectedNode={selectedTimeNode}
            />
            <div className={styles.content}>
                <header className={orderStyles.header}>
                    <div className={orderStyles.title}>
                        <p>Quản lý / Kho</p>
                        <h1>Khách trả hàng</h1>
                        <p>Theo dõi phiếu hoàn, kiểm định và trạng thái xử lý hàng khách trả.</p>
                    </div>
                </header>
                <CustomerReturnQcQueue
                    onInspectionCreated={() => setReloadKey((key) => key + 1)}
                    onPageChange={setReturnPage}
                    page={returnPage}
                    timeRange={timeRange}
                />
                <section className={styles.section}>
                    <h2 className={styles.sectionTitle}>Phiếu kiểm định đã tạo</h2>
                    <InspectionFilterBar onChange={setQuery} onReset={resetQuery} query={query} />
                    {error ? <div className={styles.error}>{error}</div> : null}
                    <div className={orderStyles.tableCard}>
                        {loading || error || inspections.length === 0 ? (
                            <div className={orderStyles.tableWrap}>
                                <table className={`${orderStyles.orderTable} ${styles.dataTable} ${styles.inspectionTable}`}>
                                    <thead><tr><th>Phiếu kiểm định</th><th>Mã trả hàng</th><th>Đơn hàng</th><th>Số dòng</th><th>Tổng số lượng</th><th>Trạng thái</th><th>Ngày tạo</th></tr></thead>
                                    <tbody><ListMessage colSpan={7} message={loading ? "Đang tải phiếu kiểm định..." : error ? "Không thể hiển thị phiếu kiểm định" : "Không có phiếu kiểm định trả hàng phù hợp"} /></tbody>
                                </table>
                            </div>
                        ) : <InspectionTable inspections={inspections} />}
                        {!loading && !error ? <ListPagination itemLabel="phiếu kiểm định" onPageChange={(page) => setQuery({ page })} page={query.page} pageSize={10} totalItems={result?.totalElements ?? 0} totalPages={result?.totalPages ?? 0} /> : null}
                    </div>
                </section>
            </div>
        </section>
    );
}
