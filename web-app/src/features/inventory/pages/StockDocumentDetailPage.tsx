import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { hasAnyRole } from "../../../auth/role-utils";
import {
    StockDocumentActionDialog,
    StockDocumentAuditTimeline,
    StockDocumentBackLink,
    StockDocumentBatchTable,
    StockDocumentLinesTable,
    StockDocumentReadonlyAlert,
    StockDocumentStatusBadge,
    StockDocumentTypeBadge
} from "../components/StockDocumentComponents";
import {
    stockDocumentActionLabels,
    stockDocumentRoleLabels,
    stockDocumentTypeLabels
} from "../constants/stock-document.constants";
import { stockDocumentApi } from "../services/stockDocument.api";
import type {
    StockDocumentAction,
    StockDocumentDetail,
    StockDocumentStatus
} from "../types/stock-document.types";
import { formatVietnamDateTime } from "../utils/inventory-format";

type DetailTab = "general" | "lines" | "batches" | "audit";

function allowedRolesForAction(
    action: StockDocumentAction,
    document: StockDocumentDetail
): string[] {
    if (action === "SUBMIT") {
        return ["ADMIN", "INVENTORY_STAFF", "MANAGER", "MANAGER"];
    }

    if (action === "APPROVE") {
        return document.totalQuantity > 80
            ? ["ADMIN", "MANAGER", "MANAGER"]
            : ["ADMIN", "MANAGER", "INVENTORY_STAFF", "MANAGER"];
    }

    if (action === "REJECT") {
        return ["ADMIN", "MANAGER", "INVENTORY_STAFF", "MANAGER"];
    }

    if (action === "POST") {
        return document.sourceLocation?.isCentral ||
            document.destinationLocation?.isCentral
            ? ["ADMIN", "INVENTORY_STAFF", "MANAGER", "ACCOUNTANT"]
            : ["ADMIN", "MANAGER", "ACCOUNTANT", "MANAGER"];
    }

    if (action === "CANCEL") {
        return ["ADMIN", "INVENTORY_STAFF", "MANAGER", "MANAGER"];
    }

    return ["ADMIN", "ACCOUNTANT", "MANAGER"];
}

function actionsForStatus(status: StockDocumentStatus): StockDocumentAction[] {
    if (status === "DRAFT") {
        return ["SUBMIT", "CANCEL"];
    }

    if (status === "SUBMITTED") {
        return ["APPROVE", "REJECT"];
    }

    if (status === "APPROVED") {
        return ["POST", "REJECT", "CANCEL"];
    }

    if (status === "POSTED") {
        return ["REVERSE"];
    }

    return [];
}

function originalBusinessPath(document: StockDocumentDetail) {
    if (document.referenceType === "PRODUCTION_ORDER") {
        return "/management/inventory/production-orders";
    }

    if (document.referenceType === "STOCK_TRANSFER") {
        return "/management/inventory/transfers";
    }

    if (document.type === "SUPPLIER_RETURN") {
        return "/management/inventory/supplier-returns";
    }

    if (document.type === "PURCHASE_RECEIPT") {
        return "/management/inventory/receipts";
    }

    return "/management/inventory/documents";
}

export function StockDocumentDetailPage() {
    const { documentId } = useParams();
    const { roles } = useAuth();
    const canViewCost = hasAnyRole(roles, ["ADMIN", "ACCOUNTANT", "DIRECTOR"]);
    const [document, setDocument] = useState<StockDocumentDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [tab, setTab] = useState<DetailTab>("general");
    const [toast, setToast] = useState<{
        message: string;
        success: boolean;
    } | null>(null);
    const [activeAction, setActiveAction] =
        useState<StockDocumentAction | null>(null);
    const [note, setNote] = useState("");
    const [reason, setReason] = useState("");
    const [mutationLoading, setMutationLoading] = useState(false);

    const loadDocument = useCallback(() => {
        if (!documentId) {
            return;
        }

        let active = true;

        queueMicrotask(() => {
            if (active) {
                setLoading(true);
                setError("");
            }
        });

        void stockDocumentApi
            .getStockDocument(documentId)
            .then((response) => {
                if (active) {
                    setDocument(response);
                }
            })
            .catch((loadError: unknown) => {
                if (active) {
                    setError(
                        loadError instanceof Error
                            ? loadError.message
                            : "Không thể tải chi tiết chứng từ."
                    );
                }
            })
            .finally(() => {
                if (active) {
                    setLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, [documentId]);

    useEffect(() => loadDocument(), [loadDocument]);

    const availableActions = useMemo(
        () => (document ? actionsForStatus(document.status) : []),
        [document]
    );

    function showToast(message: string, success: boolean) {
        setToast({ message, success });
        window.setTimeout(() => setToast(null), 2600);
    }

    async function confirmAction() {
        if (!activeAction || !document) {
            return;
        }

        const input = {
            documentId: document.id,
            note,
            reason
        };
        const mutations = {
            APPROVE: stockDocumentApi.approveStockDocument,
            CANCEL: stockDocumentApi.cancelStockDocument,
            POST: stockDocumentApi.postStockDocument,
            REJECT: stockDocumentApi.rejectStockDocument,
            REVERSE: stockDocumentApi.reverseStockDocument,
            SUBMIT: stockDocumentApi.submitStockDocument
        };

        try {
            setMutationLoading(true);
            const updated = await mutations[activeAction](input);
            setDocument(updated);
            showToast(`${stockDocumentActionLabels[activeAction]} thành công.`, true);
            setActiveAction(null);
            setNote("");
            setReason("");
        } catch (mutationError) {
            showToast(
                mutationError instanceof Error
                    ? mutationError.message
                    : "Thao tác chứng từ thất bại.",
                false
            );
        } finally {
            setMutationLoading(false);
        }
    }

    if (!documentId) {
        return <Navigate to="/management/inventory/documents" replace />;
    }

    if (loading) {
        return (
            <section className="grid gap-4">
                <div className="h-8 w-52 animate-pulse rounded-lg bg-[#eef3ef]" />
                <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                    {Array.from({ length: 8 }, (_, index) => (
                        <div
                            className="h-12 animate-pulse rounded-lg bg-[#eef3ef]"
                            key={index}
                        />
                    ))}
                </div>
            </section>
        );
    }

    if (error || !document) {
        return (
            <section className="grid gap-4">
                <StockDocumentBackLink />
                <div className="grid place-items-center rounded-xl border border-red-100 bg-red-50 px-6 py-12 text-center">
                    <div className="grid max-w-sm gap-3 justify-items-center">
                        <strong className="text-base font-black text-red-800">
                            Không thể tải chi tiết
                        </strong>
                        <p className="text-sm font-semibold leading-6 text-red-700">
                            {error || "Không tìm thấy chứng từ kho."}
                        </p>
                        <button
                            className="rounded-lg bg-red-700 px-4 py-2 text-sm font-black text-white"
                            onClick={loadDocument}
                            type="button"
                        >
                            Thử lại
                        </button>
                    </div>
                </div>
            </section>
        );
    }

    return (
        <section className="grid gap-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <StockDocumentBackLink />
            </div>

            <header className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                    <div className="grid gap-2">
                        <div className="text-xs font-black text-[#697684]">
                            Kho › Chứng từ kho › Chi tiết
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                            <h1 className="text-2xl font-black text-[#172317]">
                                {document.code}
                            </h1>
                            <StockDocumentTypeBadge type={document.type} />
                            <StockDocumentStatusBadge
                                status={document.status}
                            />
                        </div>
                        <p className="text-sm font-semibold text-[#536078]">
                            Tham chiếu nghiệp vụ:{" "}
                            <strong className="text-[#172317]">
                                {document.referenceCode ?? "-"}
                            </strong>
                        </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        {document.referenceId ? (
                            <Link
                                className="h-10 rounded-lg border border-[#d8eadc] px-4 py-2 text-sm font-black text-[#159734] hover:bg-[#f4faf5]"
                                to={originalBusinessPath(document)}
                            >
                                Quay về nghiệp vụ gốc
                            </Link>
                        ) : null}
                        {availableActions.length === 0 ? (
                            <StockDocumentReadonlyAlert />
                        ) : null}
                        {availableActions.map((action) => {
                            const allowedRoles = allowedRolesForAction(
                                action,
                                document
                            );

                            if (!hasAnyRole(roles, allowedRoles)) {
                                return null;
                            }

                            return (
                                <button
                                    className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:cursor-not-allowed disabled:opacity-60"
                                    disabled={mutationLoading}
                                    key={action}
                                    onClick={() => setActiveAction(action)}
                                    type="button"
                                >
                                    {stockDocumentActionLabels[action]}
                                </button>
                            );
                        })}
                    </div>
                </div>

                {document.status === "REVERSED" ? (
                    <div className="rounded-lg border border-purple-200 bg-purple-50 px-3 py-2 text-sm font-bold text-purple-800">
                        Chứng từ đã đảo. Chứng từ đảo:{" "}
                        {document.reversedDocumentId ? (
                            <Link
                                className="text-purple-900 underline"
                                to={`/management/inventory/documents/${document.reversedDocumentId}`}
                            >
                                {document.reversedDocumentCode}
                            </Link>
                        ) : (
                            "-"
                        )}
                    </div>
                ) : null}
            </header>

            <nav className="flex gap-2 overflow-x-auto border-b border-[#e8eef0] pb-1">
                {[
                    ["general", "Thông tin chung"],
                    ["lines", "Dòng hàng"],
                    ["batches", "Phân bổ lô"],
                    ["audit", "Audit"]
                ].map(([value, label]) => (
                    <button
                        className={`h-10 rounded-lg px-4 text-sm font-black ${
                            tab === value
                                ? "bg-[#159734] text-white"
                                : "bg-white text-[#536078] hover:bg-[#f4faf5]"
                        }`}
                        key={value}
                        onClick={() => setTab(value as DetailTab)}
                        type="button"
                    >
                        {label}
                    </button>
                ))}
            </nav>

            {tab === "general" ? (
                <GeneralTab document={document} />
            ) : null}
            {tab === "lines" ? (
                <StockDocumentLinesTable
                    canViewCost={canViewCost}
                    lines={document.lines}
                />
            ) : null}
            {tab === "batches" ? (
                <StockDocumentBatchTable lines={document.lines} />
            ) : null}
            {tab === "audit" ? (
                <StockDocumentAuditTimeline document={document} />
            ) : null}

            <StockDocumentActionDialog
                action={activeAction}
                loading={mutationLoading}
                note={note}
                onCancel={() => {
                    setActiveAction(null);
                    setNote("");
                    setReason("");
                }}
                onConfirm={confirmAction}
                onNoteChange={setNote}
                onReasonChange={setReason}
                open={Boolean(activeAction)}
                reason={reason}
            />

            {toast ? (
                <div
                    className={`fixed bottom-6 right-6 z-50 rounded-xl px-5 py-3 text-sm font-black text-white shadow-pos-panel ${
                        toast.success ? "bg-[#103b2b]" : "bg-red-700"
                    }`}
                    role="status"
                >
                    {toast.message}
                </div>
            ) : null}
        </section>
    );
}

function GeneralTab({ document }: { document: StockDocumentDetail }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-2 xl:grid-cols-3">
            <Info label="Loại chứng từ" value={stockDocumentTypeLabels[document.type]} />
            <Info label="Kho nguồn" value={document.sourceLocation?.name ?? "-"} />
            <Info
                label="Kho đích"
                value={document.destinationLocation?.name ?? "-"}
            />
            <Info
                label="Ngày chứng từ"
                value={formatVietnamDateTime(document.createdAt)}
            />
            <Info
                label="Ngày post"
                value={
                    document.postedAt
                        ? formatVietnamDateTime(document.postedAt)
                        : "-"
                }
            />
            <Info label="Người tạo" value={document.createdBy.name} />
            <Info label="Người duyệt" value={document.approvedBy?.name ?? "-"} />
            <Info label="Người post" value={document.postedBy?.name ?? "-"} />
            <Info label="Lý do" value={document.reasonCode ?? "-"} />
            <Info label="Ghi chú" value={document.note ?? "-"} />
            <Info label="Correlation ID" value={document.correlationId ?? "-"} />
            <Info
                label="Reference type"
                value={document.referenceType ?? "-"}
            />
            <Info label="Reference ID" value={document.referenceId ?? "-"} />
            <Info
                label="Role người tạo"
                value={stockDocumentRoleLabels[document.createdBy.role]}
            />
        </section>
    );
}

function Info({ label, value }: { label: string; value: string }) {
    return (
        <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2">
            <span className="text-xs font-black text-[#697684]">{label}</span>
            <strong className="text-sm font-black text-[#172317]">
                {value}
            </strong>
        </div>
    );
}
