import { useState } from "react";

import orderStyles from "../../order/pages/OrderListPage.module.css";
import { CustomerReturnQcQueue } from "../components/CustomerReturnQcQueue";
import styles from "./CustomerReturnListPage.module.css";

export function CustomerReturnListPage() {
    const [returnPage, setReturnPage] = useState(0);

    return (
        <section className={styles.page}>
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
                />
            </div>
        </section>
    );
}
