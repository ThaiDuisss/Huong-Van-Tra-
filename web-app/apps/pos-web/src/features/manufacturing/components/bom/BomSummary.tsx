import { MetricCard } from "../shared/MetricCard";
import type { BomSummaryMetric } from "../../types/bom.types";

interface BomSummaryProps {
    metrics: BomSummaryMetric[];
}

export function BomSummary({ metrics }: BomSummaryProps) {
    return (
        <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 2xl:grid-cols-4">
            {metrics.map((metric) => (
                <MetricCard
                    key={metric.id}
                    label={metric.label}
                    value={metric.value}
                    change={metric.change}
                    tone={metric.tone}
                    trend={metric.trend}
                />
            ))}
        </section>
    );
}
