import { useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";
import { ArrowLeft, CheckCircle2, ShieldAlert } from "lucide-react";

import { InspectionStatusBadge } from "../components/CustomerReturnInspectionComponents";
import { customerReturnApi } from "../services/customerReturn.api";
import { inventoryLocationApi } from "../services/inventoryLocation.api";
import type { CustomerReturnInspection, CustomerReturnQcResult } from "../types/customer-return-inspection.types";
import type { InventoryLocationResponse } from "../types/inventory.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

const resultLabels: Record<CustomerReturnQcResult, string> = {
    RESTOCK: "Nhập lại kho",
    DAMAGED: "Hàng hỏng",
    QUARANTINE: "Cách ly",
    WRITE_OFF: "Tiêu hủy"
};

const conditionLabels: Record<string, string> = {
    SEALED: "Nguyên seal",
    OPENED: "Đã mở hộp",
    DAMAGED: "Hư hỏng",
    DEFECTIVE: "Lỗi kỹ thuật",
    EXPIRED: "Hết hạn"
};

export function CustomerReturnDetailPage() {
    const { returnId: inspectionId } = useParams();
    const [detail, setDetail] = useState<CustomerReturnInspection | null>(null);
    const [locations, setLocations] = useState<InventoryLocationResponse[]>([]);
    const [selectedLocations, setSelectedLocations] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [successMessage, setSuccessMessage] = useState("");

    useEffect(() => {
        if (!inspectionId) return;
        let active = true;
        const timerId = window.setTimeout(() => {
            setLoading(true);
            setError("");

            void customerReturnApi.getInspection(inspectionId)
                .then(async (response) => {
                    if (!active) return;
                    setDetail(response);

                    // Fetch active locations for the branch
                    try {
                        const locRes = await inventoryLocationApi.list({
                            branchId: response.branchId,
                            status: "ACTIVE"
                        });
                        if (active && locRes?.content) {
                            setLocations(locRes.content);
                        }
                    } catch {
                        // non-fatal, proceed with existing locations
                    }
                })
                .catch((loadError: unknown) => {
                    if (active) {
                        setError(loadError instanceof Error ? loadError.message : "Không thể tải phiếu kiểm định.");
                    }
                })
                .finally(() => {
                    if (active) setLoading(false);
                });
        }, 0);

        return () => {
            active = false;
            window.clearTimeout(timerId);
        };
    }, [inspectionId]);

    if (!inspectionId) {
        return <Navigate replace to="/management/inventory/customer-returns" />;
    }

    function handleLocationChange(lineId: string, locationId: string) {
        setSelectedLocations((prev) => ({
            ...prev,
            [lineId]: locationId
        }));
    }

    function filterLocationsForResult(result: CustomerReturnQcResult) {
        if (result === "RESTOCK") {
            return locations.filter((loc) => loc.type !== "DAMAGED" && loc.type !== "QUARANTINE");
        }
        if (result === "DAMAGED") {
            return locations.filter((loc) => loc.type === "DAMAGED");
        }
        if (result === "QUARANTINE") {
            return locations.filter((loc) => loc.type === "QUARANTINE");
        }
        return [];
    }

    async function handlePostInspection() {
        if (!detail || detail.status !== "CREATED") return;
        setSubmitting(true);
        setError("");
        setSuccessMessage("");

        const lineOverrides = detail.lines.map((line) => ({
            lineId: line.id,
            returnItemId: line.returnItemId,
            destinationLocationId: selectedLocations[line.id] || line.destinationLocationId || undefined
        }));

        try {
            const updated = await customerReturnApi.postInspection(detail.id, { lines: lineOverrides });
            setDetail(updated);
            setSuccessMessage(updated.status === "PROCESSING"
                ? "Đã hạch toán các dòng đủ điều kiện. Dòng tiêu hủy đang chờ phê duyệt và chưa được ghi nhận hoàn tất vật lý."
                : "Xác nhận xử lý thành công. Hàng trả đã được hạch toán theo kết quả kiểm định.");
        } catch (postError) {
            setError(postError instanceof Error ? postError.message : "Không thể xử lý phiếu kiểm định.");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <Link
                        className="inline-flex items-center gap-1.5 text-sm font-black text-[#159734] hover:underline"
                        to="/management/inventory/customer-returns"
                    >
                        <ArrowLeft size={16} /> Quay lại danh sách
                    </Link>
                    <h1 className="mt-1 text-2xl font-black text-[#172317]">
                        {detail?.inspectionCode ?? "Phiếu kiểm định trả hàng"}
                    </h1>
                    {detail ? (
                        <p className="text-sm font-semibold text-[#697684]">
                            Mã phiếu hoàn: <span className="font-bold text-[#172317]">{detail.returnCode}</span> · Mã đơn hàng: <span className="font-bold text-[#172317]">{detail.orderCode ?? "Không có mã đơn hàng"}</span>
                        </p>
                    ) : null}
                </div>
                {detail?.status === "CREATED" ? (
                    <button
                        className="h-10 rounded-lg bg-[#159734] px-5 text-sm font-black text-white shadow-sm transition hover:bg-[#12822c] disabled:opacity-60"
                        disabled={submitting}
                        onClick={() => void handlePostInspection()}
                        type="button"
                    >
                        {submitting ? "Đang xử lý..." : "Xác nhận xử lý"}
                    </button>
                ) : null}
            </header>

            {loading ? (
                <div className="rounded-xl bg-white p-6 text-sm font-bold text-[#697684]">Đang tải dữ liệu phiếu...</div>
            ) : null}

            {error ? (
                <div className="flex items-center gap-2 rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">
                    <ShieldAlert size={18} /> {error}
                </div>
            ) : null}

            {successMessage ? (
                <div className="flex items-center gap-2 rounded-xl bg-green-50 p-4 text-sm font-bold text-green-800">
                    <CheckCircle2 size={18} /> {successMessage}
                </div>
            ) : null}

            {detail ? (
                <>
                    <section className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card md:grid-cols-3 lg:grid-cols-5">
                        <div>
                            <span className="text-xs font-black uppercase text-[#697684]">Trạng thái phiếu</span>
                            <div className="mt-1">
                                <InspectionStatusBadge status={detail.status} />
                            </div>
                        </div>
                        <Info label="Khách hàng" value={detail.customerName || "Khách lẻ"} />
                        <Info label="Chi nhánh tiếp nhận" value="Kho Chi nhánh chính" />
                        <Info label="Thời gian tạo phiếu" value={formatVietnamDateTime(detail.createdAt)} />
                        <Info
                            label="Thời gian xử lý kho"
                            value={detail.postedAt ? formatVietnamDateTime(detail.postedAt) : "Chờ xử lý"}
                        />
                    </section>

                    <section className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                        <table className="w-full min-w-[980px] text-left text-sm">
                            <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]">
                                <tr>
                                    <th className="px-4 py-3">Sản phẩm</th>
                                    <th>Số lượng trả</th>
                                    <th>Tình trạng hàng</th>
                                    <th>Lý do trả</th>
                                    <th>Phân loại xử lý</th>
                                    <th>Vị trí đích</th>
                                    <th>Giao dịch kho</th>
                                </tr>
                            </thead>
                            <tbody>
                                {detail.lines.map((line) => {
                                    const eligibleLocations = filterLocationsForResult(line.result);
                                    const selectedLocId = selectedLocations[line.id] ?? line.destinationLocationId ?? "";

                                    return (
                                        <tr className="hover:bg-[#f8fbf8]" key={line.id}>
                                            <td className="border-t border-[#edf0f4] px-4 py-3">
                                                <strong className="text-[#172317]">{line.itemName ?? "Sản phẩm"}</strong>
                                                <p className="text-xs text-[#697684]">{line.itemCode ?? "Chưa có SKU"}</p>
                                            </td>
                                            <td className="border-t border-[#edf0f4] font-bold">
                                                {formatVietnamNumber(Number(line.quantity))}
                                            </td>
                                            <td className="border-t border-[#edf0f4]">
                                                <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-bold text-slate-700">
                                                    {conditionLabels[line.condition ?? ""] ?? line.condition ?? "Bình thường"}
                                                </span>
                                            </td>
                                            <td className="border-t border-[#edf0f4] text-xs font-semibold text-[#536078]">
                                                {line.returnReason || "Khách yêu cầu trả"}
                                            </td>
                                            <td className="border-t border-[#edf0f4]">
                                                <span
                                                    className={`inline-flex rounded-md px-2.5 py-1 text-xs font-black ${
                                                        line.result === "RESTOCK"
                                                            ? "bg-green-100 text-green-800"
                                                            : line.result === "DAMAGED"
                                                            ? "bg-red-100 text-red-800"
                                                            : line.result === "QUARANTINE"
                                                            ? "bg-amber-100 text-amber-800"
                                                            : "bg-slate-200 text-slate-800"
                                                    }`}
                                                >
                                                    {resultLabels[line.result] ?? line.result}
                                                </span>
                                            </td>
                                            <td className="border-t border-[#edf0f4]">
                                                {line.result === "WRITE_OFF" ? (
                                                    <span className="text-xs font-semibold text-[#697684]">Không áp dụng</span>
                                                ) : detail.status === "CREATED" && eligibleLocations.length > 0 ? (
                                                    <select
                                                        className="h-8 rounded border border-pos-border px-2 text-xs font-bold text-[#172317]"
                                                        onChange={(e) => handleLocationChange(line.id, e.target.value)}
                                                        value={selectedLocId}
                                                    >
                                                        <option value="">-- Chọn vị trí --</option>
                                                        {eligibleLocations.map((loc) => (
                                                            <option key={loc.id} value={loc.id}>
                                                                {loc.name} ({loc.code})
                                                            </option>
                                                        ))}
                                                    </select>
                                                ) : (
                                                    <span className="text-xs font-bold text-[#172317]">
                                                        {line.destinationLocationName ?? "Chưa xác định"}
                                                    </span>
                                                )}
                                            </td>
                                            <td className="border-t border-[#edf0f4]">
                                                {line.disposalStatus === "PENDING_APPROVAL" && line.disposalRequestId ? (
                                                    <div className="grid gap-1">
                                                        <span className="inline-flex w-fit rounded bg-amber-50 px-2 py-0.5 text-xs font-bold text-amber-700">
                                                            Chờ duyệt tiêu hủy
                                                        </span>
                                                        <Link
                                                            className="text-xs font-black text-[#159734] hover:underline"
                                                            to={`/management/inventory/disposals/${line.disposalRequestId}`}
                                                        >
                                                            {line.disposalRequestCode ?? "Xem yêu cầu tiêu hủy"}
                                                        </Link>
                                                    </div>
                                                ) : line.posted ? (
                                                    <div className="grid gap-1">
                                                        <span className="inline-flex w-fit items-center gap-1 rounded bg-green-50 px-2.5 py-1 text-xs font-bold text-green-700">
                                                            <CheckCircle2 size={13} /> Đã xử lý
                                                        </span>
                                                        <span className="text-xs font-bold text-[#172317]">
                                                            {line.transactionType ?? (line.result === "WRITE_OFF" ? "WRITE_OFF" : "CUSTOMER_RETURN_RECEIPT")}
                                                        </span>
                                                        {line.disposalRequestId ? (
                                                            <Link
                                                                className="text-[11px] font-bold text-[#159734] hover:underline"
                                                                to={`/management/inventory/disposals/${line.disposalRequestId}`}
                                                            >
                                                                {line.disposalRequestCode ?? "Xem phiếu tiêu hủy"}
                                                            </Link>
                                                        ) : null}
                                                    </div>
                                                ) : (
                                                    <span className="inline-flex rounded bg-amber-50 px-2 py-0.5 text-xs font-bold text-amber-700">
                                                        Chưa tạo
                                                    </span>
                                                )}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </section>

                    {detail.status === "POSTED" ? (
                        <div className="rounded-xl border border-green-200 bg-green-50 p-4 text-sm font-semibold text-green-900">
                            Phiếu kiểm định đã ở trạng thái <strong>POSTED</strong>. Giao dịch kho và cập nhật tồn kho đã được hạch toán hoàn tất. Thao tác xác nhận không còn hiển thị để bảo đảm tính idempotent, tuyệt đối không ghi nhận hai lần.
                        </div>
                    ) : null}
                    {detail.status === "PROCESSING" ? (
                        <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm font-semibold text-amber-900">
                            Các dòng nhập lại kho/hàng hỏng/cách ly đã được xử lý. Phiếu chỉ hoàn tất sau khi yêu cầu tiêu hủy được phê duyệt; hiện chưa gửi xác nhận hoàn tất về Order-Service.
                        </div>
                    ) : null}
                </>
            ) : null}
        </section>
    );
}

function Info({ label, value }: { label: string; value: string }) {
    return (
        <div>
            <span className="text-xs font-black uppercase text-[#697684]">{label}</span>
            <p className="mt-1 text-sm font-black text-[#172317]">{value}</p>
        </div>
    );
}
