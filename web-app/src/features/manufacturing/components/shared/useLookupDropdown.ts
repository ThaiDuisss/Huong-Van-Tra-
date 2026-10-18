import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import type { LookupItem } from "./SearchableDropdown";

export interface LookupPage {
    content?: LookupItem[];
    last?: boolean;
}

export type LookupFetcher = (search: string, page: number, size: number) => Promise<LookupPage>;

interface UseLookupDropdownOptions {
    /** false: chưa tải (vd dropdown đang ẩn). Mặc định true. */
    enabled?: boolean;
    /** true: chỉ tải khi dropdown được mở lần đầu — truyền `onOpen` của hook vào SearchableDropdown. */
    lazy?: boolean;
    pageSize?: number;
    /** Các mục đã chọn sẵn — luôn có trong options để dropdown hiện đúng tên dù chưa tải tới trang chứa nó. */
    pinned?: (LookupItem | null | undefined)[];
    /** Id đã chọn sẵn nhưng chưa biết tên — tự gọi fetchByIds để lấy tên (bỏ qua "" và "ALL"). */
    selectedIds?: string[];
    fetchByIds?: (ids: string[]) => Promise<LookupPage>;
}

/**
 * State cho SearchableDropdown dùng API lookup phân trang (id + name): tìm kiếm phía server,
 * tải thêm khi cuộn, bỏ kết quả của lần tìm cũ khi gõ nhanh.
 */
export function useLookupDropdown(fetcher: LookupFetcher, opts: UseLookupDropdownOptions = {}) {
    const { enabled = true, lazy = false, pageSize = 20, pinned, selectedIds, fetchByIds } = opts;
    const [opened, setOpened] = useState(false);
    const onOpen = useCallback(() => setOpened(true), []);
    const shouldLoad = enabled && (!lazy || opened);
    const [items, setItems] = useState<LookupItem[]>([]);
    const [search, setSearch] = useState("");
    const [page, setPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loading, setLoading] = useState(false);
    const requestRef = useRef(0);
    const fetcherRef = useRef(fetcher);
    const fetchByIdsRef = useRef(fetchByIds);
    useEffect(() => {
        fetcherRef.current = fetcher;
        fetchByIdsRef.current = fetchByIds;
    });
    const [resolved, setResolved] = useState<LookupItem[]>([]);
    const requestedIdsRef = useRef(new Set<string>());

    const load = useCallback(
        async (term: string, pageNum: number) => {
            const requestId = ++requestRef.current;
            setLoading(true);
            try {
                const res = await fetcherRef.current(term, pageNum, pageSize);
                if (requestId !== requestRef.current) return;
                const content = res.content ?? [];
                setItems((prev) => (pageNum === 0 ? content : [...prev, ...content]));
                setHasMore(!res.last);
            } catch (err) {
                console.error("[useLookupDropdown] Lỗi tải dữ liệu dropdown:", err);
                if (requestId === requestRef.current) setHasMore(false);
            } finally {
                if (requestId === requestRef.current) setLoading(false);
            }
        },
        [pageSize]
    );

    useEffect(() => {
        if (!shouldLoad) return undefined;
        // Chạy sau microtask để lần mount bị StrictMode hủy không gửi request thừa
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setPage(0);
            void load(search, 0);
        });
        return () => {
            active = false;
        };
    }, [shouldLoad, search, load]);

    const onLoadMore = useCallback(() => {
        if (loading || !hasMore) return;
        const nextPage = page + 1;
        setPage(nextPage);
        void load(search, nextPage);
    }, [loading, hasMore, page, search, load]);

    // Lấy tên cho các id đã chọn sẵn (1 request cho cả nhóm id còn thiếu)
    const selectedKey = (selectedIds ?? []).join("|");
    useEffect(() => {
        const fetchNames = fetchByIdsRef.current;
        if (!fetchNames) return;
        const missing = (selectedIds ?? []).filter(
            (id) => id && id !== "ALL" && !requestedIdsRef.current.has(id)
        );
        if (missing.length === 0) return;
        missing.forEach((id) => requestedIdsRef.current.add(id));
        fetchNames(missing)
            .then((res) => {
                const content = res.content ?? [];
                if (content.length > 0) setResolved((prev) => [...prev, ...content]);
            })
            .catch((err: unknown) => {
                console.error("[useLookupDropdown] Lỗi lấy tên theo id:", err);
                missing.forEach((id) => requestedIdsRef.current.delete(id));
            });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedKey]);

    const pinnedKey = (pinned ?? []).map((p) => (p ? `${p.id}:${p.name}` : "")).join("|");
    const options = useMemo(() => {
        const seen = new Set(items.map((i) => i.id));
        const extra: LookupItem[] = [];
        for (const p of [...(pinned ?? []), ...resolved]) {
            if (p && p.id && !seen.has(p.id)) {
                seen.add(p.id);
                extra.push(p);
            }
        }
        return extra.length > 0 ? [...extra, ...items] : items;
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [items, resolved, pinnedKey]);

    const findOption = useCallback((id: string) => options.find((o) => o.id === id) ?? null, [options]);

    return { options, loading, hasMore, onSearch: setSearch, onLoadMore, onOpen, findOption };
}

/** Thêm các mục đã biết (vd giá trị đang sửa) vào đầu danh sách nếu chưa có, để dropdown hiện đúng tên. */
export function withPinned(options: LookupItem[], ...pinned: (LookupItem | null | undefined)[]): LookupItem[] {
    const extra = pinned.filter(
        (p): p is LookupItem => Boolean(p && p.id) && !options.some((o) => o.id === p!.id)
    );
    return extra.length > 0 ? [...extra, ...options] : options;
}
