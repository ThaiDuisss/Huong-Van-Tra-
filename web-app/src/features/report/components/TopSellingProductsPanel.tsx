import { useState } from "react";

import { Field, NativeSelect } from "../../../components/ui/Field";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import { useStatisticReport } from "../hooks/useStatisticReport";
import styles from "../pages/StatisticReportPage.module.css";
import {
    exportTopSellingProductsReport,
    getTopSellingProductsReport
} from "../services/statistic-report.api";
import type { ProductSortBy } from "../types/statistic-report.types";
import {
    formatCount,
    formatCurrency,
    formatQuantity,
    toNumber
} from "../utils/statistic-report.utils";
import { ReportExportButton } from "./ReportExportButton";
import { BarValue, Metric, RankBadge, StateRow } from "./ReportParts";

interface ReportPanelProps {
    enabled: boolean;
    fromDate: string;
    limit: number;
    toDate: string;
}

const COLUMN_COUNT = 7;

export function TopSellingProductsPanel({ enabled, fromDate, limit, toDate }: ReportPanelProps) {
    const [sortBy, setSortBy] = useState<ProductSortBy>("quantity");
    const { error, loading, report } = useStatisticReport(
        getTopSellingProductsReport,
        { fromDate, limit, sortBy, toDate },
        enabled
    );

    const rows = report?.rows ?? [];
    const maxQuantity = Math.max(...rows.map((row) => toNumber(row.quantitySold)), 0);
    const maxRevenue = Math.max(...rows.map((row) => toNumber(row.revenue)), 0);

    return (
        <>
            <section className={styles.summaryGrid}>
                <Metric label="Tổng số lượng bán" loading={loading} value={formatQuantity(report?.totalQuantitySold)} />
                <Metric label="Doanh thu sản phẩm" loading={loading} value={formatCurrency(report?.totalRevenue)} />
                <Metric label="Số đơn đã thanh toán" loading={loading} value={formatCount(report?.totalOrders)} />
                <Metric label="Sản phẩm bán được" loading={loading} value={formatCount(report?.totalProducts)} />
            </section>

            <section className={styles.tableCard}>
                <div className={styles.tableHeader}>
                    <div>
                        <h2>Sản phẩm bán chạy</h2>
                        <span className={styles.muted}>
                            Chỉ tính đơn đã thanh toán. Số lượng và doanh thu là số gộp; hàng trả hiển thị ở cột riêng.
                            File Excel có thêm tồn kho hiện tại.
                        </span>
                    </div>
                    <div className={styles.tableTools}>
                        <Field label="Xếp hạng theo">
                            <NativeSelect
                                onChange={(event) => setSortBy(event.target.value as ProductSortBy)}
                                value={sortBy}
                            >
                                <option value="quantity">Số lượng bán</option>
                                <option value="revenue">Doanh thu</option>
                            </NativeSelect>
                        </Field>
                        <ReportExportButton
                            disabled={!enabled || loading || rows.length === 0}
                            filenamePrefix="bao-cao-san-pham-ban-chay-ton-kho"
                            onExport={() =>
                                exportTopSellingProductsReport({ fromDate, limit, sortBy, toDate })
                            }
                        />
                    </div>
                </div>

                {error ? <div className={styles.errorBox}>{error}</div> : null}

                <div className={styles.tableWrap}>
                    <Table className="min-w-[860px]">
                        <TableHead>
                            <TableRow>
                                <TableHeaderCell className="w-16 text-center">Hạng</TableHeaderCell>
                                <TableHeaderCell>Sản phẩm</TableHeaderCell>
                                <TableHeaderCell>SKU</TableHeaderCell>
                                <TableHeaderCell className="text-right">Số lượng bán</TableHeaderCell>
                                <TableHeaderCell className="text-right">Doanh thu</TableHeaderCell>
                                <TableHeaderCell className="text-right">Số đơn</TableHeaderCell>
                                <TableHeaderCell className="text-right">SL đã trả</TableHeaderCell>
                            </TableRow>
                        </TableHead>
                        <tbody>
                            {loading ? (
                                <StateRow colSpan={COLUMN_COUNT} message="Đang tải dữ liệu báo cáo..." />
                            ) : null}
                            {!loading && !error
                                ? rows.map((row) => (
                                      <TableRow className="hover:bg-[#f8fbf8]" key={row.productVariantId}>
                                          <TableCell className="w-16 text-center">
                                              <RankBadge rank={row.rank} />
                                          </TableCell>
                                          <TableCell>
                                              <strong>{row.productName?.trim() || "--"}</strong>
                                              {row.variantName?.trim() ? (
                                                  <div className={styles.muted}>{row.variantName}</div>
                                              ) : null}
                                          </TableCell>
                                          <TableCell>{row.sku?.trim() || "--"}</TableCell>
                                          <TableCell className="text-right tabular-nums">
                                              {sortBy === "quantity" ? (
                                                  <BarValue max={maxQuantity} value={toNumber(row.quantitySold)}>
                                                      {formatQuantity(row.quantitySold)}
                                                  </BarValue>
                                              ) : (
                                                  formatQuantity(row.quantitySold)
                                              )}
                                          </TableCell>
                                          <TableCell className="text-right tabular-nums">
                                              {sortBy === "revenue" ? (
                                                  <BarValue max={maxRevenue} value={toNumber(row.revenue)}>
                                                      {formatCurrency(row.revenue)}
                                                  </BarValue>
                                              ) : (
                                                  formatCurrency(row.revenue)
                                              )}
                                          </TableCell>
                                          <TableCell className="text-right tabular-nums">{formatCount(row.orderCount)}</TableCell>
                                          <TableCell className="text-right tabular-nums">{formatQuantity(row.returnedQuantity)}</TableCell>
                                      </TableRow>
                                  ))
                                : null}
                            {!loading && !error && rows.length === 0 ? (
                                <StateRow
                                    colSpan={COLUMN_COUNT}
                                    message={
                                        enabled
                                            ? "Không có sản phẩm nào được bán trong khoảng thời gian này."
                                            : "Vui lòng chọn khoảng thời gian hợp lệ."
                                    }
                                />
                            ) : null}
                        </tbody>
                    </Table>
                </div>
            </section>
        </>
    );
}
