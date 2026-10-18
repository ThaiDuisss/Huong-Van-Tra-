import { useState } from "react";

import { useStatisticReport } from "../hooks/useStatisticReport";
import styles from "../pages/StatisticReportPage.module.css";
import {
    exportTopSalesStaffReport,
    getTopSalesStaffReport
} from "../services/statistic-report.api";
import type { StaffSortBy } from "../types/statistic-report.types";
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

export function TopSalesStaffPanel({ enabled, fromDate, limit, toDate }: ReportPanelProps) {
    const [sortBy, setSortBy] = useState<StaffSortBy>("revenue");
    const { error, loading, report } = useStatisticReport(
        getTopSalesStaffReport,
        { fromDate, limit, sortBy, toDate },
        enabled
    );

    const rows = report?.rows ?? [];
    const maxRevenue = Math.max(...rows.map((row) => toNumber(row.revenue)), 0);
    const maxOrders = Math.max(...rows.map((row) => row.orderCount), 0);

    return (
        <>
            <section className={styles.summaryGrid}>
                <Metric label="Tổng doanh thu" loading={loading} value={formatCurrency(report?.totalRevenue)} />
                <Metric label="Số đơn đã thanh toán" loading={loading} value={formatCount(report?.totalOrders)} />
                <Metric label="Giá trị đơn trung bình" loading={loading} value={formatCurrency(report?.averageOrderValue)} />
                <Metric label="Sản phẩm đã bán" loading={loading} value={formatQuantity(report?.totalItemsSold)} />
                <Metric label="Nhân viên có doanh số" loading={loading} value={formatCount(report?.totalStaff)} />
            </section>

            <section className={styles.tableCard}>
                <div className={styles.tableHeader}>
                    <div>
                        <h2>Nhân viên bán hàng xuất sắc</h2>
                        <span className={styles.muted}>
                            Chỉ tính đơn đã thanh toán. Doanh thu là số gộp, chưa trừ tiền hoàn.
                        </span>
                    </div>
                    <div className={styles.tableTools}>
                        <label className={styles.inlineField}>
                            Xếp hạng theo
                            <select
                                onChange={(event) => setSortBy(event.target.value as StaffSortBy)}
                                value={sortBy}
                            >
                                <option value="revenue">Doanh thu</option>
                                <option value="orders">Số đơn hàng</option>
                            </select>
                        </label>
                        <ReportExportButton
                            disabled={!enabled || loading || rows.length === 0}
                            filenamePrefix="bao-cao-nhan-vien-ban-hang"
                            onExport={() =>
                                exportTopSalesStaffReport({ fromDate, limit, sortBy, toDate })
                            }
                        />
                    </div>
                </div>

                {error ? <div className={styles.errorBox}>{error}</div> : null}

                <div className={styles.tableWrap}>
                    <table className={styles.reportTable}>
                        <thead>
                            <tr>
                                <th className={styles.rankColumn}>Hạng</th>
                                <th>Nhân viên</th>
                                <th className={styles.numeric}>Số đơn</th>
                                <th className={styles.numeric}>Doanh thu</th>
                                <th className={styles.numeric}>Giá trị đơn TB</th>
                                <th className={styles.numeric}>SL sản phẩm bán</th>
                                <th className={styles.numeric}>Đơn có hoàn tiền</th>
                            </tr>
                        </thead>
                        <tbody>
                            {loading ? (
                                <StateRow colSpan={COLUMN_COUNT} message="Đang tải dữ liệu báo cáo..." />
                            ) : null}
                            {!loading && !error
                                ? rows.map((row) => (
                                      <tr key={row.cashierId ?? "unknown"}>
                                          <td className={styles.rankColumn}>
                                              <RankBadge rank={row.rank} />
                                          </td>
                                          <td>
                                              <strong>{row.cashierName?.trim() || "Không xác định"}</strong>
                                          </td>
                                          <td className={styles.numeric}>
                                              {sortBy === "orders" ? (
                                                  <BarValue max={maxOrders} value={row.orderCount}>
                                                      {formatCount(row.orderCount)}
                                                  </BarValue>
                                              ) : (
                                                  formatCount(row.orderCount)
                                              )}
                                          </td>
                                          <td className={styles.numeric}>
                                              {sortBy === "revenue" ? (
                                                  <BarValue max={maxRevenue} value={toNumber(row.revenue)}>
                                                      {formatCurrency(row.revenue)}
                                                  </BarValue>
                                              ) : (
                                                  formatCurrency(row.revenue)
                                              )}
                                          </td>
                                          <td className={styles.numeric}>{formatCurrency(row.averageOrderValue)}</td>
                                          <td className={styles.numeric}>{formatQuantity(row.itemsSold)}</td>
                                          <td className={styles.numeric}>{formatCount(row.refundedOrderCount)}</td>
                                      </tr>
                                  ))
                                : null}
                            {!loading && !error && rows.length === 0 ? (
                                <StateRow
                                    colSpan={COLUMN_COUNT}
                                    message={
                                        enabled
                                            ? "Không có đơn hàng đã thanh toán trong khoảng thời gian này."
                                            : "Vui lòng chọn khoảng thời gian hợp lệ."
                                    }
                                />
                            ) : null}
                        </tbody>
                    </table>
                </div>
            </section>
        </>
    );
}
