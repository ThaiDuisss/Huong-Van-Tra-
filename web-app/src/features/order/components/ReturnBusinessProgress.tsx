import { Check, Circle } from "lucide-react";
import type { ReactNode } from "react";

import type {
    CustomerReturnQcDispositionSummary,
    CustomerReturnQcSummaryResponse,
    OrderReturnListRow,
    OrderReturnResponse,
    OrderReturnTimelineResponse
} from "../types/order-return.types";
import { formatReturnQuantity, toReturnNumber } from "../utils/order-return.utils";
import { formatDateTime } from "../utils/order-formatters";
import styles from "./ReturnBusinessProgress.module.css";

const dispositionLabels: Record<string, string> = {
    RESTOCK: "Đã nhập lại kho bán",
    DAMAGED: "Đã chuyển vào khu hàng hỏng",
    QUARANTINE: "Đã chuyển vào khu cách ly (lịch sử)",
    WRITE_OFF: "Đã ghi nhận tiêu hủy / loại bỏ"
};

interface ReturnBusinessProgressProps {
    qcSummary: CustomerReturnQcSummaryResponse | null;
    returnData: OrderReturnResponse | OrderReturnListRow;
    timeline?: OrderReturnTimelineResponse[];
}

export function ReturnBusinessProgress({
    qcSummary,
    returnData,
    timeline = []
}: ReturnBusinessProgressProps) {
    const qcLines = qcSummary?.lines ?? [];
    const receivedDone = returnData.status === "RECEIVED" || returnData.status === "COMPLETED";
    const isPhysicalDone = Boolean(returnData.physicalDone || returnData.status === "COMPLETED");
    const invStatus = returnData.inventoryProcessingStatus ?? (isPhysicalDone ? "COMPLETED" : "PENDING");

    const inspectedDone = isPhysicalDone || (
        qcSummary?.inspectionStatus === "POSTED" &&
        qcLines.length > 0 &&
        qcLines.every((line) => toReturnNumber(line.remainingQcQuantity) === 0)
    );

    const inventoryTitle = (() => {
        if (isPhysicalDone) {
            return "Đã xử lý hàng trong kho";
        }
        if (invStatus === "FAILED") {
            return "Xử lý kho thất bại";
        }
        if (invStatus === "PROCESSING") {
            return "Đang xử lý hàng trong kho";
        }
        return "Chờ xử lý hàng trong kho";
    })();

    const progressDescription = returnData.requiredQuantity != null && toReturnNumber(returnData.requiredQuantity) > 0
        ? `Đã xử lý: ${formatReturnQuantity(returnData.processedQuantity ?? 0)} / ${formatReturnQuantity(returnData.requiredQuantity)}${
            returnData.remainingQuantity != null && toReturnNumber(returnData.remainingQuantity) > 0
                ? ` · Còn lại: ${formatReturnQuantity(returnData.remainingQuantity)}`
                : ""
          }`
        : undefined;

    const createdAt = findOccurredAt(timeline, "CREATED") ?? returnData.createdAt;
    const receivedAt = findOccurredAt(timeline, "RECEIVED");

    return (
        <section className={styles.container} aria-label="Tiến độ xử lý phiếu hoàn">
            <h3>Tiến độ nghiệp vụ</h3>
            <ProgressStep
                completed
                description={formatOptionalDate(createdAt)}
                title="Đã tạo phiếu hoàn"
            />
            <ProgressStep
                completed={receivedDone}
                description={receivedDone ? formatOptionalDate(receivedAt) : undefined}
                title={receivedDone ? "Đã nhận hàng" : "Chờ nhận hàng"}
            />
            <ProgressStep
                completed={inspectedDone}
                description={inspectedDone ? formatOptionalDate(qcSummary?.inspectedAt) : undefined}
                title={inspectedDone ? "Đã kiểm hàng" : "Chờ kiểm hàng"}
            />
            <ProgressStep
                completed={isPhysicalDone}
                description={progressDescription}
                title={inventoryTitle}
            >
                {inspectedDone ? (
                    <div className={styles.dispositionList}>
                        {qcLines.flatMap((line) =>
                            (line.dispositions ?? []).map((disposition, index) => (
                                <DispositionEvidence
                                    disposition={disposition}
                                    itemName={resolveItemName(returnData, line.returnItemId)}
                                    key={`${line.returnItemId}-${disposition.transactionId ?? index}`}
                                />
                            ))
                        )}
                    </div>
                ) : null}
            </ProgressStep>
        </section>
    );
}

function ProgressStep({
    children,
    completed,
    description,
    title
}: {
    children?: ReactNode;
    completed: boolean;
    description?: string;
    title: string;
}) {
    const Icon = completed ? Check : Circle;
    return (
        <div className={`${styles.step} ${completed ? styles.completed : ""}`}>
            <span className={styles.marker}>
                <Icon aria-hidden="true" size={15} />
            </span>
            <div>
                <strong>{title}</strong>
                {description ? <span>{description}</span> : null}
                {children}
            </div>
        </div>
    );
}

function DispositionEvidence({
    disposition,
    itemName
}: {
    disposition: CustomerReturnQcDispositionSummary;
    itemName: string;
}) {
    const transactionType =
        disposition.transactionType ??
        (disposition.result === "WRITE_OFF" ? "WRITE_OFF" : "CUSTOMER_RETURN_RECEIPT");
    return (
        <div className={styles.evidence}>
            <strong>
                {itemName} × {formatReturnQuantity(disposition.quantity)}
            </strong>
            <span>{dispositionLabels[disposition.result] ?? disposition.result}</span>
            {disposition.destinationLocationName ? (
                <span>Điểm đến: {disposition.destinationLocationName}</span>
            ) : disposition.result === "WRITE_OFF" ? (
                <span>Điểm đến: Không nhập tồn kho</span>
            ) : null}
            <span>Giao dịch: {TRANSACTION_TYPE_LABELS[transactionType] ?? transactionType}</span>
            <span>
                Trạng thái: {disposition.processingStatus === "PROCESSED" ? "Đã xử lý" : "Chờ đồng bộ"}
            </span>
            {disposition.processedAt ? (
                <span>Thời gian xử lý: {formatDateTime(disposition.processedAt)}</span>
            ) : null}
        </div>
    );
}

const TRANSACTION_TYPE_LABELS: Record<string, string> = {
    CUSTOMER_RETURN_RECEIPT: "Nhận hàng trả vào kho",
    WRITE_OFF: "Tiêu hủy",
};

function resolveItemName(
    returnData: OrderReturnResponse | OrderReturnListRow,
    returnItemId: string
) {
    const item = (returnData.items ?? []).find((candidate) => candidate.id === returnItemId);
    if (item?.productNameSnapshot && item.skuSnapshot) {
        return `${item.productNameSnapshot} (${item.skuSnapshot})`;
    }
    return item?.productNameSnapshot || item?.skuSnapshot || "Sản phẩm";
}

function findOccurredAt(timeline: OrderReturnTimelineResponse[], action: string) {
    return timeline.find((entry) => entry.action === action)?.occurredAt;
}

function formatOptionalDate(value?: string | null) {
    return value ? formatDateTime(value) : undefined;
}
