import { Box, TriangleAlert } from "lucide-react";

import { MiniSparkline } from "./MiniSparkline";

interface MetricCardProps {
    label: string;
    value: string;
    change: string;
    tone: "green" | "orange";
    trend: number[];
}

export function MetricCard({
    label,
    value,
    change,
    tone,
    trend
}: MetricCardProps) {
    const Icon = tone === "orange" ? TriangleAlert : Box;

    return (
        <article className="grid grid-cols-[52px_minmax(0,1fr)_auto] items-center gap-4 rounded-2xl bg-white p-5 shadow-pos-card">
            <span
                className={`grid size-12 place-items-center rounded-full ${
                    tone === "orange"
                        ? "bg-orange-50 text-orange-500"
                        : "bg-hvt-green-50 text-hvt-green-900"
                }`}
                aria-hidden="true"
            >
                <Icon size={24} />
            </span>
            <div>
                <p className="text-sm font-semibold text-pos-ink">
                    {label}
                </p>
                <strong className="mt-1 block text-3xl text-pos-ink">
                    {value}
                </strong>
                <p
                    className={`text-xs font-bold ${
                        tone === "orange"
                            ? "text-orange-600"
                            : "text-hvt-green-800"
                    }`}
                >
                    {change}{" "}
                    <span className="font-medium text-pos-muted">
                        so với tháng trước
                    </span>
                </p>
            </div>
            <MiniSparkline values={trend} tone={tone} />
        </article>
    );
}
