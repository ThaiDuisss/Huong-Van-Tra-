import { CalendarClock } from "lucide-react";

import { formatCurrency, parseCurrencyInput } from "../../services/money";
import {
    formatAvailabilityDate,
    formatQuantity,
    type AvailabilityEstimate
} from "../../services/posBackorder.api";

interface BackorderCheckoutNoticeProps {
    estimate: AvailabilityEstimate;
    total: number;
    /** Đặt cọc chỉ hỗ trợ tiền mặt. */
    depositAllowed: boolean;
    depositEnabled: boolean;
    depositAmount: number;
    onDepositEnabledChange(enabled: boolean): void;
    onDepositAmountChange(amount: number): void;
}

function roundToThousand(value: number) {
    return Math.max(1000, Math.round(value / 1000) * 1000);
}

export function BackorderCheckoutNotice({
    estimate,
    total,
    depositAllowed,
    depositEnabled,
    depositAmount,
    onDepositEnabledChange,
    onDepositAmountChange
}: BackorderCheckoutNoticeProps) {
    const waitingLines = estimate.lines.filter((line) => line.scenario !== "AVAILABLE");
    const depositInvalid =
        depositEnabled && (depositAmount <= 0 || depositAmount >= total);

    return (
        <div className="grid gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-3">
            <div className="flex items-start gap-2">
                <CalendarClock aria-hidden="true" className="mt-0.5 shrink-0 text-amber-700" size={18} />
                <div className="grid gap-0.5">
                    <strong className="text-sm font-black text-amber-900">
                        Đơn chờ hàng · Dự kiến có hàng {formatAvailabilityDate(estimate.estimatedAvailableAt)}
                    </strong>
                    <span className="text-xs font-semibold text-amber-800">
                        {waitingLines
                            .map(
                                (line) =>
                                    `${line.itemName ?? "Mặt hàng"} thiếu ${formatQuantity(line.missingQuantity)}`
                            )
                            .join(", ")}
                        . Thu tiền trước, giao và trừ tồn khi có hàng.
                    </span>
                </div>
            </div>

            <div className="grid grid-cols-2 gap-2">
                <button
                    aria-pressed={!depositEnabled}
                    className={`h-9 rounded-lg border text-sm font-black ${
                        !depositEnabled
                            ? "border-[#159734] bg-white text-[#159734]"
                            : "border-amber-200 bg-transparent text-amber-900"
                    }`}
                    onClick={() => onDepositEnabledChange(false)}
                    type="button"
                >
                    Thu đủ
                </button>
                <button
                    aria-pressed={depositEnabled}
                    className={`h-9 rounded-lg border text-sm font-black disabled:opacity-50 ${
                        depositEnabled
                            ? "border-[#159734] bg-white text-[#159734]"
                            : "border-amber-200 bg-transparent text-amber-900"
                    }`}
                    disabled={!depositAllowed}
                    onClick={() => {
                        onDepositEnabledChange(true);

                        if (depositAmount <= 0 || depositAmount >= total) {
                            onDepositAmountChange(roundToThousand(total * 0.3));
                        }
                    }}
                    type="button"
                >
                    Đặt cọc
                </button>
            </div>

            {!depositAllowed ? (
                <span className="text-xs font-semibold text-amber-800">
                    Đặt cọc hiện chỉ hỗ trợ tiền mặt. Chuyển khoản thì thu đủ.
                </span>
            ) : null}

            {depositEnabled ? (
                <div className="grid gap-2">
                    <label className="grid gap-1 text-xs font-bold text-amber-900">
                        Số tiền cọc
                        <input
                            className="h-10 rounded-lg border border-amber-200 bg-white px-3 text-right text-base font-black tabular-nums text-[#172317]"
                            inputMode="numeric"
                            onChange={(event) =>
                                onDepositAmountChange(parseCurrencyInput(event.target.value))
                            }
                            value={depositAmount > 0 ? depositAmount.toLocaleString("vi-VN") : ""}
                        />
                    </label>
                    <div className="grid grid-cols-3 gap-2">
                        {[0.3, 0.5, 0.7].map((ratio) => (
                            <button
                                className="h-8 rounded-lg border border-amber-200 bg-white text-xs font-black text-amber-900"
                                key={ratio}
                                onClick={() => onDepositAmountChange(roundToThousand(total * ratio))}
                                type="button"
                            >
                                {Math.round(ratio * 100)}%
                            </button>
                        ))}
                    </div>
                    {depositInvalid ? (
                        <span className="text-xs font-semibold text-red-700">
                            Tiền cọc phải lớn hơn 0 và nhỏ hơn tổng tiền {formatCurrency(total)}.
                        </span>
                    ) : (
                        <span className="text-xs font-semibold text-amber-800">
                            Còn lại thu khi giao hàng: {formatCurrency(total - depositAmount)}
                        </span>
                    )}
                </div>
            ) : null}
        </div>
    );
}
