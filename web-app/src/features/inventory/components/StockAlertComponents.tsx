import { AlertTriangle } from "lucide-react";

import { type BadgeTone, StatusBadge } from "@/components/ui/StatusBadge";

import { stockAlertSeverityLabels } from "../constants/stock-alert.constants";
import type { StockAlertSeverity } from "../types/stock-alert.types";

export function StockAlertSeverityBadge({ severity }: { severity: StockAlertSeverity }) {
    const tone: BadgeTone = severity === "CRITICAL" ? "danger" : severity === "HIGH" ? "warning" : "info";
    return (
        <StatusBadge tone={tone}>
            <span className="inline-flex items-center gap-1.5"><AlertTriangle size={14} />{stockAlertSeverityLabels[severity]}</span>
        </StatusBadge>
    );
}
