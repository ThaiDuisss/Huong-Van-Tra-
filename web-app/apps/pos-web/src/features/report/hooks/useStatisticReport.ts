import { useEffect, useState } from "react";

import type { StatisticReportQueryParams } from "../types/statistic-report.types";
import { getErrorMessage } from "../utils/statistic-report.utils";

interface StatisticReportState<TReport> {
    error: string | null;
    loading: boolean;
    report: TReport | null;
}

/**
 * Tải báo cáo mỗi khi bộ lọc đổi. `fetcher` phải là hàm ổn định (khai báo ở module),
 * `enabled = false` (bộ lọc không hợp lệ) thì không gọi API.
 */
export function useStatisticReport<TSort extends string, TReport>(
    fetcher: (params: StatisticReportQueryParams<TSort>) => Promise<TReport>,
    { fromDate, limit, sortBy, toDate }: StatisticReportQueryParams<TSort>,
    enabled: boolean
): StatisticReportState<TReport> {
    const [state, setState] = useState<StatisticReportState<TReport>>({
        error: null,
        loading: enabled,
        report: null
    });

    useEffect(() => {
        if (!enabled) {
            return;
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setState((current) => ({ ...current, error: null, loading: true }));

            fetcher({ fromDate, limit, sortBy, toDate })
                .then((report) => {
                    if (!ignore) {
                        setState({ error: null, loading: false, report });
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setState({
                            error: getErrorMessage(
                                error,
                                "Không thể tải dữ liệu báo cáo. Vui lòng thử lại."
                            ),
                            loading: false,
                            report: null
                        });
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [enabled, fetcher, fromDate, limit, sortBy, toDate]);

    return enabled ? state : { error: null, loading: false, report: null };
}
