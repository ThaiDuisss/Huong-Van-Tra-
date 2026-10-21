export interface TimeoutController {
    signal: AbortSignal;
    clear(): void;
}

export function createTimeoutController(
    timeoutMs: number,
    externalSignal?: AbortSignal
): TimeoutController {
    const controller = new AbortController();

    const abortFromExternalSignal = () => {
        controller.abort(externalSignal?.reason);
    };

    if (externalSignal?.aborted) {
        abortFromExternalSignal();
    } else {
        externalSignal?.addEventListener(
            "abort",
            abortFromExternalSignal,
            { once: true }
        );
    }

    const timeoutId = setTimeout(() => {
        controller.abort(
            new DOMException(
                `Request exceeded ${timeoutMs}ms`,
                "TimeoutError"
            )
        );
    }, timeoutMs);

    return {
        signal: controller.signal,

        clear() {
            clearTimeout(timeoutId);

            externalSignal?.removeEventListener(
                "abort",
                abortFromExternalSignal
            );
        }
    };
}