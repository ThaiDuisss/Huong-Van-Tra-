import { Plus, RefreshCw } from "lucide-react";
import { useMemo, useState } from "react";

import { DiscountCodeFilters } from "../components/discount-codes/DiscountCodeFilters";
import { DiscountCodeTable } from "../components/discount-codes/DiscountCodeTable";
import styles from "../components/discount-programs/DiscountProgramPage.module.css";
import { discountCodes } from "../mock/discount-codes.mock";
import {
    defaultDiscountCodeFilters,
    filterDiscountCodes,
    getUniquePrograms
} from "../utils/discount-codes";

export function DiscountCodeListPage() {
    const [query, setQuery] = useState("");
    const [filters, setFilters] = useState(
        defaultDiscountCodeFilters
    );
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const programOptions = useMemo(
        () => getUniquePrograms(discountCodes),
        []
    );

    const filteredCodes = useMemo(
        () => filterDiscountCodes(discountCodes, query, filters),
        [query, filters]
    );

    const pageCount = Math.max(
        1,
        Math.ceil(filteredCodes.length / pageSize)
    );
    const safePage = Math.min(page, pageCount);
    const pagedCodes = filteredCodes.slice(
        (safePage - 1) * pageSize,
        safePage * pageSize
    );

    function updateQuery(value: string) {
        setQuery(value);
        setPage(1);
    }

    function updateFilters(
        nextFilters: typeof defaultDiscountCodeFilters
    ) {
        setFilters(nextFilters);
        setPage(1);
    }

    function updatePageSize(nextPageSize: number) {
        setPageSize(nextPageSize);
        setPage(1);
    }

    return (
        <section className={styles.page}>
            <header className={styles.header}>
                <div className={styles.titleBlock}>
                    <nav
                        className="flex items-center gap-2 text-sm"
                        aria-label="Breadcrumb"
                    >
                        <span className="font-bold text-[#009b4d]">
                            Marketing
                        </span>
                        <span aria-hidden="true">›</span>
                        <span className="font-bold text-[#17213a]">
                            Mã giảm giá
                        </span>
                    </nav>
                    <h1 className={styles.title}>Mã giảm giá</h1>
                    <p className={styles.description}>
                        Quản lý tất cả coupon phát hành trong hệ thống.
                    </p>
                </div>

                <div className={styles.actions}>
                    <button className={styles.primaryButton} type="button">
                        <Plus size={18} aria-hidden="true" />
                        Tạo mã giảm giá
                    </button>
                    <button
                        className={styles.secondaryButton}
                        type="button"
                    >
                        <RefreshCw size={17} aria-hidden="true" />
                        Tải lại
                    </button>
                </div>
            </header>

            <DiscountCodeFilters
                query={query}
                filters={filters}
                programOptions={programOptions}
                onQueryChange={updateQuery}
                onFiltersChange={updateFilters}
            />

            <DiscountCodeTable
                codes={pagedCodes}
                page={safePage}
                pageCount={pageCount}
                totalItems={filteredCodes.length}
                pageSize={pageSize}
                onPageChange={setPage}
                onPageSizeChange={updatePageSize}
            />
        </section>
    );
}
