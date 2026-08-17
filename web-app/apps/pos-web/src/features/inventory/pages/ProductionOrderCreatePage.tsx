import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import {
    BatchAllocationTable,
    MaterialTable,
    OutputTable,
    PrioritySelect,
    SelectFilter,
    TextFilter
} from "../components/ProductionOrderComponents";
import { productionOrderApi } from "../services/productionOrder.api";
import type {
    ProductionOrderCreateInput,
    ProductionOrderOptions,
    ProductionPriority
} from "../types/production-order.types";

interface OutputDraft {
    finishedSkuId: string;
    plannedExpiryAt: string;
    plannedQuantity: number;
}

const steps = [
    "Thông tin chung",
    "Chọn thành phẩm",
    "Tổng hợp BOM",
    "Chọn SKU nguyên liệu",
    "Xem trước FEFO",
    "Kiểm tra"
];

export function ProductionOrderCreatePage() {
    const navigate = useNavigate();
    const [options, setOptions] = useState<ProductionOrderOptions>({
        finishedSkus: [],
        locations: [],
        materialSkus: [],
        users: []
    });
    const [step, setStep] = useState(0);
    const [dirty, setDirty] = useState(false);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [general, setGeneral] = useState({
        dueAt: "2026-08-12T17:00",
        locationId: "",
        note: "",
        ownerId: "",
        plannedStartAt: "2026-08-03T08:00",
        priority: "NORMAL" as ProductionPriority,
        replenishmentReference: "",
        title: ""
    });
    const [outputs, setOutputs] = useState<OutputDraft[]>([
        { finishedSkuId: "", plannedExpiryAt: "2027-02-01", plannedQuantity: 100 }
    ]);

    useEffect(() => {
        void productionOrderApi.getProductionOrderOptions().then((response) => {
            setOptions(response);
            setGeneral((current) => ({
                ...current,
                locationId: current.locationId || response.locations[0]?.id || "",
                ownerId: current.ownerId || response.users[1]?.id || response.users[0]?.id || ""
            }));
            setOutputs((current) =>
                current.map((output) => ({
                    ...output,
                    finishedSkuId: output.finishedSkuId || response.finishedSkus[0]?.id || ""
                }))
            );
        });
    }, []);

    useEffect(() => {
        if (!dirty) return;
        function handleBeforeUnload(event: BeforeUnloadEvent) {
            event.preventDefault();
            event.returnValue = "";
        }
        window.addEventListener("beforeunload", handleBeforeUnload);
        return () => window.removeEventListener("beforeunload", handleBeforeUnload);
    }, [dirty]);

    const previewOrder = useMemo(() => {
        const mappedOutputs = outputs.map((output) => {
            const sku = options.finishedSkus.find((item) => item.id === output.finishedSkuId);
            return {
                bomVersionId: sku?.activeBomVersionId ?? "",
                bomVersionLabel: sku?.activeBomVersionLabel ?? "Chưa có BOM Active",
                completedQuantity: 0,
                finishedSkuId: output.finishedSkuId,
                packageSize: sku?.packageSize ?? "-",
                plannedExpiryAt: output.plannedExpiryAt,
                plannedQuantity: output.plannedQuantity,
                rejectedQuantity: 0,
                skuCode: sku?.skuCode ?? "-",
                skuName: sku?.skuName ?? "-",
                unit: sku?.unit ?? "-"
            };
        });
        const total = mappedOutputs.reduce((sum, output) => sum + output.plannedQuantity, 0);
        const materials = options.materialSkus.map((material, index) => {
            const required = index === 0 ? total * 0.18 : index === 1 ? total * 0.06 : total;
            return {
                allocations: [
                    {
                        allocatedQuantity: required,
                        availableQuantity: required + 20,
                        batchCode: `FEFO-PREVIEW-${index + 1}`,
                        blocked: false,
                        expiryDate: "2026-10-30T00:00:00.000Z",
                        id: `preview-${index}`,
                        locationName: "Kho nguyên liệu A",
                        remainingAfterAllocation: 20,
                        warning: index === 0 ? "Gần hết hạn" : undefined
                    }
                ],
                availableQuantity: required + 20,
                baseUnit: material.specification.includes("kg") ? "kg" : "cái",
                bomVersionSource: "BOM active từ output",
                consumedBaseQuantity: 0,
                conversionFactor: material.conversionFactor,
                hasAlternative: index !== 2,
                materialProductId: material.id,
                materialProductName: material.name,
                preferredSkuCode: material.skuCode,
                requiredBaseQuantity: required,
                requiredSkuQuantity: required / material.conversionFactor,
                reservedBaseQuantity: required,
                selectedSkuCode: material.skuCode,
                selectedSkuId: material.id,
                specification: material.specification,
                usedByOutputs: mappedOutputs.map((output) => output.skuCode),
                wasteTolerancePercent: index === 2 ? 2 : 5
            };
        });
        return { materials, outputs: mappedOutputs };
    }, [options.finishedSkus, options.materialSkus, outputs]);

    function markDirty() {
        setDirty(true);
        setError("");
    }

    function updateOutput(index: number, patch: Partial<OutputDraft>) {
        setOutputs((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item));
        markDirty();
    }

    async function save(submit: boolean) {
        const input: ProductionOrderCreateInput = {
            dueAt: new Date(general.dueAt).toISOString(),
            locationId: general.locationId,
            note: general.note,
            outputs: outputs.map((output) => ({
                finishedSkuId: output.finishedSkuId,
                plannedExpiryAt: new Date(output.plannedExpiryAt).toISOString(),
                plannedQuantity: output.plannedQuantity
            })),
            ownerId: general.ownerId,
            plannedStartAt: new Date(general.plannedStartAt).toISOString(),
            priority: general.priority,
            replenishmentReference: general.replenishmentReference || undefined,
            submit,
            title: general.title
        };
        try {
            setSubmitting(true);
            const order = await productionOrderApi.createProductionOrder(input);
            setDirty(false);
            navigate(`/management/inventory/production-orders/${order.id}`);
        } catch (saveError) {
            setError(saveError instanceof Error ? saveError.message : "Không thể lưu LSX.");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section className="grid gap-4">
            <header className="grid gap-1">
                <Link className="text-sm font-black text-[#159734] hover:underline" to="/management/inventory/production-orders">Quay lại danh sách</Link>
                <h1 className="text-2xl font-black text-[#172317]">Tạo lệnh sản xuất</h1>
                <p className="text-sm font-semibold text-[#697684]">Wizard hỗ trợ nhiều output, BOM active, chọn SKU nguyên liệu và preview FEFO.</p>
            </header>
            <nav className="flex gap-2 overflow-x-auto">{steps.map((label,index)=><button className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${step===index ? "bg-[#159734] text-white" : "bg-white text-[#536078]"}`} key={label} onClick={()=>setStep(index)} type="button">{index+1}. {label}</button>)}</nav>
            {error ? <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-700">{error}</div> : null}
            <div className="rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                {step === 0 ? <div className="grid gap-3 md:grid-cols-2">
                    <SelectFilter label="Kho sản xuất" value={general.locationId} onChange={(locationId)=>{setGeneral({...general, locationId}); markDirty();}} options={options.locations.map((item)=>({label:item.name,value:item.id}))} />
                    <TextFilter label="Tên hoặc mô tả lệnh" value={general.title} onChange={(title)=>{setGeneral({...general, title}); markDirty();}} />
                    <TextFilter label="Planned start date" type="datetime-local" value={general.plannedStartAt} onChange={(plannedStartAt)=>{setGeneral({...general, plannedStartAt}); markDirty();}} />
                    <TextFilter label="Due date" type="datetime-local" value={general.dueAt} onChange={(dueAt)=>{setGeneral({...general, dueAt}); markDirty();}} />
                    <PrioritySelect value={general.priority} onChange={(priority)=>{setGeneral({...general, priority}); markDirty();}} />
                    <SelectFilter label="Người phụ trách" value={general.ownerId} onChange={(ownerId)=>{setGeneral({...general, ownerId}); markDirty();}} options={options.users.map((item)=>({label:item.name,value:item.id}))} />
                    <TextFilter label="Reference kế hoạch bổ sung" value={general.replenishmentReference} onChange={(replenishmentReference)=>{setGeneral({...general, replenishmentReference}); markDirty();}} />
                    <TextFilter label="Ghi chú" value={general.note} onChange={(note)=>{setGeneral({...general, note}); markDirty();}} />
                </div> : null}
                {step === 1 ? <div className="grid gap-3">{outputs.map((output,index)=>{const sku=options.finishedSkus.find((item)=>item.id===output.finishedSkuId); return <div className="grid gap-3 rounded-lg border border-[#e8eef0] p-3 md:grid-cols-3" key={index}>
                    <SelectFilter label="Finished SKU" value={output.finishedSkuId} onChange={(finishedSkuId)=>updateOutput(index,{finishedSkuId})} options={options.finishedSkus.map((item)=>({label:`${item.skuCode} - ${item.skuName}${item.activeBomVersionId ? "" : " (không có BOM)"}`,value:item.id}))} />
                    <Info label="Product name" value={sku?.skuName ?? "-"} />
                    <Info label="Package size" value={sku?.packageSize ?? "-"} />
                    <NumberField label="Planned quantity" value={output.plannedQuantity} onChange={(plannedQuantity)=>updateOutput(index,{plannedQuantity})} />
                    <Info label="UOM" value={sku?.unit ?? "-"} />
                    <TextFilter label="Planned expiry date" type="date" value={output.plannedExpiryAt} onChange={(plannedExpiryAt)=>updateOutput(index,{plannedExpiryAt})} />
                    <Info label="Active BOM version" value={sku?.activeBomVersionLabel ?? "Không có BOM Active"} />
                    <button className="h-10 self-end rounded-lg border border-red-200 px-3 text-sm font-black text-red-700 disabled:opacity-40" disabled={outputs.length===1} onClick={()=>{setOutputs((current)=>current.filter((_,i)=>i!==index)); markDirty();}} type="button">Xóa output</button>
                </div>;})}<button className="h-10 justify-self-start rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" onClick={()=>{setOutputs((current)=>[...current,{ finishedSkuId: options.finishedSkus[0]?.id ?? "", plannedExpiryAt: "2027-02-01", plannedQuantity: 10 }]); markDirty();}} type="button">Thêm output</button></div> : null}
                {step === 2 ? <MaterialTable materials={previewOrder.materials} /> : null}
                {step === 3 ? <MaterialTable materials={previewOrder.materials} /> : null}
                {step === 4 ? <div className="grid gap-3"><BatchAllocationTable materials={previewOrder.materials} /><button className="h-10 justify-self-start rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" type="button">Phân bổ lại FEFO</button><div className="text-sm font-bold text-[#697684]">Manual allocation hiển thị cho ADMIN/WAREHOUSE khi cấu hình cho phép.</div></div> : null}
                {step === 5 ? <div className="grid gap-4"><OutputTable outputs={previewOrder.outputs} /><MaterialTable materials={previewOrder.materials} /><BatchAllocationTable materials={previewOrder.materials} /></div> : null}
            </div>
            <footer className="flex justify-between gap-3">
                <button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]" disabled={step===0} onClick={()=>setStep((current)=>Math.max(0,current-1))} type="button">Quay lại</button>
                <div className="flex gap-2">{step === steps.length - 1 ? <><button className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting} onClick={()=>void save(false)} type="button">Lưu Draft</button><button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={()=>void save(true)} type="button">Submit for Approval</button></> : <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" onClick={()=>setStep((current)=>Math.min(steps.length-1,current+1))} type="button">Tiếp tục</button>}</div>
            </footer>
        </section>
    );
}

function NumberField({ label, onChange, value }: { label: string; onChange: (value: number) => void; value: number }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317]" min={0} onChange={(event)=>onChange(Number(event.currentTarget.value))} type="number" value={value}/></label>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value}</strong></div>;
}
