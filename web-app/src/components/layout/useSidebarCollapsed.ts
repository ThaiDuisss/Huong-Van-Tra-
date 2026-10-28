import { useCallback, useState } from "react";

function readCollapsed(storageKey: string) {
    try {
        return window.localStorage.getItem(storageKey) === "1";
    } catch {
        return false;
    }
}

/** Trạng thái thu gọn menu trái (chỉ còn icon), nhớ theo từng máy. Không đọc/ghi được storage thì mặc định mở rộng. */
export function useSidebarCollapsed(storageKey: string) {
    const [collapsed, setCollapsed] = useState(() => readCollapsed(storageKey));

    const setAndStore = useCallback(
        (next: boolean) => {
            setCollapsed(next);

            try {
                window.localStorage.setItem(storageKey, next ? "1" : "0");
            } catch {
                // Trình duyệt chặn storage: vẫn thu gọn được trong phiên hiện tại.
            }
        },
        [storageKey]
    );

    return [collapsed, setAndStore] as const;
}
