import { Leaf, Search, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { Button, IconButton } from "../../../../components/ui/Button";
import { TextInput } from "../../../../components/ui/Field";
import { formatCurrency } from "../../services/money";
import { formatAvailabilityDate } from "../../services/posBackorder.api";
import {
    bundleLimits,
    posCustomBundleApi,
    type BundleAvailability,
    type BundleIngredientOption
} from "../../services/posCustomBundle.api";

export interface CustomBundleInput {
    bundleName: string;
    note: string;
    lines: Array<{ ingredientId: string; ingredientName: string; quantity: number }>;
}

interface CustomBundleBuilderProps {
    branchId: string | null;
    submitLoading: boolean;
    onCancel(): void;
    onSubmit(input: CustomBundleInput): void;
}

interface DraftLine {
    ingredient: BundleIngredientOption;
    quantity: string;
}

const DEFAULT_ADD_QUANTITY = 10;

function getErrorMessage(error: unknown) {
    return error instanceof Error ? error.message : "Có lỗi xảy ra, vui lòng thử lại.";
}

/** Khách tự chọn nguyên liệu và khối lượng; kho đóng gói sau khi thu tiền. */
export function CustomBundleBuilder({ branchId, submitLoading, onCancel, onSubmit }: CustomBundleBuilderProps) {
    const [search, setSearch] = useState("");
    const [options, setOptions] = useState<BundleIngredientOption[]>([]);
    const [optionsLoading, setOptionsLoading] = useState(false);
    const [optionsError, setOptionsError] = useState("");
    const [lines, setLines] = useState<DraftLine[]>([]);
    const [bundleName, setBundleName] = useState("");
    const [note, setNote] = useState("");
    const [validationError, setValidationError] = useState("");
    const [availability, setAvailability] = useState<BundleAvailability | null>(null);
    const [availabilityLoading, setAvailabilityLoading] = useState(false);

    useEffect(() => {
        let active = true;
        const timeoutId = window.setTimeout(() => {
            setOptionsLoading(true);
            setOptionsError("");
            posCustomBundleApi
                .searchIngredients(search)
                .then((result) => {
                    if (active) setOptions(result);
                })
                .catch((error: unknown) => {
                    if (active) setOptionsError(getErrorMessage(error));
                })
                .finally(() => {
                    if (active) setOptionsLoading(false);
                });
        }, 250);
        return () => {
            active = false;
            window.clearTimeout(timeoutId);
        };
    }, [search]);

    const parsedLines = useMemo(
        () => lines.map((line) => ({ ...line, amount: Number(line.quantity) })),
        [lines]
    );
    const totalQuantity = parsedLines.reduce(
        (sum, line) => sum + (Number.isFinite(line.amount) ? line.amount : 0),
        0
    );
    const estimatedPrice = parsedLines.reduce(
        (sum, line) => sum + (Number.isFinite(line.amount) ? line.amount * line.ingredient.retailPricePerUnit : 0),
        0
    );

    function addIngredient(ingredient: BundleIngredientOption) {
        setAvailability(null);
        setValidationError("");
        setLines((current) => {
            const existing = current.find((line) => line.ingredient.id === ingredient.id);
            if (existing) {
                return current.map((line) =>
                    line.ingredient.id === ingredient.id
                        ? { ...line, quantity: String(Number(line.quantity || 0) + DEFAULT_ADD_QUANTITY) }
                        : line
                );
            }
            if (current.length >= bundleLimits.maxIngredients) {
                setValidationError(`Bundle tối đa ${bundleLimits.maxIngredients} nguyên liệu.`);
                return current;
            }
            return [...current, { ingredient, quantity: String(DEFAULT_ADD_QUANTITY) }];
        });
    }

    function changeQuantity(ingredientId: string, value: string) {
        setAvailability(null);
        setLines((current) =>
            current.map((line) => (line.ingredient.id === ingredientId ? { ...line, quantity: value } : line))
        );
    }

    function removeLine(ingredientId: string) {
        setAvailability(null);
        setLines((current) => current.filter((line) => line.ingredient.id !== ingredientId));
    }

    function validate() {
        if (parsedLines.length === 0) return "Bundle phải có ít nhất một nguyên liệu.";
        const invalid = parsedLines.find(
            (line) => !Number.isFinite(line.amount) || line.amount < bundleLimits.minLineQuantity
        );
        if (invalid) {
            return `${invalid.ingredient.name} tối thiểu ${bundleLimits.minLineQuantity}${invalid.ingredient.unitSymbol ?? ""}.`;
        }
        if (totalQuantity < bundleLimits.minTotalQuantity || totalQuantity > bundleLimits.maxTotalQuantity) {
            return `Tổng khối lượng phải từ ${bundleLimits.minTotalQuantity} đến ${bundleLimits.maxTotalQuantity}.`;
        }
        return "";
    }

    function requestLines() {
        return parsedLines.map((line) => ({
            ingredientId: line.ingredient.id,
            ingredientName: line.ingredient.name,
            quantity: line.amount
        }));
    }

    async function checkAvailability() {
        const error = validate();
        setValidationError(error);
        if (error) return;
        setAvailabilityLoading(true);
        try {
            setAvailability(await posCustomBundleApi.checkAvailability({ branchId, lines: requestLines() }));
        } catch (checkError) {
            setValidationError(`Không kiểm tra được tồn nguyên liệu: ${getErrorMessage(checkError)}`);
        } finally {
            setAvailabilityLoading(false);
        }
    }

    function submit() {
        const error = validate();
        setValidationError(error);
        if (error) return;
        onSubmit({ bundleName: bundleName.trim(), note: note.trim(), lines: requestLines() });
    }

    return (
        <section className="grid min-h-0 grid-rows-[minmax(0,1fr)_auto] overflow-hidden rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
            <div className="grid min-h-0 grid-cols-[minmax(0,1fr)_380px] gap-4 overflow-hidden p-4">
                <div className="grid min-h-0 grid-rows-[auto_minmax(0,1fr)] gap-3">
                    <div className="relative">
                        <Search aria-hidden="true" className="absolute left-3 top-3 text-[#697684]" size={16} />
                        <input
                            className="h-11 w-full rounded-lg border border-pos-border pl-9 pr-3 text-sm font-bold outline-none focus:border-[#159734]"
                            onChange={(event) => setSearch(event.currentTarget.value)}
                            placeholder="Tìm nguyên liệu theo tên hoặc mã"
                            value={search}
                        />
                    </div>
                    <div className="grid min-h-0 auto-rows-min grid-cols-2 gap-2 overflow-y-auto rounded-xl border border-[#e8eef0] bg-[#f8fbf8] p-2">
                        {optionsLoading ? (
                            <p className="col-span-2 px-3 py-4 text-sm font-bold text-[#697684]">Đang tải nguyên liệu...</p>
                        ) : null}
                        {optionsError ? (
                            <p className="col-span-2 px-3 py-4 text-sm font-bold text-red-700">{optionsError}</p>
                        ) : null}
                        {!optionsLoading && !optionsError && options.length === 0 ? (
                            <p className="col-span-2 px-3 py-4 text-sm font-bold text-[#697684]">
                                Chưa có nguyên liệu bán lẻ. Cần nhập giá bán lẻ ở màn Nguyên liệu.
                            </p>
                        ) : null}
                        {options.map((ingredient) => (
                            <button
                                className="grid gap-1 rounded-lg border border-transparent bg-white px-3 py-2 text-left hover:border-[#159734]"
                                key={ingredient.id}
                                onClick={() => addIngredient(ingredient)}
                                type="button"
                            >
                                <span className="flex items-center gap-2 font-black text-[#172317]">
                                    <Leaf aria-hidden="true" className="text-[#159734]" size={15} />
                                    {ingredient.name}
                                </span>
                                <span className="text-xs font-bold text-[#697684]">
                                    {ingredient.ingredientCode} · {formatCurrency(ingredient.retailPricePerUnit)} /{" "}
                                    {ingredient.unitSymbol ?? "đơn vị"}
                                </span>
                            </button>
                        ))}
                    </div>
                </div>

                <aside className="grid min-h-0 grid-rows-[minmax(0,1fr)_auto] gap-3">
                    <div className="grid min-h-0 auto-rows-min gap-2 overflow-y-auto">
                        {parsedLines.length === 0 ? (
                            <p className="rounded-lg border border-dashed border-[#cfdad2] px-3 py-6 text-center text-sm font-bold text-[#697684]">
                                Chọn nguyên liệu ở bên trái.
                            </p>
                        ) : null}
                        {parsedLines.map((line) => {
                            const material = availability?.lines.find(
                                (item) => item.ingredientId === line.ingredient.id
                            );
                            return (
                                <div className="grid gap-2 rounded-lg border border-[#e8eef0] p-3" key={line.ingredient.id}>
                                    <div className="flex items-start justify-between gap-2">
                                        <strong className="text-sm text-[#172317]">{line.ingredient.name}</strong>
                                        <IconButton
                                            aria-label={`Bỏ ${line.ingredient.name}`}
                                            onClick={() => removeLine(line.ingredient.id)}
                                            size="xs"
                                            tone="danger"
                                        >
                                            <Trash2 size={16} aria-hidden="true" />
                                        </IconButton>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <input
                                            aria-label={`Khối lượng ${line.ingredient.name}`}
                                            className="h-9 w-28 rounded-lg border border-pos-border px-3 text-sm font-bold"
                                            min={bundleLimits.minLineQuantity}
                                            onChange={(event) => changeQuantity(line.ingredient.id, event.currentTarget.value)}
                                            type="number"
                                            value={line.quantity}
                                        />
                                        <span className="text-sm font-bold text-[#536078]">{line.ingredient.unitSymbol ?? ""}</span>
                                        <span className="ml-auto text-sm font-black text-[#172317]">
                                            {formatCurrency(
                                                Number.isFinite(line.amount) ? line.amount * line.ingredient.retailPricePerUnit : 0
                                            )}
                                        </span>
                                    </div>
                                    {material && material.missingQuantity > 0 ? (
                                        <span className="text-xs font-black text-amber-700">
                                            Thiếu {material.missingQuantity}
                                            {line.ingredient.unitSymbol ?? ""} · dự kiến có{" "}
                                            {formatAvailabilityDate(material.expectedArrivalAt)}
                                        </span>
                                    ) : null}
                                </div>
                            );
                        })}
                    </div>

                    <div className="grid gap-2">
                        <TextInput
                            maxLength={255}
                            onChange={(event) => setBundleName(event.currentTarget.value)}
                            placeholder="Tên bundle (tuỳ chọn)"
                            value={bundleName}
                        />
                        <TextInput
                            maxLength={1000}
                            onChange={(event) => setNote(event.currentTarget.value)}
                            placeholder="Ghi chú đóng gói (tuỳ chọn)"
                            value={note}
                        />
                        <div className="grid gap-1 rounded-lg bg-[#f7f8fb] p-3 text-sm font-bold text-[#536078]">
                            <span>Tổng khối lượng: {totalQuantity}</span>
                            <span>Giá dự kiến: {formatCurrency(estimatedPrice)}</span>
                            {availability ? (
                                availability.enough ? (
                                    <span className="text-[#159734]">Đủ nguyên liệu, đóng gói ngay sau khi thu tiền.</span>
                                ) : (
                                    <span className="text-amber-700">
                                        Thiếu nguyên liệu, dự kiến có {formatAvailabilityDate(availability.estimatedAvailableAt)}.
                                        Khách có thể đặt cọc và nhận sau.
                                    </span>
                                )
                            ) : null}
                        </div>
                        {validationError ? (
                            <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700">
                                {validationError}
                            </p>
                        ) : null}
                    </div>
                </aside>
            </div>

            <footer className="flex justify-end gap-3 border-t border-[#e8eef0] px-5 py-4">
                <Button disabled={submitLoading} onClick={onCancel}>
                    Hủy
                </Button>
                <Button disabled={submitLoading || availabilityLoading} onClick={() => void checkAvailability()}>
                    {availabilityLoading ? "Đang kiểm tra..." : "Kiểm tra tồn"}
                </Button>
                <Button disabled={submitLoading} onClick={submit} variant="primary">
                    {submitLoading ? "Đang thêm..." : "Thêm vào đơn"}
                </Button>
            </footer>
        </section>
    );
}
