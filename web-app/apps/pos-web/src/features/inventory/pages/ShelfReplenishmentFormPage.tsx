import { ArrowLeft, Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { routePaths } from "../../../routes/path";
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import { useLookupDropdown } from "../../manufacturing/components/shared/useLookupDropdown";
import { productVariantApi } from "../../product/services/productVariant.api";
import { InventoryPageHeader } from "../components/InventoryManagementComponents";
import { shelfReplenishmentApi } from "../services/shelfReplenishment.api";
import { storeShelfApi } from "../services/storeShelf.api";
import type { InventoryLocationResponse } from "../types/inventory.types";
import type { ShelfReplenishmentLineInput } from "../types/shelfReplenishment.types";
import { shelfReplenishmentDetailPath } from "../utils/shelfReplenishment.utils";

interface FormLine {
    key: number;
    productVariantId: string;
    name: string;
    quantity: string;
}

const inputClass =
    "h-10 w-full rounded-lg border border-pos-border bg-white px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734] disabled:bg-neutral-50";
const labelClass = "grid gap-1.5 text-[13px] font-[900] text-[#536078]";

let nextLineKey = 1;

function emptyLine(): FormLine {
    return { key: nextLineKey++, productVariantId: "", name: "", quantity: "1" };
}

function errorMessage(error: unknown, fallback: string) {
    return error instanceof Error && error.message ? error.message : fallback;
}

/** Tạo phiếu nhập kệ mới, hoặc sửa phiếu đang ở trạng thái nháp khi có `slipId` trên URL. */
export function ShelfReplenishmentFormPage() {
    const { slipId } = useParams<{ slipId: string }>();
    const navigate = useNavigate();
    const isEdit = Boolean(slipId);

    const [shelves, setShelves] = useState<InventoryLocationResponse[]>([]);
    const [shelfId, setShelfId] = useState("");
    const [note, setNote] = useState("");
    const [lines, setLines] = useState<FormLine[]>(() => [emptyLine()]);
    const [loading, setLoading] = useState(isEdit);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    const variantDropdown = useLookupDropdown(productVariantApi.lookup, {
        pinned: lines
            .filter((line) => line.productVariantId && line.name)
            .map((line) => ({ id: line.productVariantId, name: line.name }))
    });

    useEffect(() => {
        let active = true;
        storeShelfApi
            .list({ status: "ACTIVE", size: 200 })
            .then((page) => {
                if (active) setShelves(page.content ?? []);
            })
            .catch((loadError) => console.error("Error loading shelves", loadError));
        return () => {
            active = false;
        };
    }, []);

    useEffect(() => {
        if (!slipId) return undefined;
        let active = true;
        shelfReplenishmentApi
            .detail(slipId)
            .then((slip) => {
                if (!active) return;
                if (slip.status !== "DRAFT") {
                    navigate(shelfReplenishmentDetailPath(slip.id), { replace: true });
                    return;
                }
                setShelfId(slip.shelfId ?? "");
                setNote(slip.note ?? "");
                setLines(
                    slip.lines.length > 0
                        ? slip.lines.map((line) => ({
                              key: nextLineKey++,
                              productVariantId: line.productVariantId,
                              name: line.itemName,
                              quantity: String(line.quantity)
                          }))
                        : [emptyLine()]
                );
            })
            .catch((loadError) => {
                if (active) setError(errorMessage(loadError, "Không tải được phiếu nhập kệ."));
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [navigate, slipId]);

    function updateLine(key: number, patch: Partial<FormLine>) {
        setLines((current) => current.map((line) => (line.key === key ? { ...line, ...patch } : line)));
    }

    function buildLines(): ShelfReplenishmentLineInput[] | null {
        const filled = lines.filter((line) => line.productVariantId);
        if (filled.length === 0) {
            setError("Vui lòng chọn ít nhất một mặt hàng.");
            return null;
        }
        const seen = new Set<string>();
        for (const [index, line] of filled.entries()) {
            const quantity = Number(line.quantity);
            if (!Number.isFinite(quantity) || quantity <= 0) {
                setError(`Dòng ${index + 1}: số lượng phải lớn hơn 0.`);
                return null;
            }
            if (seen.has(line.productVariantId)) {
                setError("Không được chọn trùng mặt hàng trong cùng một phiếu.");
                return null;
            }
            seen.add(line.productVariantId);
        }
        return filled.map((line) => ({
            productVariantId: line.productVariantId,
            quantity: Number(line.quantity)
        }));
    }

    async function save(submit: boolean) {
        if (submitting) return;
        const payloadLines = buildLines();
        if (!payloadLines) return;

        setError("");
        setSubmitting(true);
        try {
            let slip;
            if (slipId) {
                slip = await shelfReplenishmentApi.update(slipId, {
                    shelfId: shelfId || null,
                    note: note.trim() || null,
                    lines: payloadLines
                });
                if (submit) {
                    slip = await shelfReplenishmentApi.submit(slipId);
                }
            } else {
                slip = await shelfReplenishmentApi.create({
                    shelfId: shelfId || null,
                    note: note.trim() || null,
                    submit,
                    lines: payloadLines
                });
            }
            navigate(shelfReplenishmentDetailPath(slip.id));
        } catch (saveError) {
            setError(errorMessage(saveError, "Không lưu được phiếu nhập kệ."));
        } finally {
            setSubmitting(false);
        }
    }

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        void save(true);
    }

    const disabled = loading || submitting;

    return (
        <section className="grid gap-5">
            <InventoryPageHeader
                actions={
                    <button
                        className="inline-flex h-10 items-center gap-2 rounded-lg border border-pos-border bg-white px-4 text-sm font-black text-[#536078] hover:bg-neutral-50"
                        onClick={() => navigate(routePaths.shelfReplenishments)}
                        type="button"
                    >
                        <ArrowLeft aria-hidden="true" size={16} />
                        Danh sách phiếu
                    </button>
                }
                description="Chọn kệ cần bổ sung và các mặt hàng lấy từ kho. Lưu nháp để điền sau, hoặc gửi kho xử lý ngay."
                eyebrow="Kho / Phiếu nhập kệ"
                title={isEdit ? "Sửa phiếu nhập kệ" : "Tạo phiếu nhập kệ"}
            />

            <form
                className="grid gap-5 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card"
                noValidate
                onSubmit={handleSubmit}
            >
                {error ? (
                    <p
                        className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700"
                        role="alert"
                    >
                        {error}
                    </p>
                ) : null}

                <div className="grid gap-4 md:grid-cols-2">
                    <label className={labelClass}>
                        Kệ nhận hàng
                        <select
                            className={inputClass}
                            disabled={disabled}
                            onChange={(event) => setShelfId(event.target.value)}
                            value={shelfId}
                        >
                            <option value="">Tự chọn kệ đang bày mặt hàng</option>
                            {shelves.map((shelf) => (
                                <option key={shelf.id} value={shelf.id}>
                                    {shelf.name} ({shelf.code})
                                </option>
                            ))}
                        </select>
                    </label>
                    <label className={labelClass}>
                        Ghi chú
                        <input
                            className={inputClass}
                            disabled={disabled}
                            maxLength={500}
                            onChange={(event) => setNote(event.target.value)}
                            placeholder="Ví dụ: bổ sung kệ trước giờ cao điểm"
                            type="text"
                            value={note}
                        />
                    </label>
                </div>

                <div className="grid gap-3">
                    <div className="flex items-center justify-between">
                        <h2 className="text-base font-black text-[#172317]">Mặt hàng cần đưa lên kệ</h2>
                        <button
                            className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-[#07883a] bg-white px-3.5 text-xs font-bold text-[#07883a] hover:bg-green-50 disabled:opacity-60"
                            disabled={disabled}
                            onClick={() => setLines((current) => [...current, emptyLine()])}
                            type="button"
                        >
                            <Plus aria-hidden="true" size={14} />
                            Thêm dòng
                        </button>
                    </div>

                    <div className="overflow-x-auto rounded-xl border border-pos-border">
                        <table className="w-full min-w-[560px] text-left text-sm">
                            <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
                                <tr>
                                    <th className="px-3 py-2.5" scope="col">Mặt hàng</th>
                                    <th className="w-40 px-3 py-2.5" scope="col">Số lượng</th>
                                    <th className="w-16 px-3 py-2.5 text-center" scope="col">Xóa</th>
                                </tr>
                            </thead>
                            <tbody>
                                {lines.map((line) => (
                                    <tr className="border-t border-pos-border" key={line.key}>
                                        <td className="p-2">
                                            <SearchableDropdown
                                                disabled={disabled}
                                                emptyText="Không tìm thấy mặt hàng"
                                                hasMore={variantDropdown.hasMore}
                                                loading={variantDropdown.loading}
                                                onChange={(value) =>
                                                    updateLine(line.key, {
                                                        productVariantId: value,
                                                        name: variantDropdown.findOption(value)?.name ?? ""
                                                    })
                                                }
                                                onLoadMore={variantDropdown.onLoadMore}
                                                onSearch={variantDropdown.onSearch}
                                                options={variantDropdown.options}
                                                placeholder="-- Chọn mặt hàng --"
                                                value={line.productVariantId}
                                            />
                                        </td>
                                        <td className="p-2">
                                            <input
                                                aria-label="Số lượng"
                                                className={inputClass}
                                                disabled={disabled}
                                                min={0.01}
                                                onChange={(event) =>
                                                    updateLine(line.key, { quantity: event.target.value })
                                                }
                                                step="0.01"
                                                type="number"
                                                value={line.quantity}
                                            />
                                        </td>
                                        <td className="p-2 text-center">
                                            <button
                                                aria-label="Xóa dòng"
                                                className="mx-auto grid size-8 place-items-center rounded-lg text-red-500 hover:bg-red-50 disabled:opacity-40"
                                                disabled={disabled || lines.length === 1}
                                                onClick={() =>
                                                    setLines((current) =>
                                                        current.filter((candidate) => candidate.key !== line.key)
                                                    )
                                                }
                                                type="button"
                                            >
                                                <Trash2 aria-hidden="true" size={16} />
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>

                <footer className="flex flex-wrap justify-end gap-3 border-t border-pos-border pt-4">
                    <button
                        className="h-11 rounded-xl border border-pos-border bg-white px-6 text-sm font-black text-[#536078] hover:bg-neutral-50 disabled:opacity-60"
                        disabled={disabled}
                        onClick={() => void save(false)}
                        type="button"
                    >
                        Lưu nháp
                    </button>
                    <button
                        className="h-11 rounded-xl bg-[#07883a] px-6 text-sm font-black text-white hover:bg-[#067331] disabled:opacity-60"
                        disabled={disabled}
                        type="submit"
                    >
                        {submitting ? "Đang lưu..." : "Lưu và gửi kho"}
                    </button>
                </footer>
            </form>
        </section>
    );
}
