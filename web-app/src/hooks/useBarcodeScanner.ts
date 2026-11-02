import { useEffect, useRef } from "react";

export interface UseBarcodeScannerOptions {
    /**
     * Callback được gọi khi máy quét mã vạch quét thành công.
     */
    onScan: (barcode: string) => void;

    /**
     * Khoảng thời gian tối đa (ms) giữa hai phím liên tiếp để xem là từ máy quét.
     * Máy quét vật lý (HID Keyboard) thường gõ cực nhanh (< 20-30ms / ký tự).
     * Người dùng gõ tay thường > 80-150ms / ký tự.
     * Mặc định: 50ms.
     */
    maxInterval?: number;

    /**
     * Độ dài tối thiểu của chuỗi mã vạch để phân biệt với phím Enter thông thường.
     * Mặc định: 3 ký tự (EAN-13 thường là 13 ký tự).
     */
    minLength?: number;

    /**
     * Trạng thái bật/tắt lắng nghe sự kiện toàn cục.
     * Mặc định: true.
     */
    enabled?: boolean;

    /**
     * Ref đến thẻ input tìm kiếm (nếu có).
     * Khi quét thành công, hook sẽ tự động focus và bôi đen input này.
     */
    targetInputRef?: React.RefObject<HTMLInputElement | null>;

    /**
     * Có ngăn chặn hành vi Enter mặc định khi quét mã vạch không (preventDefault).
     * Mặc định: true.
     */
    preventDefault?: boolean;

    /**
     * Nếu con trỏ đang nằm ở một input/textarea khác không phải ô tìm kiếm,
     * tự động xoá chuỗi mã vạch vừa bị gõ nhầm vào ô đó.
     * Mặc định: true.
     */
    cleanOtherInputs?: boolean;
}

/**
 * Hook lắng nghe sự kiện máy quét mã vạch toàn cục (Global Keydown Listener).
 * Cho phép người dùng đứng ở bất kỳ vị trí nào trên trang web, click vào bất kỳ đâu,
 * khi bấm máy quét mã vạch, hệ thống sẽ tự động bắt chuỗi và đưa vào ô tìm kiếm.
 */
export function useBarcodeScanner({
    onScan,
    maxInterval = 50,
    minLength = 3,
    enabled = true,
    targetInputRef,
    preventDefault = true,
    cleanOtherInputs = true
}: UseBarcodeScannerOptions) {
    const onScanRef = useRef(onScan);
    useEffect(() => {
        onScanRef.current = onScan;
    }, [onScan]);

    const bufferRef = useRef<string>("");
    const lastKeyTimeRef = useRef<number>(0);
    const firstKeyTimeRef = useRef<number>(0);
    const keyCountRef = useRef<number>(0);

    useEffect(() => {
        if (!enabled) return;

        function handleKeyDown(event: KeyboardEvent) {
            // Bỏ qua các tổ hợp phím chức năng (Ctrl, Alt, Meta/Command)
            if (event.ctrlKey || event.altKey || event.metaKey) {
                return;
            }

            const now = Date.now();

            // Khi gặp phím kết thúc Enter (hậu tố mặc định của máy quét mã vạch)
            if (event.key === "Enter") {
                const buffer = bufferRef.current.trim();
                const keyCount = keyCountRef.current;
                const timeSinceLastKey = now - lastKeyTimeRef.current;
                const totalDuration = now - firstKeyTimeRef.current;
                const avgInterval = keyCount > 1 ? totalDuration / keyCount : 999;

                // Điều kiện máy quét:
                // 1. Độ dài chuỗi đủ lớn (>= minLength)
                // 2. Ký tự cuối cùng đến phím Enter không quá xa (< maxInterval * 2.5 hoặc 120ms)
                // 3. Tốc độ gõ trung bình cực nhanh (avgInterval <= maxInterval)
                const isBarcodeScan =
                    buffer.length >= minLength &&
                    keyCount >= minLength &&
                    timeSinceLastKey <= Math.max(maxInterval * 2.5, 120) &&
                    avgInterval <= maxInterval;

                if (isBarcodeScan) {
                    if (preventDefault) {
                        event.preventDefault();
                        event.stopPropagation();
                    }

                    // Nếu đang focus ở một input khác ngoài targetInputRef, xoá chuỗi vừa gõ nhầm vào đó
                    if (cleanOtherInputs) {
                        const activeEl = document.activeElement;
                        if (
                            activeEl &&
                            activeEl !== targetInputRef?.current &&
                            (activeEl instanceof HTMLInputElement || activeEl instanceof HTMLTextAreaElement)
                        ) {
                            if (activeEl.value.endsWith(buffer)) {
                                activeEl.value = activeEl.value.slice(0, -buffer.length);
                                // Phát event change để cập nhật state của input bị dính phím
                                activeEl.dispatchEvent(new Event("input", { bubbles: true }));
                            }
                        }
                    }

                    // Gọi callback xử lý barcode
                    onScanRef.current(buffer);

                    // Tự động focus và bôi đen ô tìm kiếm nếu có ref
                    if (targetInputRef?.current) {
                        targetInputRef.current.focus();
                        targetInputRef.current.select();
                    }
                }

                // Reset buffer sau khi Enter
                bufferRef.current = "";
                keyCountRef.current = 0;
                lastKeyTimeRef.current = 0;
                firstKeyTimeRef.current = 0;
                return;
            }

            // Chỉ xử lý các ký tự in được (độ dài 1 ký tự)
            if (event.key.length === 1) {
                const timeDiff = now - lastKeyTimeRef.current;

                // Nếu khoảng cách giữa 2 phím quá lớn (> maxInterval), coi như bắt đầu một chuỗi mới (hoặc người gõ tay)
                if (timeDiff > maxInterval) {
                    bufferRef.current = event.key;
                    keyCountRef.current = 1;
                    firstKeyTimeRef.current = now;
                    lastKeyTimeRef.current = now;
                } else {
                    // Phím gõ siêu nhanh: gom vào buffer máy quét
                    bufferRef.current += event.key;
                    keyCountRef.current += 1;
                    lastKeyTimeRef.current = now;
                }
            }
        }

        // Lắng nghe sự kiện keydown toàn trang
        window.addEventListener("keydown", handleKeyDown, true);

        return () => {
            window.removeEventListener("keydown", handleKeyDown, true);
        };
    }, [enabled, maxInterval, minLength, preventDefault, cleanOtherInputs, targetInputRef]);
}
