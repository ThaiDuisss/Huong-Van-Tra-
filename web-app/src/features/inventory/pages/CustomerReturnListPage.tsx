import { useState } from "react";
import { TimeSidebar } from "@/components/shared/TimeSidebar";
import { useTimeFilter } from "@/components/shared/useTimeFilter";

import orderStyles from "../../order/pages/OrderListPage.module.css";
import { CustomerReturnQcQueue } from "../components/CustomerReturnQcQueue";
import styles from "./CustomerReturnListPage.module.css";

export function CustomerReturnListPage() {
    const [returnPage, setReturnPage] = useState(0);
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
    const { selectedTimeNode, selectTimeNode, timeRange, timeZone } = useTimeFilter(() => setReturnPage(0));

    return (
        <section className={`${orderStyles.page} ${isTimeCollapsed ? orderStyles.pageTimeCollapsed : ""}`}>
            <TimeSidebar
                activeDate={timeRange?.selectedDate ?? undefined}
                collapsed={isTimeCollapsed}
                enableCalendar
                onClear={() => selectTimeNode(null)}
                onSelect={selectTimeNode}
                onToggleCollapse={() => setIsTimeCollapsed((collapsed) => !collapsed)}
                selectedNode={selectedTimeNode}
                timeZone={timeZone}
            />
            <div className={styles.content}>
                <header className={orderStyles.header}>
                    <div className={orderStyles.title}>
                        <h1>Khách trả hàng</h1>
                        <p>Theo dõi và xử lý hàng khách trả.</p>
                    </div>
                </header>
                <CustomerReturnQcQueue
                    onPageChange={setReturnPage}
                    page={returnPage}
                    timeRange={timeRange}
                />
            </div>
        </section>
    );
}
