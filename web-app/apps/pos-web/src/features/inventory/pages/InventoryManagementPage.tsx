import {
    AlertTriangle,
    ClipboardList,
    DollarSign,
    Plus,
    ShieldCheck,
    Truck
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Navigate, useParams } from "react-router-dom";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    BatchAllocationTable,
    BatchSelect,
    ConfirmInventoryActionDialog,
    EvidenceUpload,
    InventoryApprovalPanel,
    InventoryAuditPanel,
    InventoryDataTable,
    InventoryEmptyState,
    InventoryErrorState,
    InventoryFilterBar,
    InventoryLoadingSkeleton,
    InventoryPageHeader,
    InventoryReferenceLink,
    InventoryStatusBadge,
    InventorySummaryCard,
    LocationSelect,
    QuantityInput,
    ReasonCodeSelect,
    RoleRestrictedAction,
    SkuSelect,
    UnitOfMeasureSelect
} from "../components/InventoryManagementComponents";
import {
    inventoryActionLabels,
    inventoryDocumentTypeLabels,
    inventoryWorkspaceConfigs
} from "../constants/inventory-management.constants";
import { useInventoryQueryState } from "../hooks/useInventoryQueryState";
import { inventoryManagementApi } from "../services/inventoryManagement.api";
import { stockAlertApi } from "../services/stockAlert.api";
import type {
    InventoryActionType,
    InventoryBatchOption,
    InventoryCreateDraftInput,
    InventoryLocationOption,
    InventoryManagementListResult,
    InventoryManagementRecord,
    InventoryRole,
    InventorySkuOption,
    InventoryWorkspaceId
} from "../types/inventory-management.types";
import type { StockAlertListResult } from "../types/stock-alert.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";
import { routePaths } from "../../../routes/path";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";

import { useAuth } from "../../../auth/auth-context";

const workspaceIds = inventoryWorkspaceConfigs.map((config) => config.id);

interface FormState {
    batchId: string;
    locationId: string;
    note: string;
    quantity: number;
    reasonCode: string;
    skuId: string;
    title: string;
    unit: string;
}

interface OptionsState {
    batches: InventoryBatchOption[];
    locations: InventoryLocationOption[];
    skus: InventorySkuOption[];
}

function isWorkspaceId(value: string | undefined): value is InventoryWorkspaceId {
    return Boolean(value && workspaceIds.includes(value as InventoryWorkspaceId));
}

export function InventoryManagementPage() {
    const params = useParams();
    const workspaceId = params.workspaceId;
    const { roles } = useAuth();
    const effectiveRole: InventoryRole =
        roles?.includes("ADMIN") || roles?.includes("BRANCH_MANAGER")
            ? "INVENTORY_MANAGER"
            : roles?.includes("ACCOUNTANT") || roles?.includes("AUDITOR")
            ? "AUDITOR"
            : roles?.includes("WAREHOUSE_STAFF") || roles?.includes("WAREHOUSE")
            ? "WAREHOUSE_STAFF"
            : roles?.includes("SALES_STAFF") || roles?.includes("STAFF")
            ? "BRANCH_STAFF"
            : "INVENTORY_MANAGER";

    const { query, setQuery } = useInventoryQueryState();
    const [result, setResult] = useState<InventoryManagementListResult | null>(
        null
    );
    const [alertWidget, setAlertWidget] =
        useState<StockAlertListResult["summary"] | null>(null);
    const [options, setOptions] = useState<OptionsState>({
        batches: [],
        locations: [],
        skus: []
    });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);
    const [selected, setSelected] =
        useState<InventoryManagementRecord | null>(null);
    const [toast, setToast] = useState<{
        message: string;
        success: boolean;
    } | null>(null);
    const [createOpen, setCreateOpen] = useState(false);
    const [formDirty, setFormDirty] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState("");
    const [confirmAction, setConfirmAction] = useState<{
        action: InventoryActionType;
        record: InventoryManagementRecord;
    } | null>(null);
    const [confirmLoading, setConfirmLoading] = useState(false);

    const config = useMemo(
        () =>
            inventoryWorkspaceConfigs.find(
                (item) => item.id === workspaceId
            ),
        [workspaceId]
    );

    const initialSkuId = options.skus[0]?.id ?? "";
    const initialLocationId = options.locations[0]?.id ?? "";
    const initialBatchId = options.batches[0]?.id ?? "";
    const [form, setForm] = useState<FormState>({
        batchId: "",
        locationId: "",
        note: "",
        quantity: 1,
        reasonCode: "SUPPLIER_DELIVERY",
        skuId: "",
        title: "",
        unit: "gói"
    });

    useEffect(() => {
        void inventoryManagementApi.getOptions().then(setOptions);
    }, []);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            void stockAlertApi
                .listStockAlerts({
                    createdFrom: "",
                    hasAction: "ALL",
                    locationId: "ALL",
                    page: 0,
                    role: "ADMIN",
                    severity: "ALL",
                    size: 5,
                    skuOrCategory: "",
                    status: "OPEN",
                    type: "ALL"
                })
                .then((response) => {
                    if (active) setAlertWidget(response.summary);
                });
        });
        return () => {
            active = false;
        };
    }, []);

    useEffect(() => {
        if (!workspaceId || !isWorkspaceId(workspaceId)) {
            return;
        }

        let active = true;
        queueMicrotask(() => {
            if (active) {
                setLoading(true);
                setError("");
            }
        });

        void inventoryManagementApi
            .list(workspaceId, query)
            .then((response) => {
                if (active) {
                    setResult(response);
                    setSelected((current) => {
                        if (!current) {
                            return response.content[0] ?? null;
                        }

                        return (
                            response.content.find(
                                (record) =>
                                    String(record.id) === String(current.id)
                            ) ??
                            response.content[0] ??
                            null
                        );
                    });
                }
            })
            .catch((loadError: unknown) => {
                if (active) {
                    setError(
                        loadError instanceof Error
                            ? loadError.message
                            : "Không thể tải dữ liệu Inventory."
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
    }, [query, refreshKey, workspaceId]);

    useEffect(() => {
        if (!formDirty) {
            return;
        }

        function handleBeforeUnload(event: BeforeUnloadEvent) {
            event.preventDefault();
            event.returnValue = "";
        }

        window.addEventListener("beforeunload", handleBeforeUnload);
        return () =>
            window.removeEventListener("beforeunload", handleBeforeUnload);
    }, [formDirty]);

    const showToast = useCallback((message: string, success: boolean) => {
        setToast({ message, success });
        window.setTimeout(() => setToast(null), 2600);
    }, []);

    const openCreateDialog = useCallback(() => {
        setForm({
            batchId: initialBatchId,
            locationId: initialLocationId,
            note: "",
            quantity: 1,
            reasonCode: "SUPPLIER_DELIVERY",
            skuId: initialSkuId,
            title: "",
            unit: "gói"
        });
        setFormDirty(false);
        setFormError("");
        setCreateOpen(true);
    }, [initialBatchId, initialLocationId, initialSkuId]);

    if (!isWorkspaceId(workspaceId) || !config) {
        return <Navigate to={routePaths.inventoryDocuments} replace />;
    }

    const activeWorkspaceId: InventoryWorkspaceId = workspaceId;
    const createAllowedRoles = config.allowedActions.CREATE ?? [];
    const actionAllowedRoles = {
        APPROVE: config.allowedActions.APPROVE ?? [],
        CANCEL: config.allowedActions.CANCEL ?? [],
        COMPLETE: config.allowedActions.COMPLETE ?? []
    };

    function updateForm(patch: Partial<FormState>) {
        setForm((current) => ({ ...current, ...patch }));
        setFormDirty(true);
        setFormError("");
    }

    function closeCreateDialog() {
        if (
            formDirty &&
            !window.confirm(
                "Bạn có thay đổi chưa lưu. Bạn có chắc muốn rời form?"
            )
        ) {
            return;
        }

        setCreateOpen(false);
        setFormDirty(false);
        setFormError("");
    }

    async function submitCreate() {
        if (!form.title.trim()) {
            setFormError("Vui lòng nhập tiêu đề chứng từ.");
            return;
        }

        if (!form.skuId || !form.locationId) {
            setFormError("Vui lòng chọn mặt hàng và kho xử lý.");
            return;
        }

        if (!Number.isFinite(form.quantity) || form.quantity <= 0) {
            setFormError("Số lượng phải lớn hơn 0.");
            return;
        }

        const input: InventoryCreateDraftInput = {
            locationId: form.locationId,
            note: form.note,
            quantity: form.quantity,
            reasonCode: form.reasonCode,
            skuId: form.skuId,
            title: form.title,
            unit: form.unit,
            workspaceId: activeWorkspaceId
        };

        try {
            setSubmitting(true);
            await inventoryManagementApi.createDraft(input);
            showToast("Tạo chứng từ kho thành công.", true);
            setCreateOpen(false);
            setFormDirty(false);
            setForm({
                batchId: initialBatchId,
                locationId: initialLocationId,
                note: "",
                quantity: 1,
                reasonCode: "SUPPLIER_DELIVERY",
                skuId: initialSkuId,
                title: "",
                unit: "gói"
            });
            setRefreshKey((value) => value + 1);
        } catch (submitError) {
            showToast(
                submitError instanceof Error
                    ? submitError.message
                    : "Không thể tạo chứng từ kho.",
                false
            );
        } finally {
            setSubmitting(false);
        }
    }

    async function confirmImportantAction() {
        if (!confirmAction) {
            return;
        }

        try {
            setConfirmLoading(true);
            await inventoryManagementApi.runAction({
                action: confirmAction.action,
                note: "Xác nhận từ giao diện Inventory Management.",
                recordId: confirmAction.record.id,
                role: effectiveRole
            });
            showToast(
                `${inventoryActionLabels[confirmAction.action]} thành công.`,
                true
            );
            setConfirmAction(null);
            setRefreshKey((value) => value + 1);
        } catch (actionError) {
            showToast(
                actionError instanceof Error
                    ? actionError.message
                    : "Thao tác kho thất bại.",
                false
            );
        } finally {
            setConfirmLoading(false);
        }
    }

    const records = result?.content ?? [];
    const summary = result?.summary;

    return (
        <section className="grid gap-4">
            <InventoryPageHeader
                description={config.description}
                eyebrow="Inventory Management"
                title={config.title}
                actions={
                    <>
                        {config.createLabel ? (
                            <RoleRestrictedAction
                                allowedRoles={createAllowedRoles}
                                currentRole={effectiveRole}
                            >
                                <button
                                    className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white shadow-pos-card hover:bg-[#0f7d2b]"
                                    onClick={openCreateDialog}
                                    type="button"
                                >
                                    <Plus size={17} aria-hidden="true" />
                                    {config.createLabel}
                                </button>
                            </RoleRestrictedAction>
                        ) : null}
                    </>
                }
            />

            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                <InventorySummaryCard
                    icon={<ClipboardList size={18} aria-hidden="true" />}
                    label="Tổng chứng từ"
                    value={formatVietnamNumber(summary?.totalRecords ?? 0)}
                />
                <InventorySummaryCard
                    icon={<ShieldCheck size={18} aria-hidden="true" />}
                    label="Chờ duyệt"
                    value={formatVietnamNumber(summary?.pendingApproval ?? 0)}
                />
                <InventorySummaryCard
                    icon={<AlertTriangle size={18} aria-hidden="true" />}
                    label="Cần chú ý"
                    value={formatVietnamNumber(summary?.warningRecords ?? 0)}
                />
                <InventorySummaryCard
                    icon={<DollarSign size={18} aria-hidden="true" />}
                    label="Giá trị tồn liên quan"
                    value={formatVnd(summary?.totalValue ?? 0)}
                />
            </div>

            {alertWidget ? (
                <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-5">
                    <InventorySummaryCard
                        icon={<AlertTriangle size={18} aria-hidden="true" />}
                        label="Top SKU dưới reorder point"
                        value={formatVietnamNumber(
                            alertWidget.belowReorderPoint
                        )}
                    />
                    <InventorySummaryCard
                        icon={<AlertTriangle size={18} aria-hidden="true" />}
                        label="Hàng hết"
                        value={formatVietnamNumber(alertWidget.outOfStock)}
                    />
                    <InventorySummaryCard
                        icon={<Truck size={18} aria-hidden="true" />}
                        label="Hàng đang về"
                        value={formatVietnamNumber(
                            alertWidget.transferNeeded
                        )}
                    />
                    <InventorySummaryCard
                        icon={<Truck size={18} aria-hidden="true" />}
                        label="Transfer đang vận chuyển"
                        value={formatVietnamNumber(
                            alertWidget.transferNeeded
                        )}
                    />
                    <InventorySummaryCard
                        icon={<AlertTriangle size={18} aria-hidden="true" />}
                        label="Alerts quá SLA"
                        value={formatVietnamNumber(alertWidget.overSla)}
                    />
                </div>
            ) : null}

            <InventoryFilterBar
                locations={options.locations}
                onChange={setQuery}
                query={query}
            />

            {loading ? <InventoryLoadingSkeleton /> : null}
            {!loading && error ? (
                <InventoryErrorState
                    message={error}
                    onRetry={() => setRefreshKey((value) => value + 1)}
                />
            ) : null}
            {!loading && !error && records.length === 0 ? (
                <InventoryEmptyState
                    action={
                        config.createLabel ? (
                            <button
                                className="rounded-lg bg-[#159734] px-4 py-2 text-sm font-black text-white"
                                onClick={openCreateDialog}
                                type="button"
                            >
                                {config.createLabel}
                            </button>
                        ) : null
                    }
                />
            ) : null}
            {!loading && !error && records.length > 0 ? (
                <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
                    <div className="grid min-w-0 gap-3">
                        <InventoryDataTable
                            activeRecordId={
                                selected ? String(selected.id) : undefined
                            }
                            onSortToggle={() =>
                                setQuery({
                                    page: 0,
                                    sortDirection:
                                        query.sortDirection === "asc"
                                            ? "desc"
                                            : "asc"
                                })
                            }
                            onView={setSelected}
                            records={records}
                            sortDirection={query.sortDirection}
                        />
                        <Pagination
                            onPageChange={(page) =>
                                setQuery({ page: page - 1 })
                            }
                            onPageSizeChange={(size) =>
                                setQuery({ page: 0, size })
                            }
                            page={query.page + 1}
                            pageCount={result?.totalPages ?? 1}
                            pageSize={query.size}
                            totalItems={result?.totalElements ?? 0}
                            totalItemsLabel="Tổng số chứng từ"
                        />
                    </div>

                    <QuickViewPanel
                        actionAllowedRoles={actionAllowedRoles}
                        currentRole={effectiveRole}
                        onAction={(action, record) =>
                            setConfirmAction({ action, record })
                        }
                        record={selected}
                    />
                </div>
            ) : null}

            <CreateInventoryDialog
                batches={options.batches}
                error={formError}
                form={form}
                locations={options.locations}
                onClose={closeCreateDialog}
                onSubmit={submitCreate}
                onUpdate={updateForm}
                open={createOpen}
                skus={options.skus}
                submitting={submitting}
                title={config.createLabel ?? "Tạo chứng từ"}
            />

            <ConfirmInventoryActionDialog
                action={confirmAction?.action ?? "APPROVE"}
                loading={confirmLoading}
                onCancel={() => setConfirmAction(null)}
                onConfirm={confirmImportantAction}
                open={Boolean(confirmAction)}
                record={confirmAction?.record ?? null}
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

function QuickViewPanel({
    actionAllowedRoles,
    currentRole,
    onAction,
    record
}: {
    actionAllowedRoles: Record<
        "APPROVE" | "CANCEL" | "COMPLETE",
        InventoryRole[]
    >;
    currentRole: InventoryRole;
    onAction: (
        action: "APPROVE" | "CANCEL" | "COMPLETE",
        record: InventoryManagementRecord
    ) => void;
    record: InventoryManagementRecord | null;
}) {
    if (!record) {
        return (
            <aside className="rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                <InventoryEmptyState message="Chọn một dòng để xem nhanh chi tiết chứng từ." />
            </aside>
        );
    }

    return (
        <aside className="grid max-h-[calc(100dvh-180px)] gap-4 overflow-y-auto rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-2">
                <div className="flex items-start justify-between gap-3">
                    <div className="grid gap-1">
                        <strong className="text-base font-black text-[#172317]">
                            {record.code}
                        </strong>
                        <span className="text-sm font-semibold text-[#697684]">
                            {inventoryDocumentTypeLabels[record.documentType]}
                        </span>
                    </div>
                    <InventoryStatusBadge status={record.status} />
                </div>
                <p className="text-sm font-semibold leading-6 text-[#536078]">
                    {record.description}
                </p>
                <InventoryReferenceLink code={record.referenceCode} />
            </div>

            <div className="grid gap-2 rounded-xl bg-[#f7f8fb] p-3 text-sm font-semibold text-[#536078]">
                <p>
                    Mặt hàng:{" "}
                    <strong className="text-[#172317]">
                        {record.itemName}
                    </strong>
                </p>
                <p>
                    Số lượng:{" "}
                    <strong className="text-[#172317]">
                        {formatVietnamNumber(record.quantity)} {record.unit}
                    </strong>
                </p>
                <p>
                    Kho:{" "}
                    <strong className="text-[#172317]">
                        {record.targetLocationName
                            ? `${record.locationName} -> ${record.targetLocationName}`
                            : record.locationName}
                    </strong>
                </p>
                <p>
                    Cập nhật:{" "}
                    <strong className="text-[#172317]">
                        {formatVietnamDateTime(record.updatedAt)}
                    </strong>
                </p>
            </div>

            <div className="grid gap-2">
                <h3 className="text-sm font-black text-[#172317]">
                    Phân bổ batch
                </h3>
                <BatchAllocationTable
                    allocations={record.batchAllocations}
                />
            </div>

            <InventoryApprovalPanel steps={record.approvals} />
            <InventoryAuditPanel record={record} />

            <div className="grid gap-2 border-t border-[#edf0f4] pt-3">
                {(["APPROVE", "COMPLETE", "CANCEL"] as const).map(
                    (action) => (
                        <RoleRestrictedAction
                            allowedRoles={actionAllowedRoles[action]}
                            currentRole={currentRole}
                            key={action}
                        >
                            <button
                                className="h-10 rounded-lg border border-[#d8eadc] bg-white px-3 text-sm font-black text-[#159734] hover:bg-[#f4faf5]"
                                onClick={() => onAction(action, record)}
                                type="button"
                            >
                                {inventoryActionLabels[action]}
                            </button>
                        </RoleRestrictedAction>
                    )
                )}
            </div>
        </aside>
    );
}

function CreateInventoryDialog({
    batches,
    error,
    form,
    locations,
    onClose,
    onSubmit,
    onUpdate,
    open,
    skus,
    submitting,
    title
}: {
    batches: InventoryBatchOption[];
    error: string;
    form: FormState;
    locations: InventoryLocationOption[];
    onClose: () => void;
    onSubmit: () => void;
    onUpdate: (patch: Partial<FormState>) => void;
    open: boolean;
    skus: InventorySkuOption[];
    submitting: boolean;
    title: string;
}) {
    return (
        <Dialog onOpenChange={(nextOpen) => !nextOpen && onClose()} open={open}>
            <DialogContent className="max-w-[760px]">
                <DialogHeader>
                    <DialogTitle>{title}</DialogTitle>
                    <DialogDescription>
                        Nhập thông tin chứng từ. Các trường bắt buộc cần hợp lệ
                        trước khi lưu.
                    </DialogDescription>
                </DialogHeader>
                <div className="grid max-h-[66vh] gap-4 overflow-y-auto px-6 py-5">
                    {error ? (
                        <div
                            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-bold text-red-700"
                            role="alert"
                        >
                            {error}
                        </div>
                    ) : null}

                    <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                        Tiêu đề chứng từ
                        <input
                            aria-label="Tiêu đề chứng từ"
                            className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                            onChange={(event) =>
                                onUpdate({
                                    title: event.currentTarget.value
                                })
                            }
                            placeholder="Ví dụ: Nhập matcha đợt tháng 8"
                            value={form.title}
                        />
                    </label>

                    <div className="grid gap-3 md:grid-cols-2">
                        <SkuSelect
                            onChange={(skuId) => onUpdate({ skuId })}
                            skus={skus}
                            value={form.skuId}
                        />
                        <LocationSelect
                            label="Kho xử lý"
                            locations={locations}
                            onChange={(locationId) =>
                                onUpdate({ locationId })
                            }
                            value={form.locationId}
                        />
                        <QuantityInput
                            label="Số lượng"
                            onChange={(quantity) =>
                                onUpdate({ quantity })
                            }
                            value={form.quantity}
                        />
                        <UnitOfMeasureSelect
                            onChange={(unit) => onUpdate({ unit })}
                            value={form.unit}
                        />
                        <BatchSelect
                            batches={batches}
                            onChange={(batchId) => onUpdate({ batchId })}
                            value={form.batchId}
                        />
                        <ReasonCodeSelect
                            onChange={(reasonCode) =>
                                onUpdate({ reasonCode })
                            }
                            value={form.reasonCode}
                        />
                    </div>

                    <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                        Ghi chú
                        <textarea
                            aria-label="Ghi chú chứng từ"
                            className="min-h-24 rounded-lg border border-pos-border px-3 py-2 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                            onChange={(event) =>
                                onUpdate({ note: event.currentTarget.value })
                            }
                            placeholder="Nhập ghi chú kiểm tra, điều kiện hàng hóa hoặc lý do xử lý..."
                            value={form.note}
                        />
                    </label>

                    <EvidenceUpload onDirty={() => onUpdate({})} />
                </div>
                <DialogFooter>
                    <button
                        className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]"
                        disabled={submitting}
                        onClick={onClose}
                        type="button"
                    >
                        Hủy
                    </button>
                    <button
                        className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:cursor-not-allowed disabled:opacity-60"
                        disabled={submitting}
                        onClick={onSubmit}
                        type="button"
                    >
                        {submitting ? "Đang lưu..." : "Lưu chứng từ"}
                    </button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
