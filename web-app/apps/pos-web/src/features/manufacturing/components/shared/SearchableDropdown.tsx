import { useState, useEffect, useLayoutEffect, useRef, useCallback } from "react";
import type { CSSProperties } from "react";
import { createPortal } from "react-dom";
import { Search, ChevronDown, Check } from "lucide-react";

export interface LookupItem {
    id: string;
    name: string;
}

interface SearchableDropdownProps {
    value: string;
    onChange: (value: string) => void;
    options: LookupItem[];
    onSearch: (searchTerm: string) => void;
    onLoadMore: () => void;
    hasMore: boolean;
    loading: boolean;
    placeholder?: string;
    emptyText?: string;
    allOptionText?: string;
    disabled?: boolean;
    /** Gọi khi mở menu — dùng để tải dữ liệu lười (chỉ gọi API khi người dùng thực sự mở dropdown). */
    onOpen?: () => void;
}

export function SearchableDropdown({
    value,
    onChange,
    options,
    onSearch,
    onLoadMore,
    hasMore,
    loading,
    placeholder = "Tìm kiếm...",
    emptyText = "Không có dữ liệu",
    allOptionText,
    disabled = false,
    onOpen
}: SearchableDropdownProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [searchTerm, setSearchTerm] = useState("");
    const [menuStyle, setMenuStyle] = useState<CSSProperties>({});
    const triggerRef = useRef<HTMLButtonElement>(null);
    const menuRef = useRef<HTMLDivElement>(null);
    const observerTarget = useRef<HTMLDivElement>(null);
    const searchInputRef = useRef<HTMLInputElement>(null);

    // Định vị menu ngay dưới ô chọn (nổi qua portal ra ngoài card/table). Nếu bên dưới không đủ chỗ
    // mà bên trên rộng hơn thì lật menu lên trên; chiều cao co theo khoảng trống thực tế.
    const updatePosition = useCallback(() => {
        if (!triggerRef.current) return;
        const rect = triggerRef.current.getBoundingClientRect();
        const gap = 4;
        const viewportMargin = 8;
        const preferredHeight = 300;
        const minHeight = 120;

        const spaceBelow = window.innerHeight - rect.bottom - gap - viewportMargin;
        const spaceAbove = rect.top - gap - viewportMargin;
        const openUpward = spaceBelow < preferredHeight && spaceAbove > spaceBelow;
        const maxHeight = Math.max(minHeight, Math.min(preferredHeight, openUpward ? spaceAbove : spaceBelow));

        const width = Math.min(Math.max(rect.width, 240), window.innerWidth - viewportMargin * 2);
        const left = Math.max(viewportMargin, Math.min(rect.left, window.innerWidth - width - viewportMargin));

        setMenuStyle({
            position: "fixed",
            left,
            width,
            maxHeight,
            zIndex: 9999,
            ...(openUpward
                ? { bottom: window.innerHeight - rect.top + gap }
                : { top: rect.bottom + gap })
        });
    }, []);

    useLayoutEffect(() => {
        if (!isOpen) return;
        updatePosition();
        window.addEventListener("resize", updatePosition);
        window.addEventListener("scroll", updatePosition, true);
        return () => {
            window.removeEventListener("resize", updatePosition);
            window.removeEventListener("scroll", updatePosition, true);
        };
    }, [isOpen, updatePosition]);

    // Xử lý đóng dropdown khi click ra ngoài (bao gồm cả trigger và menu rendered qua portal)
    useEffect(() => {
        function handleClickOutside(event: MouseEvent) {
            const target = event.target as Node;
            if (
                triggerRef.current && !triggerRef.current.contains(target) &&
                menuRef.current && !menuRef.current.contains(target)
            ) {
                setIsOpen(false);
            }
        }
        document.addEventListener("mousedown", handleClickOutside);
        return () => {
            document.removeEventListener("mousedown", handleClickOutside);
        };
    }, []);

    // Focus input khi mở dropdown
    useEffect(() => {
        if (isOpen && searchInputRef.current) {
            searchInputRef.current.focus();
        }
    }, [isOpen]);

    // Xử lý debounce search
    useEffect(() => {
        const timer = setTimeout(() => {
            onSearch(searchTerm);
        }, 300);
        return () => clearTimeout(timer);
    }, [searchTerm, onSearch]);

    // Intersection Observer cho Infinite Scroll
    const handleObserver = useCallback(
        (entries: IntersectionObserverEntry[]) => {
            const [target] = entries;
            if (target.isIntersecting && hasMore && !loading) {
                onLoadMore();
            }
        },
        [hasMore, loading, onLoadMore]
    );

    useEffect(() => {
        const element = observerTarget.current;
        if (!element) return;

        const observer = new IntersectionObserver(handleObserver, {
            root: null,
            rootMargin: "20px",
            threshold: 1.0,
        });

        observer.observe(element);
        return () => {
            observer.unobserve(element);
        };
    }, [handleObserver, isOpen]);

    // Tìm option đang được chọn để hiển thị label
    const selectedOption = value === "ALL" || value === ""
        ? { id: value, name: allOptionText || placeholder }
        : options.find((opt) => opt.id === value) || { id: value, name: "Đã chọn" };

    const menuContent = isOpen && typeof document !== "undefined" ? createPortal(
        <div
            ref={menuRef}
            style={menuStyle}
            className="flex flex-col rounded-xl border border-pos-border bg-white shadow-[0_18px_45px_rgba(24,39,28,0.16)] overflow-hidden animate-in fade-in zoom-in-95 duration-100"
            onPointerDown={(e) => e.stopPropagation()}
        >
            {/* Ô Search */}
            <div className="p-2 border-b border-pos-border bg-neutral-50/70 shrink-0">
                <div className="relative">
                    <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" size={14} />
                    <input
                        ref={searchInputRef}
                        type="text"
                        className="h-9 w-full rounded-lg border border-pos-border bg-white pl-8 pr-3 text-sm text-gray-700 outline-none focus:border-hvt-green-600 focus:ring-1 focus:ring-hvt-green-600"
                        placeholder={placeholder}
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                </div>
            </div>

            {/* Danh sách Options */}
            <div className="flex-1 min-h-0 overflow-y-auto p-1 py-1.5 max-h-[200px]">
                {/* Tùy chọn Tất cả */}
                {allOptionText && (
                    <button
                        type="button"
                        className={`flex w-full items-center justify-between rounded-lg px-3 py-2 text-sm font-medium transition-colors cursor-pointer ${
                            value === "ALL" || value === ""
                                ? "bg-[#07883a]/10 text-[#07883a]"
                                : "text-gray-700 hover:bg-neutral-100"
                        }`}
                        onClick={() => {
                            onChange("ALL");
                            setIsOpen(false);
                        }}
                    >
                        <span>{allOptionText}</span>
                        {(value === "ALL" || value === "") && <Check size={16} />}
                    </button>
                )}

                {/* Các Options từ API */}
                {options.map((opt) => (
                    <button
                        key={opt.id}
                        type="button"
                        className={`flex w-full items-center justify-between rounded-lg px-3 py-2 text-sm font-medium transition-colors cursor-pointer mt-0.5 ${
                            value === opt.id
                                ? "bg-[#07883a]/10 text-[#07883a]"
                                : "text-gray-700 hover:bg-neutral-100"
                        }`}
                        onClick={() => {
                            onChange(opt.id);
                            setIsOpen(false);
                        }}
                    >
                        <span className="truncate pr-2 text-left">{opt.name}</span>
                        {value === opt.id && <Check size={16} className="shrink-0" />}
                    </button>
                ))}

                {/* Intersection Observer Target & Loading State */}
                {loading && (
                    <div className="flex justify-center py-3">
                        <div className="h-4 w-4 animate-spin rounded-full border-2 border-[#07883a] border-t-transparent"></div>
                    </div>
                )}
                {!loading && options.length === 0 && (
                    <div className="py-4 text-center text-sm text-gray-500">
                        {emptyText}
                    </div>
                )}
                <div ref={observerTarget} className="h-1 w-full opacity-0" />
            </div>
        </div>,
        document.body
    ) : null;

    return (
        <div className="relative min-w-[180px]">
            {/* Nút Toggle */}
            <button
                ref={triggerRef}
                type="button"
                disabled={disabled}
                onClick={() => {
                    if (!isOpen) onOpen?.();
                    setIsOpen(!isOpen);
                }}
                className="flex h-10 w-full items-center justify-between rounded-xl border border-pos-border bg-white px-3 text-sm text-gray-700 outline-none hover:bg-neutral-50 focus:border-hvt-green-700 focus:ring-1 focus:ring-hvt-green-700 transition-all cursor-pointer disabled:bg-neutral-100 disabled:text-gray-400 disabled:cursor-not-allowed"
            >
                <span className="truncate pr-2 font-medium text-left">
                    {selectedOption.name}
                </span>
                <div className="flex items-center gap-1 shrink-0">
                    {value !== "" && value !== "ALL" && !allOptionText && !disabled && (
                        <div
                            className="p-0.5 hover:bg-gray-200 rounded-full text-gray-400 hover:text-gray-600 transition-colors"
                            onClick={(e) => {
                                e.stopPropagation();
                                onChange("");
                            }}
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
                        </div>
                    )}
                    <ChevronDown size={16} className={`text-gray-400 transition-transform ${isOpen ? "rotate-180" : ""}`} />
                </div>
            </button>

            {menuContent}
        </div>
    );
}
