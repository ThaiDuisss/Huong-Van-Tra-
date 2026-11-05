import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { isTimePreset, resolveTimeRange, type TimeRangeValue } from "./timeRange.utils";

const SEARCH_DEBOUNCE_MS = 350;

/**
 * Trạng thái danh sách đồng bộ lên URL (giống màn Khách hàng): F5 / chia sẻ link giữ nguyên bộ lọc.
 * Tham số: `q` (từ khóa), `status`, `page` (tính từ 1 trên URL), `time`, `from`, `to`.
 */
export function useListUrlState({ defaultStatus = "ALL" }: { defaultStatus?: string } = {}) {
    const [searchParams, setSearchParams] = useSearchParams();

    const keyword = searchParams.get("q") ?? "";
    const status = searchParams.get("status") ?? defaultStatus;
    const page = Math.max(0, Number(searchParams.get("page") ?? "1") - 1 || 0);
    const timePreset = searchParams.get("time");
    const fromParam = searchParams.get("from") ?? undefined;
    const toParam = searchParams.get("to") ?? undefined;
    const time = useMemo<TimeRangeValue>(() => ({
        preset: isTimePreset(timePreset) ? timePreset : "all",
        from: fromParam,
        to: toParam
    }), [fromParam, timePreset, toParam]);
    const timeRange = useMemo(() => resolveTimeRange(time), [time]);

    const update = useCallback((patch: Record<string, string | null | undefined>, resetPage = true) => {
        setSearchParams((current) => {
            const next = new URLSearchParams(current);
            for (const [key, value] of Object.entries(patch)) {
                if (value === null || value === undefined || value === "") next.delete(key);
                else next.set(key, value);
            }
            if (resetPage) next.delete("page");
            return next;
        }, { replace: true });
    }, [setSearchParams]);

    // Ô tìm kiếm giữ bản nháp cục bộ, chỉ đẩy lên URL sau khi ngừng gõ.
    const [searchDraft, setSearchDraft] = useState(keyword);
    const [syncedKeyword, setSyncedKeyword] = useState(keyword);
    if (keyword !== syncedKeyword) {
        // URL đổi từ bên ngoài (back/forward, link) thì cập nhật ô tìm kiếm;
        // nếu do chính ô tìm kiếm đẩy lên thì giữ nguyên bản nháp (vd. dấu cách cuối).
        setSyncedKeyword(keyword);
        if (searchDraft.trim() !== keyword) setSearchDraft(keyword);
    }
    useEffect(() => {
        const trimmed = searchDraft.trim();
        if (trimmed === keyword) return;
        const timerId = window.setTimeout(() => update({ q: trimmed }), SEARCH_DEBOUNCE_MS);
        return () => window.clearTimeout(timerId);
    }, [keyword, searchDraft, update]);

    const setStatus = useCallback(
        (value: string) => update({ status: value === defaultStatus ? null : value }),
        [defaultStatus, update]
    );
    const setPage = useCallback(
        (value: number) => update({ page: value > 0 ? String(value + 1) : null }, false),
        [update]
    );
    const setTime = useCallback((value: TimeRangeValue) => update({
        time: value.preset === "all" ? null : value.preset,
        from: value.preset === "custom" ? value.from : null,
        to: value.preset === "custom" ? value.to : null
    }), [update]);

    return {
        keyword,
        searchDraft,
        setSearchDraft,
        status,
        setStatus,
        page,
        setPage,
        time,
        timeRange,
        setTime,
        /** Có đang áp dụng điều kiện lọc nào không (dùng cho empty state). */
        hasFilter: Boolean(keyword) || status !== defaultStatus || timeRange !== null
    };
}
