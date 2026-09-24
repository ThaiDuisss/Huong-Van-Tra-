import { Eye, Lock } from "lucide-react";
import type { ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";

import {
    productionPriorityLabels,
    productionPriorityOptions,
    productionRoleLabels,
    productionStatusLabels,
    productionStatusOptions
} from "../constants/production-order.constants";
import type {
    FinishedSkuOption,
    ProductionOrder,
    ProductionOrderListQuery,
    ProductionOrderMaterial,
    ProductionOrderOutput,
    ProductionOrderSummary,
    ProductionPriority,
    ProductionRole,
    ProductionOrderStatus
} from "../types/production-order.types";
import type { InventoryLocationSummary, UserSnapshot } from "../types/stock-document.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber,
    formatVnd
} from "../utils/inventory-format";

const tone: Record<ProductionOrderStatus, string> = {
    CANCELLED: "border-red-200 bg-red-50 text-red-700",
    COMPLETED: "border-emerald-200 bg-emerald-50 text-emerald-700",
    DRAFT: "border-neutral-200 bg-neutral-100 text-neutral-700",
    FAILED: "border-red-200 bg-red-50 text-red-700",
    IN_PROGRESS: "border-amber-200 bg-amber-50 text-amber-700",
    PARTIALLY_COMPLETED: "border-amber-200 bg-amber-50 text-amber-700",
    PENDING_APPROVAL: "border-purple-200 bg-purple-50 text-purple-700",
    RELEASED: "border-blue-200 bg-blue-50 text-blue-700"
};

export function ProductionStatusBadge({ status }: { status: ProductionOrderStatus }) {
    return (
        <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-black ${tone[status]}`}>
            {productionStatusLabels[status]}
        </span>
    );
}

export function PriorityBadge({ priority }: { priority: ProductionPriority }) {
    return (
        <span className="inline-flex rounded-full bg-[#f7f8fb] px-2.5 py-1 text-xs font-black text-[#536078]">
            {productionPriorityLabels[priority]}
        </span>
    );
}

export function ProductionSummaryCard({
    icon,
    label,
    value
}: {
    icon: ReactNode;
    label: string;
    value: number;
}) {
    return (
        <article className="grid min-h-[86px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="flex items-center gap-2 text-sm font-black text-[#315f3c]">
                <span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">
                    {icon}
                </span>
                {label}
            </div>
            <strong className="text-2xl font-black text-[#172317]">{formatVietnamNumber(value)}</strong>
        </article>
    );
}

export function ProductionFilterBar({
    finishedSkus,
    locations,
    onChange,
    onReset,
    query,
    users
}: {
    finishedSkus: FinishedSkuOption[];
    locations: InventoryLocationSummary[];
    onChange: (patch: Partial<ProductionOrderListQuery>) => void;
    onReset: () => void;
    query: ProductionOrderListQuery;
    users: UserSnapshot[];
}) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                <TextFilter label="Mã lệnh" value={query.code} onChange={(code) => onChange({ code, page: 0 })} />
                <SelectFilter label="Kho sản xuất" value={query.locationId} onChange={(locationId) => onChange({ locationId, page: 0 })} options={[{label:"Tất cả", value:"ALL"}, ...locations.map((item)=>({label:item.name,value:item.id}))]} />
                <SelectFilter label="Thành phẩm" value={query.outputSkuId} onChange={(outputSkuId) => onChange({ outputSkuId, page: 0 })} options={[{label:"Tất cả", value:"ALL"}, ...finishedSkus.map((item)=>({label:`${item.skuCode} - ${item.skuName}`,value:item.id}))]} />
                <SelectFilter label="Trạng thái" value={query.status} onChange={(status) => onChange({ status: status as ProductionOrderListQuery["status"], page: 0 })} options={[{label:"Tất cả", value:"ALL"}, ...productionStatusOptions.map((item)=>({label:productionStatusLabels[item],value:item}))]} />
                <TextFilter label="Ngày tạo từ" type="date" value={query.createdFrom} onChange={(createdFrom) => onChange({ createdFrom, page: 0 })} />
                <TextFilter label="Ngày tạo đến" type="date" value={query.createdTo} onChange={(createdTo) => onChange({ createdTo, page: 0 })} />
                <TextFilter label="Due date từ" type="date" value={query.dueFrom} onChange={(dueFrom) => onChange({ dueFrom, page: 0 })} />
                <TextFilter label="Due date đến" type="date" value={query.dueTo} onChange={(dueTo) => onChange({ dueTo, page: 0 })} />
                <SelectFilter label="Người tạo" value={query.createdById} onChange={(createdById) => onChange({ createdById, page: 0 })} options={[{label:"Tất cả", value:"ALL"}, ...users.map((item)=>({label:item.name,value:item.id}))]} />
                <SelectFilter label="Có thiếu nguyên liệu" value={query.hasShortage} onChange={(hasShortage) => onChange({ hasShortage: hasShortage as ProductionOrderListQuery["hasShortage"], page: 0 })} options={[{label:"Tất cả", value:"ALL"},{label:"Có",value:"YES"},{label:"Không",value:"NO"}]} />
                <div className="flex items-end">
                    <button className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]" onClick={onReset} type="button">Xóa bộ lọc</button>
                </div>
            </div>
        </section>
    );
}

export function ProductionTable({ orders }: { orders: ProductionOrderSummary[] }) {
    const navigate = useNavigate();
    return (
        <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
            <table className="w-full min-w-[1180px] border-separate border-spacing-0 text-left text-sm">
                <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
                    <tr>
                        <Th>Mã LSX</Th><Th>Kho</Th><Th>Output</Th><Th align="right">Tổng output</Th><Th>Nguyên liệu</Th><Th>Trạng thái</Th><Th>Due date</Th><Th>Phụ trách</Th><Th>Tiến độ</Th><Th align="right">Thao tác</Th>
                    </tr>
                </thead>
                <tbody>
                    {orders.map((order) => (
                        <tr className="cursor-pointer bg-white hover:bg-[#f8fbf8]" key={order.id} onClick={() => navigate(`/management/inventory/production-orders/${order.id}`)}>
                            <Td strong><Link onClick={(e)=>e.stopPropagation()} className="text-[#159734] hover:underline" to={`/management/inventory/production-orders/${order.id}`}>{order.code}</Link></Td>
                            <Td>{order.location.name}</Td>
                            <Td>{order.outputs.map((item) => item.skuCode).join(", ")}</Td>
                            <Td align="right">{formatVietnamNumber(order.plannedOutputQuantity)}</Td>
                            <Td>{order.hasShortage ? <span className="font-black text-red-700">Thiếu nguyên liệu</span> : "Đủ để giữ"}</Td>
                            <Td><ProductionStatusBadge status={order.status} /></Td>
                            <Td>{order.dueAt ? formatVietnamDateTime(order.dueAt) : "-"}</Td>
                            <Td>{order.owner.name}</Td>
                            <Td><Progress value={order.progressPercent} /></Td>
                            <Td align="right"><Link onClick={(e)=>e.stopPropagation()} className="inline-flex h-9 items-center gap-2 rounded-lg border border-[#d8eadc] px-3 text-xs font-black text-[#159734]" to={`/management/inventory/production-orders/${order.id}`}><Eye size={15}/>Xem</Link></Td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export function Progress({ value }: { value: number }) {
    return (
        <div className="grid gap-1">
            <div className="h-2 overflow-hidden rounded-full bg-[#edf0f4]">
                <span className="block h-full bg-[#159734]" style={{ width: `${Math.min(100, value)}%` }} />
            </div>
            <span className="text-xs font-bold text-[#697684]">{value}%</span>
        </div>
    );
}

export function RoleUnavailable({ role }: { role: ProductionRole }) {
    return <span className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#e8eef0] bg-[#f7f8fb] px-3 text-xs font-black text-[#697684]"><Lock size={14}/>Không khả dụng với {productionRoleLabels[role]}</span>;
}

export function OutputTable({ outputs }: { outputs: ProductionOrderOutput[] }) {
    return <SimpleTable headers={["SKU", "Tên", "BOM version", "Planned", "Completed", "Rejected", "Expiry"]} rows={outputs.map((item)=>[item.skuCode,item.skuName,item.bomVersionLabel,`${formatVietnamNumber(item.plannedQuantity)} ${item.unit}`,formatVietnamNumber(item.completedQuantity),formatVietnamNumber(item.rejectedQuantity),item.plannedExpiryAt ? formatVietnamDateTime(item.plannedExpiryAt) : "-"])} />;
}

export function MaterialTable({ materials }: { materials: ProductionOrderMaterial[] }) {
    return <SimpleTable headers={["Material", "SKU", "Required base", "Required SKU", "Available", "Reserved", "Consumed", "Shortage", "BOM source", "Outputs"]} rows={materials.map((item)=>[item.materialProductName,item.selectedSkuCode,`${formatVietnamNumber(item.requiredBaseQuantity)} ${item.baseUnit}`,formatVietnamNumber(item.requiredSkuQuantity),formatVietnamNumber(item.availableQuantity),formatVietnamNumber(item.reservedBaseQuantity),formatVietnamNumber(item.consumedBaseQuantity),Math.max(0,item.requiredBaseQuantity-item.availableQuantity) > 0 ? `${formatVietnamNumber(item.requiredBaseQuantity-item.availableQuantity)} ${item.baseUnit}` : "Không",item.bomVersionSource,item.usedByOutputs.join(", ")])} />;
}

export function BatchAllocationTable({ materials }: { materials: ProductionOrderMaterial[] }) {
    const rows = materials.flatMap((material) => material.allocations.map((allocation)=>[material.selectedSkuCode, allocation.batchCode, formatVietnamDateTime(allocation.expiryDate), allocation.locationName, formatVietnamNumber(allocation.availableQuantity), formatVietnamNumber(allocation.allocatedQuantity), formatVietnamNumber(allocation.remainingAfterAllocation), allocation.blocked ? "Blocked" : allocation.warning ?? "OK"]));
    return <SimpleTable headers={["SKU","Batch code","Expiry date","Location","Available","Allocated","Remaining","Warning"]} rows={rows} />;
}

export function CostPanel({ order, canViewFull }: { order: ProductionOrder; canViewFull: boolean }) {
    if (!canViewFull) {
        return <div className="rounded-xl border border-[#e8eef0] bg-white p-4 text-sm font-bold text-[#697684]">Vai trò hiện tại chỉ xem summary, không xem chi tiết giá thành.</div>;
    }
    const cost = order.cost;
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 md:grid-cols-3">
        <Info label="Material cost" value={formatVnd(cost.materialCost)} />
        <Info label="Packaging cost" value={formatVnd(cost.packagingCost)} />
        <Info label="Direct production cost" value={formatVnd(cost.directProductionCost)} />
        <Info label="Allocated overhead" value={formatVnd(cost.allocatedOverhead)} />
        <Info label="Total production cost" value={formatVnd(cost.totalProductionCost)} />
        <Info label="Unit production cost" value={formatVnd(cost.unitProductionCost)} />
    </section>;
}

export function AuditTimeline({ order }: { order: ProductionOrder }) {
    return <ol className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4">{order.auditEvents.map((event)=><li className="grid grid-cols-[18px_1fr] gap-3" key={event.id}><span className="mt-1 size-2.5 rounded-full bg-[#159734]" /><div><strong className="text-sm font-black text-[#172317]">{event.type}</strong><p className="text-sm font-semibold text-[#536078]">{event.actor.name} · {formatVietnamDateTime(event.happenedAt)}</p>{event.note ? <p className="text-sm font-semibold text-[#697684]">{event.note}</p> : null}</div></li>)}</ol>;
}

export function PrioritySelect({ onChange, value }: { onChange: (value: ProductionPriority) => void; value: ProductionPriority }) {
    return <SelectFilter label="Priority" value={value} onChange={(next)=>onChange(next as ProductionPriority)} options={productionPriorityOptions.map((item)=>({label:productionPriorityLabels[item],value:item}))} />;
}

function SimpleTable({ headers, rows }: { headers: string[]; rows: Array<Array<ReactNode>> }) {
    return <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white"><table className="w-full min-w-[920px] text-left text-sm"><thead className="bg-[#f4faf5] text-xs font-black text-[#315f3c]"><tr>{headers.map((header)=><Th key={header}>{header}</Th>)}</tr></thead><tbody>{rows.map((row,index)=><tr key={index}>{row.map((cell,cellIndex)=><Td key={cellIndex}>{cell}</Td>)}</tr>)}</tbody></table></div>;
}

function TextFilter({ label, onChange, type = "text", value }: { label: string; onChange: (value: string) => void; type?: string; value: string }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317]" onChange={(event)=>onChange(event.currentTarget.value)} type={type} value={value}/></label>;
}

function SelectFilter({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: Array<{label:string; value:string}>; value: string }) {
    return <label className="grid gap-1.5 text-xs font-black text-[#536078]">{label}<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-semibold text-[#172317]" onChange={(event)=>onChange(event.currentTarget.value)} value={value}>{options.map((option)=><option key={option.value} value={option.value}>{option.label}</option>)}</select></label>;
}

function Th({ align = "left", children }: { align?: "left" | "right"; children: ReactNode }) {
    return <th className={`border-b border-[#dce8df] px-4 py-3 ${align === "right" ? "text-right" : "text-left"}`}>{children}</th>;
}

function Td({ align = "left", children, strong = false }: { align?: "left" | "right"; children: ReactNode; strong?: boolean }) {
    return <td className={`border-b border-[#edf0f4] px-4 py-3 ${align === "right" ? "text-right" : "text-left"} ${strong ? "font-black text-[#172317]" : "font-semibold text-[#536078]"}`}>{children}</td>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value}</strong></div>;
}

export { TextFilter, SelectFilter };
