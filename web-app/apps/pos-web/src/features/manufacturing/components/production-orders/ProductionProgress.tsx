interface ProductionProgressProps {
    plannedQuantity: number;
    completedQuantity: number;
    unit: string;
}

export function ProductionProgress({
    plannedQuantity,
    completedQuantity,
    unit
}: ProductionProgressProps) {
    const percent =
        plannedQuantity === 0
            ? 0
            : Math.round((completedQuantity / plannedQuantity) * 100);

    return (
        <div className="grid gap-2">
            <div className="flex justify-between text-sm">
                <span className="text-pos-muted">Tiến độ</span>
                <strong className="text-hvt-green-900">
                    {percent}%
                </strong>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-pos-border">
                <div
                    className="h-full rounded-full bg-hvt-green-800"
                    style={{ width: `${Math.min(100, percent)}%` }}
                />
            </div>
            <div className="grid gap-1 text-sm">
                <p>
                    Số lượng kế hoạch:{" "}
                    <strong>
                        {plannedQuantity} {unit}
                    </strong>
                </p>
                <p>
                    Đã hoàn thành:{" "}
                    <strong>
                        {completedQuantity} {unit}
                    </strong>
                </p>
            </div>
        </div>
    );
}
