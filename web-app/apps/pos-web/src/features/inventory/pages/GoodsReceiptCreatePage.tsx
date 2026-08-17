import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import {
    calculateFinalUnitCost,
    calculateLineNetAmount
} from "../constants/goods-receipt.constants";
import { goodsReceiptApi } from "../services/goodsReceipt.api";
import type {
    GoodsReceiptCreateInput,
    GoodsReceiptCreateLineInput,
    GoodsReceiptEvidence,
    GoodsReceiptMaterialOption,
    GoodsReceiptOptions
} from "../types/goods-receipt.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

interface WizardLine {
    countryOfOrigin: string;
    duplicateReason: string;
    expiryDate: string;
    initialDamagedQuantity: number;
    manufacturedAt: string;
    materialId: string;
    orderedQuantity: number;
    qualityCertificate: string;
    receivedQuantity: number;
    storageCondition: string;
    supplierLot: string;
}

const steps = [
    "Thông tin chung",
    "Dòng nguyên liệu",
    "Thông tin batch",
    "Chứng từ và bằng chứng",
    "Kiểm tra và xác nhận"
];

function emptyLine(materialId = ""): WizardLine {
    return {
        countryOfOrigin: "Việt Nam",
        duplicateReason: "",
        expiryDate: "2026-12-31",
        initialDamagedQuantity: 0,
        manufacturedAt: "2026-07-01",
        materialId,
        orderedQuantity: 10,
        qualityCertificate: "",
        receivedQuantity: 10,
        storageCondition: "Khô ráo",
        supplierLot: ""
    };
}

export function GoodsReceiptCreatePage() {
    const navigate = useNavigate();
    const [options, setOptions] = useState<GoodsReceiptOptions>({
        locations: [],
        materials: [],
        suppliers: []
    });
    const [step, setStep] = useState(0);
    const [dirty, setDirty] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [confirmed, setConfirmed] = useState(false);
    const [currentTime] = useState(() => Date.now());
    const [general, setGeneral] = useState({
        deliveryPerson: "",
        invoiceNumber: "",
        note: "",
        outsidePo: false,
        outsidePoReason: "",
        purchaseOrderCode: "",
        receivedAt: "2026-08-02T09:00",
        receivingLocationId: "",
        supplierId: "",
        vehiclePlate: ""
    });
    const [lines, setLines] = useState<WizardLine[]>([emptyLine()]);
    const [evidence, setEvidence] = useState<GoodsReceiptEvidence[]>([]);
    const [varianceNote, setVarianceNote] = useState("");

    useEffect(() => {
        void goodsReceiptApi.getGoodsReceiptOptions().then((response) => {
            setOptions(response);
            setGeneral((current) => ({
                ...current,
                receivingLocationId:
                    current.receivingLocationId || response.locations[0]?.id || "",
                supplierId: current.supplierId || response.suppliers[0]?.id || ""
            }));
            setLines((current) =>
                current.map((line) => ({
                    ...line,
                    materialId: line.materialId || response.materials[0]?.id || ""
                }))
            );
        });
    }, []);

    useEffect(() => {
        if (!dirty) {
            return;
        }

        function handleBeforeUnload(event: BeforeUnloadEvent) {
            event.preventDefault();
            event.returnValue = "";
        }

        window.addEventListener("beforeunload", handleBeforeUnload);
        return () =>
            window.removeEventListener("beforeunload", handleBeforeUnload);
    }, [dirty]);

    const selectedSupplier = options.suppliers.find(
        (supplier) => supplier.id === general.supplierId
    );
    const selectedPo = general.outsidePo
        ? "Nhập ngoài PO"
        : general.purchaseOrderCode || "Chưa nhập PO";
    const totals = useMemo(() => {
        const totalQuantity = lines.reduce(
            (sum, line) => sum + line.receivedQuantity,
            0
        );
        const shortage = lines.filter(
            (line) => line.orderedQuantity > line.receivedQuantity
        ).length;
        const surplus = lines.filter(
            (line) => line.receivedQuantity > line.orderedQuantity
        ).length;
        const damaged = lines.filter(
            (line) => line.initialDamagedQuantity > 0
        ).length;

        return { damaged, shortage, surplus, totalQuantity };
    }, [lines]);

    function markDirty() {
        setDirty(true);
        setError("");
    }

    function updateLine(index: number, patch: Partial<WizardLine>) {
        setLines((current) =>
            current.map((line, lineIndex) =>
                lineIndex === index ? { ...line, ...patch } : line
            )
        );
        markDirty();
    }

    function materialOf(line: WizardLine): GoodsReceiptMaterialOption | undefined {
        return options.materials.find((item) => item.id === line.materialId);
    }

    function expiryWarning(line: WizardLine) {
        const material = materialOf(line);
        if (!material || !line.expiryDate) {
            return "";
        }

        const remainingDays = Math.ceil(
            (new Date(line.expiryDate).getTime() - currentTime) /
                (24 * 60 * 60 * 1000)
        );

        return remainingDays < material.shelfLifeWarningDays
            ? `Cảnh báo: hạn dùng còn ${remainingDays} ngày, thấp hơn ngưỡng ${material.shelfLifeWarningDays} ngày.`
            : "";
    }

    function toInput(submit: boolean): GoodsReceiptCreateInput {
        return {
            deliveryPerson: general.deliveryPerson,
            evidence,
            invoiceNumber: general.invoiceNumber || undefined,
            lines: lines.map<GoodsReceiptCreateLineInput>((line) => ({
                countryOfOrigin: line.countryOfOrigin || undefined,
                duplicateReason: line.duplicateReason || undefined,
                expiryDate: line.expiryDate,
                initialDamagedQuantity: line.initialDamagedQuantity,
                manufacturedAt: line.manufacturedAt,
                materialId: line.materialId,
                orderedQuantity: general.outsidePo
                    ? undefined
                    : line.orderedQuantity,
                qualityCertificate: line.qualityCertificate || undefined,
                receivedQuantity: line.receivedQuantity,
                storageCondition: line.storageCondition || undefined,
                supplierLot: line.supplierLot
            })),
            note: varianceNote || general.note,
            outsidePo: general.outsidePo,
            outsidePoReason: general.outsidePoReason || undefined,
            purchaseOrderCode: general.outsidePo
                ? undefined
                : general.purchaseOrderCode || undefined,
            receivedAt: new Date(general.receivedAt).toISOString(),
            receivingLocationId: general.receivingLocationId,
            submit,
            supplierId: general.supplierId,
            vehiclePlate: general.vehiclePlate || undefined
        };
    }

    async function save(submit: boolean) {
        if (step === 4 && !confirmed) {
            setError("Vui lòng xác nhận thông tin trước khi lưu.");
            return;
        }

        try {
            setSubmitting(true);
            const receipt = await goodsReceiptApi.createGoodsReceipt(
                toInput(submit)
            );
            setDirty(false);
            navigate(`/management/inventory/receipts/${receipt.id}`);
        } catch (saveError) {
            setError(
                saveError instanceof Error
                    ? saveError.message
                    : "Không thể lưu phiếu nhập."
            );
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div className="grid gap-1">
                    <Link
                        className="text-sm font-black text-[#159734] hover:underline"
                        to="/management/inventory/receipts"
                    >
                        Quay lại danh sách
                    </Link>
                    <h1 className="text-2xl font-black text-[#172317]">
                        Tạo phiếu nhập nguyên liệu
                    </h1>
                    <p className="text-sm font-semibold text-[#697684]">
                        Wizard giữ dữ liệu khi chuyển bước. Hàng vật lý được
                        ghi nhận AWAITING_QC, available chỉ tăng sau QC và post.
                    </p>
                </div>
            </header>

            <nav className="flex gap-2 overflow-x-auto">
                {steps.map((label, index) => (
                    <button
                        className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${
                            step === index
                                ? "bg-[#159734] text-white"
                                : "bg-white text-[#536078]"
                        }`}
                        key={label}
                        onClick={() => setStep(index)}
                        type="button"
                    >
                        {index + 1}. {label}
                    </button>
                ))}
            </nav>

            {error ? (
                <div
                    className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-700"
                    role="alert"
                >
                    {error}
                </div>
            ) : null}

            <div className="rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                {step === 0 ? (
                    <div className="grid gap-3 md:grid-cols-2">
                        <SelectField
                            label="Kho nhận"
                            onChange={(receivingLocationId) => {
                                setGeneral((current) => ({
                                    ...current,
                                    receivingLocationId
                                }));
                                markDirty();
                            }}
                            options={options.locations.map((location) => ({
                                label: location.name,
                                value: location.id
                            }))}
                            value={general.receivingLocationId}
                        />
                        <SelectField
                            label="Nhà cung cấp"
                            onChange={(supplierId) => {
                                setGeneral((current) => ({
                                    ...current,
                                    supplierId
                                }));
                                markDirty();
                            }}
                            options={options.suppliers.map((supplier) => ({
                                label: supplier.name,
                                value: supplier.id
                            }))}
                            value={general.supplierId}
                        />
                        <TextField
                            label="Purchase Order"
                            onChange={(purchaseOrderCode) => {
                                setGeneral((current) => ({
                                    ...current,
                                    purchaseOrderCode
                                }));
                                markDirty();
                            }}
                            value={general.purchaseOrderCode}
                        />
                        <TextField
                            label="Số hóa đơn nhà cung cấp"
                            onChange={(invoiceNumber) => {
                                setGeneral((current) => ({
                                    ...current,
                                    invoiceNumber
                                }));
                                markDirty();
                            }}
                            value={general.invoiceNumber}
                        />
                        <TextField
                            label="Ngày giao hàng"
                            onChange={(receivedAt) => {
                                setGeneral((current) => ({
                                    ...current,
                                    receivedAt
                                }));
                                markDirty();
                            }}
                            type="datetime-local"
                            value={general.receivedAt}
                        />
                        <TextField
                            label="Người giao"
                            onChange={(deliveryPerson) => {
                                setGeneral((current) => ({
                                    ...current,
                                    deliveryPerson
                                }));
                                markDirty();
                            }}
                            value={general.deliveryPerson}
                        />
                        <TextField
                            label="Biển số xe"
                            onChange={(vehiclePlate) => {
                                setGeneral((current) => ({
                                    ...current,
                                    vehiclePlate
                                }));
                                markDirty();
                            }}
                            value={general.vehiclePlate}
                        />
                        <label className="flex items-center gap-2 text-sm font-black text-[#536078]">
                            <input
                                checked={general.outsidePo}
                                onChange={(event) => {
                                    setGeneral((current) => ({
                                        ...current,
                                        outsidePo: event.currentTarget.checked
                                    }));
                                    markDirty();
                                }}
                                type="checkbox"
                            />
                            Nhập ngoài PO
                        </label>
                        {general.outsidePo ? (
                            <TextField
                                label="Lý do nhập ngoài PO"
                                onChange={(outsidePoReason) => {
                                    setGeneral((current) => ({
                                        ...current,
                                        outsidePoReason
                                    }));
                                    markDirty();
                                }}
                                value={general.outsidePoReason}
                            />
                        ) : null}
                    </div>
                ) : null}

                {step === 1 ? (
                    <div className="grid gap-3">
                        {lines.map((line, index) => {
                            const material = materialOf(line);
                            const diff =
                                line.receivedQuantity - line.orderedQuantity;

                            return (
                                <div
                                    className="grid gap-3 rounded-lg border border-[#e8eef0] p-3 md:grid-cols-3"
                                    key={index}
                                >
                                    <SelectField
                                        label="Material/SKU"
                                        onChange={(materialId) =>
                                            updateLine(index, { materialId })
                                        }
                                        options={options.materials.map(
                                            (item) => ({
                                                label: `${item.skuCode} - ${item.name}`,
                                                value: item.id
                                            })
                                        )}
                                        value={line.materialId}
                                    />
                                    <Info
                                        label="Tên nguyên liệu"
                                        value={material?.name ?? "-"}
                                    />
                                    <Info
                                        label="UOM nhập"
                                        value={material?.unit ?? "-"}
                                    />
                                    <NumberField
                                        label="Số lượng đặt theo PO"
                                        onChange={(orderedQuantity) =>
                                            updateLine(index, {
                                                orderedQuantity
                                            })
                                        }
                                        value={line.orderedQuantity}
                                    />
                                    <NumberField
                                        label="Số lượng thực nhận"
                                        onChange={(receivedQuantity) =>
                                            updateLine(index, {
                                                receivedQuantity
                                            })
                                        }
                                        value={line.receivedQuantity}
                                    />
                                    <NumberField
                                        label="Số lượng hỏng ban đầu"
                                        onChange={(initialDamagedQuantity) =>
                                            updateLine(index, {
                                                initialDamagedQuantity
                                            })
                                        }
                                        value={line.initialDamagedQuantity}
                                    />
                                    <Info
                                        label="Thiếu/thừa"
                                        value={
                                            diff > 0
                                                ? `Thừa ${formatVietnamNumber(diff)}`
                                                : diff < 0
                                                  ? `Thiếu ${formatVietnamNumber(Math.abs(diff))}`
                                                  : "Khớp"
                                        }
                                    />
                                    <Info
                                        label="Unit conversion preview"
                                        value={`1 ${material?.unit ?? "UOM"} = 1 ${material?.unit ?? "UOM"} nhập kho`}
                                    />
                                    <button
                                        className="h-10 self-end rounded-lg border border-red-200 px-3 text-sm font-black text-red-700 disabled:opacity-40"
                                        disabled={lines.length === 1}
                                        onClick={() => {
                                            setLines((current) =>
                                                current.filter(
                                                    (_, lineIndex) =>
                                                        lineIndex !== index
                                                )
                                            );
                                            markDirty();
                                        }}
                                        type="button"
                                    >
                                        Xóa dòng
                                    </button>
                                </div>
                            );
                        })}
                        <button
                            className="h-10 justify-self-start rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]"
                            onClick={() => {
                                setLines((current) => [
                                    ...current,
                                    emptyLine(options.materials[0]?.id)
                                ]);
                                markDirty();
                            }}
                            type="button"
                        >
                            Thêm dòng
                        </button>
                    </div>
                ) : null}

                {step === 2 ? (
                    <div className="grid gap-3">
                        {lines.map((line, index) => (
                            <div
                                className="grid gap-3 rounded-lg border border-[#e8eef0] p-3 md:grid-cols-3"
                                key={index}
                            >
                                <TextField
                                    label="Supplier lot number"
                                    onChange={(supplierLot) =>
                                        updateLine(index, { supplierLot })
                                    }
                                    value={line.supplierLot}
                                />
                                <Info
                                    label="Internal batch code"
                                    value={`HVT-${materialOf(line)?.skuCode ?? "SKU"}-PREVIEW`}
                                />
                                <TextField
                                    label="Manufacturing date"
                                    onChange={(manufacturedAt) =>
                                        updateLine(index, { manufacturedAt })
                                    }
                                    type="date"
                                    value={line.manufacturedAt}
                                />
                                <TextField
                                    label="Expiry date"
                                    onChange={(expiryDate) =>
                                        updateLine(index, { expiryDate })
                                    }
                                    type="date"
                                    value={line.expiryDate}
                                />
                                <Info
                                    label="Received date"
                                    value={formatVietnamDateTime(
                                        new Date(general.receivedAt).toISOString()
                                    )}
                                />
                                <TextField
                                    label="Country/origin"
                                    onChange={(countryOfOrigin) =>
                                        updateLine(index, { countryOfOrigin })
                                    }
                                    value={line.countryOfOrigin}
                                />
                                <TextField
                                    label="Quality certificate"
                                    onChange={(qualityCertificate) =>
                                        updateLine(index, {
                                            qualityCertificate
                                        })
                                    }
                                    value={line.qualityCertificate}
                                />
                                <TextField
                                    label="Storage condition"
                                    onChange={(storageCondition) =>
                                        updateLine(index, {
                                            storageCondition
                                        })
                                    }
                                    value={line.storageCondition}
                                />
                                {expiryWarning(line) ? (
                                    <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-bold text-amber-800 md:col-span-3">
                                        {expiryWarning(line)}
                                    </div>
                                ) : null}
                            </div>
                        ))}
                    </div>
                ) : null}

                {step === 3 ? (
                    <div className="grid gap-3">
                        <div className="grid gap-3 md:grid-cols-2">
                            {[
                                "Ảnh hàng",
                                "Ảnh nhãn lô",
                                "Phiếu giao hàng",
                                "Hóa đơn",
                                "Certificate"
                            ].map((label) => (
                                <label
                                    className="grid min-h-24 place-items-center rounded-lg border border-dashed border-[#cfdad2] bg-[#f7f8fb] px-3 text-sm font-bold text-[#697684]"
                                    key={label}
                                >
                                    {label}
                                    <input
                                        className="sr-only"
                                        onChange={(event) => {
                                            const file =
                                                event.currentTarget.files?.[0];
                                            if (!file) return;
                                            setEvidence((current) => [
                                                ...current,
                                                {
                                                    fileName: file.name,
                                                    id: `ev-${Date.now()}`,
                                                    type: "GOODS_PHOTO",
                                                    uploadedAt:
                                                        new Date().toISOString(),
                                                    uploadedBy:
                                                        "Người dùng mô phỏng"
                                                }
                                            ]);
                                            markDirty();
                                        }}
                                        type="file"
                                    />
                                </label>
                            ))}
                        </div>
                        <TextField
                            label="Ghi chú chênh lệch"
                            onChange={(value) => {
                                setVarianceNote(value);
                                markDirty();
                            }}
                            value={varianceNote}
                        />
                        {evidence.length > 0 ? (
                            <ul className="grid gap-2 text-sm font-semibold text-[#536078]">
                                {evidence.map((item) => (
                                    <li key={item.id}>{item.fileName}</li>
                                ))}
                            </ul>
                        ) : null}
                    </div>
                ) : null}

                {step === 4 ? (
                    <div className="grid gap-3">
                        <Info
                            label="Supplier"
                            value={selectedSupplier?.name ?? "-"}
                        />
                        <Info label="PO" value={selectedPo} />
                        <Info
                            label="Tổng SKU"
                            value={formatVietnamNumber(lines.length)}
                        />
                        <Info
                            label="Tổng số lượng"
                            value={formatVietnamNumber(totals.totalQuantity)}
                        />
                        <Info
                            label="Dòng thiếu"
                            value={formatVietnamNumber(totals.shortage)}
                        />
                        <Info
                            label="Dòng thừa"
                            value={formatVietnamNumber(totals.surplus)}
                        />
                        <Info
                            label="Dòng hỏng"
                            value={formatVietnamNumber(totals.damaged)}
                        />
                        <Info
                            label="Batch và expiry"
                            value={`${lines.length} batch đã khai báo`}
                        />
                        <label className="flex items-center gap-2 text-sm font-black text-[#536078]">
                            <input
                                checked={confirmed}
                                onChange={(event) =>
                                    setConfirmed(event.currentTarget.checked)
                                }
                                type="checkbox"
                            />
                            Tôi xác nhận thông tin phiếu nhập là đúng.
                        </label>
                        <div className="rounded-lg border border-[#d8eadc] bg-[#f4faf5] p-3 text-sm font-bold text-[#315f3c]">
                            Cost preview:{" "}
                            {formatVietnamNumber(
                                calculateFinalUnitCost({
                                    acceptedQuantity:
                                        totals.totalQuantity || 1,
                                    allocatedShippingCost: 0,
                                    discount: 0,
                                    otherCost: 0,
                                    purchaseUnitPrice: 100000,
                                    tax: 0
                                })
                            )}{" "}
                            VND/UOM. Net amount mẫu:{" "}
                            {formatVietnamNumber(
                                calculateLineNetAmount({
                                    acceptedQuantity: totals.totalQuantity,
                                    allocatedShippingCost: 0,
                                    discount: 0,
                                    otherCost: 0,
                                    purchaseUnitPrice: 100000,
                                    tax: 0
                                })
                            )}
                        </div>
                    </div>
                ) : null}
            </div>

            <footer className="flex flex-wrap justify-between gap-3">
                <button
                    className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]"
                    disabled={step === 0}
                    onClick={() => setStep((current) => Math.max(0, current - 1))}
                    type="button"
                >
                    Quay lại
                </button>
                <div className="flex gap-2">
                    {step === 4 ? (
                        <>
                            <button
                                className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734] disabled:opacity-60"
                                disabled={submitting}
                                onClick={() => void save(false)}
                                type="button"
                            >
                                {submitting ? "Đang lưu..." : "Lưu Draft"}
                            </button>
                            <button
                                className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                                disabled={submitting}
                                onClick={() => void save(true)}
                                type="button"
                            >
                                {submitting ? "Đang submit..." : "Submit"}
                            </button>
                        </>
                    ) : (
                        <button
                            className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white"
                            onClick={() =>
                                setStep((current) =>
                                    Math.min(steps.length - 1, current + 1)
                                )
                            }
                            type="button"
                        >
                            Tiếp tục
                        </button>
                    )}
                </div>
            </footer>
        </section>
    );
}

function TextField({
    label,
    onChange,
    type = "text",
    value
}: {
    label: string;
    onChange: (value: string) => void;
    type?: string;
    value: string;
}) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <input
                aria-label={label}
                className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                onChange={(event) => onChange(event.currentTarget.value)}
                type={type}
                value={value}
            />
        </label>
    );
}

function NumberField({
    label,
    onChange,
    value
}: {
    label: string;
    onChange: (value: number) => void;
    value: number;
}) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <input
                aria-label={label}
                className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                min={0}
                onChange={(event) => onChange(Number(event.currentTarget.value))}
                type="number"
                value={value}
            />
        </label>
    );
}

function SelectField({
    label,
    onChange,
    options,
    value
}: {
    label: string;
    onChange: (value: string) => void;
    options: Array<{ label: string; value: string }>;
    value: string;
}) {
    return (
        <label className="grid gap-1.5 text-xs font-black text-[#536078]">
            {label}
            <select
                aria-label={label}
                className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                onChange={(event) => onChange(event.currentTarget.value)}
                value={value}
            >
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
        </label>
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
