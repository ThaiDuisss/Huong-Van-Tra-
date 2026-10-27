import { AlertTriangle } from "lucide-react";

import { stockAlertSeverityLabels } from "../constants/stock-alert.constants";
import type { StockAlertSeverity } from "../types/stock-alert.types";

export function StockAlertSeverityBadge({ severity }: { severity: StockAlertSeverity }) {
    const tone = severity === "CRITICAL" ? "border-red-200 bg-red-50 text-red-700" : severity === "HIGH" ? "border-amber-200 bg-amber-50 text-amber-700" : "border-blue-200 bg-blue-50 text-blue-700";
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${tone}`}><AlertTriangle size={14} />{stockAlertSeverityLabels[severity]}</span>;
}
