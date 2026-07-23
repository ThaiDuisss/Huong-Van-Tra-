import { useState, useCallback, useEffect } from "react";

export function useLookupDropdown<T>(fetcher: (search: string, page: number) => Promise<{ content: T[], last: boolean }>) {
    const [options, setOptions] = useState<T[]>([]);
    const [page, setPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loading, setLoading] = useState(false);
    const [searchTerm, setSearchTerm] = useState("");

    const loadOptions = useCallback(async (search: string, pageNum: number) => {
        try {
            setLoading(true);
            const res = await fetcher(search, pageNum);
            setOptions(prev => pageNum === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]);
            setHasMore(!res.last);
        } catch (err) {
            console.error("Error loading options", err);
            setHasMore(false);
        } finally {
            setLoading(false);
        }
    }, [fetcher]);

    useEffect(() => {
        setPage(0);
        loadOptions(searchTerm, 0);
    }, [searchTerm, loadOptions]);

    const handleLoadMore = useCallback(() => {
        if (!loading && hasMore) {
            const nextPage = page + 1;
            setPage(nextPage);
            loadOptions(searchTerm, nextPage);
        }
    }, [loading, hasMore, page, searchTerm, loadOptions]);

    return { options, hasMore, loading, searchTerm, setSearchTerm, handleLoadMore };
}
