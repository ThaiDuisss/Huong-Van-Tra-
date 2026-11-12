import { ArrowLeft, Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { Button, IconButton } from "../../../components/ui/Button";
import { Field, NativeSelect, TextInput } from "../../../components/ui/Field";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
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
                    <Button onClick={() => navigate(routePaths.shelfReplenishments)}>
                        <ArrowLeft aria-hidden="true" size={16} />
                        Danh sách phiếu
                    </Button>
                }
                description="Chọn kệ cần bổ sung và các mặt hàng lấy từ kho. Lưu nháp để điền sau, hoặc gửi ngay để Quản lý duyệt."
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
                    <Field label="Kệ nhận hàng">
                        <NativeSelect
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
                        </NativeSelect>
                    </Field>
                    <Field label="Ghi chú">
                        <TextInput
                            disabled={disabled}
                            maxLength={500}
                            onChange={(event) => setNote(event.target.value)}
                            placeholder="Ví dụ: bổ sung kệ trước giờ cao điểm"
                            type="text"
                            value={note}
                        />
                    </Field>
                </div>

                <div className="grid gap-3">
                    <div className="flex items-center justify-between">
                        <h2 className="text-base font-black text-[#172317]">Mặt hàng cần đưa lên kệ</h2>
                        <Button
                            disabled={disabled}
                            onClick={() => setLines((current) => [...current, emptyLine()])}
                            size="sm"
                        >
                            <Plus aria-hidden="true" size={14} />
                            Thêm dòng
                        </Button>
                    </div>

                    <div className="overflow-x-auto rounded-xl border border-pos-border">
                        <Table className="min-w-[560px]">
                            <TableHead>
                                <TableRow>
                                    <TableHeaderCell>Mặt hàng</TableHeaderCell>
                                    <TableHeaderCell className="w-40">Số lượng</TableHeaderCell>
                                    <TableHeaderCell className="w-16 text-center">Xóa</TableHeaderCell>
                                </TableRow>
                            </TableHead>
                            <tbody>
                                {lines.map((line) => (
                                    <TableRow key={line.key}>
                                        <TableCell>
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
                                        </TableCell>
                                        <TableCell>
                                            <TextInput
                                                aria-label="Số lượng"
                                                disabled={disabled}
                                                min={0.01}
                                                onChange={(event) =>
                                                    updateLine(line.key, { quantity: event.target.value })
                                                }
                                                step="0.01"
                                                type="number"
                                                value={line.quantity}
                                            />
                                        </TableCell>
                                        <TableCell className="text-center">
                                            <IconButton
                                                aria-label="Xóa dòng"
                                                disabled={disabled || lines.length === 1}
                                                onClick={() =>
                                                    setLines((current) =>
                                                        current.filter((candidate) => candidate.key !== line.key)
                                                    )
                                                }
                                                tone="danger"
                                            >
                                                <Trash2 aria-hidden="true" size={16} />
                                            </IconButton>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </tbody>
                        </Table>
                    </div>
                </div>

                <footer className="flex flex-wrap justify-end gap-3 border-t border-pos-border pt-4">
                    <Button disabled={disabled} onClick={() => void save(false)}>
                        Lưu nháp
                    </Button>
                    <Button disabled={disabled} type="submit" variant="primary">
                        {submitting ? "Đang lưu..." : "Lưu và gửi duyệt"}
                    </Button>
                </footer>
            </form>
        </section>
    );
}
