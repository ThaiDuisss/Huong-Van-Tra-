import type { ProductionLogEntry } from "../../types/production-order.types";

interface ProductionLogProps {
    logs: ProductionLogEntry[];
}

export function ProductionLog({ logs }: ProductionLogProps) {
    if (logs.length === 0) {
        return (
            <p className="text-sm text-pos-muted">
                Chưa có nhật ký sản xuất.
            </p>
        );
    }

    return (
        <div className="grid gap-3">
            {logs.map((log) => (
                <article
                    key={log.id}
                    className="grid grid-cols-[1fr_auto] gap-2 border-l-2 border-hvt-green-800 pl-3 text-sm"
                >
                    <div>
                        <p className="font-semibold text-pos-ink">
                            {formatDateTime(log.time)}
                        </p>
                        <p className="text-pos-muted">{log.message}</p>
                    </div>
                    <span className="text-pos-muted">{log.actor}</span>
                </article>
            ))}
        </div>
    );
}

function formatDateTime(value: string) {
    return value.replace(" ", " - ");
}
