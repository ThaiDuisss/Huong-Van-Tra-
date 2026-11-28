import { Leaf, Search, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { Button, IconButton } from "../../../../components/ui/Button";
import { TextInput } from "../../../../components/ui/Field";
import { IngredientRecommendationPanel } from "../../../product-recommendation/components/IngredientRecommendationPanel";
import { useIngredientRecommendations } from "../../../product-recommendation/hooks/useIngredientRecommendations";
import type { RecommendedIngredient } from "../../../product-recommendation/types/ingredientRecommendation.types";
import { formatCurrency } from "../../services/money";
import { formatAvailabilityDate } from "../../services/posBackorder.api";
import {
    bundleLimits,
    defaultBundleQuantity,
    posCustomBundleApi,
    type BundleAvailability,
    type BundleIngredientOption
} from "../../services/posCustomBundle.api";
import {
    addFlexibleWeightStep,
    defaultFlexibleWeightText,
    formatKgPriceMeta,
    isWeightUnit,
    parseFlexibleWeightInput
} from "./flexibleWeight";

export type CustomBundleBuilderMode = "MANUAL" | "AI_RECOMMENDATION";

export interface CustomBundleInput {
    bundleName: string;
    note: string;
    lines: Array<{ ingredientId: string; ingredientName: string; quantity: number }>;
}

interface CustomBundleBuilderProps {
    branchId: string | null;
    customerId?: string | null;
    mode?: CustomBundleBuilderMode;
    showAiSuggestions?: boolean;
    submitLoading: boolean;
    submitError?: string;
    onCancel(): void;
    onDirtyChange?(dirty: boolean): void;
    onSubmit(input: CustomBundleInput): void | Promise<unknown>;
}

interface DraftLine {
    ingredient: BundleIngredientOption;
    quantity: string;
    availableQuantity?: number | null;
}

function addQuantityStep(currentQuantity: string, unitSymbol?: string | null): string {
    const base = Number(currentQuantity || 0);
    const step = defaultBundleQuantity(unitSymbol);
    const next = (Number.isFinite(base) ? base : 0) + step;
    return String(Number(next.toFixed(3)));
}

function getErrorMessage(error: unknown) {
    return error instanceof Error ? error.message : "Có lỗi xảy ra, vui lòng thử lại.";
}

/** Form đóng trà theo yêu cầu tách 2 chế độ: MANUAL (Catalog thủ công) và AI_RECOMMENDATION (chỉ Top 5 AI). */
export function CustomBundleBuilder({
    branchId,
    customerId = null,
    mode,
    showAiSuggestions = false,
    submitLoading,
    submitError = "",
    onCancel,
    onDirtyChange,
    onSubmit
}: CustomBundleBuilderProps) {
    const resolvedMode: CustomBundleBuilderMode = mode ?? (showAiSuggestions ? "AI_RECOMMENDATION" : "MANUAL");
    const isAiMode = resolvedMode === "AI_RECOMMENDATION";

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
        if (isAiMode) {
            setOptions([]);
            setOptionsLoading(false);
            setOptionsError("");
            return;
        }
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
    }, [isAiMode, search]);

    const selectedIngredientIds = useMemo(
        () => lines.map((line) => line.ingredient.id),
        [lines]
    );

    const aiRecommendations = useIngredientRecommendations({
        enabled: isAiMode && Boolean(customerId),
        customerId,
        branchId,
        selectedIngredientIds
    });

    const isDirty = lines.length > 0 || bundleName.trim().length > 0 || note.trim().length > 0;
    useEffect(() => {
        onDirtyChange?.(isDirty);
    }, [isDirty, onDirtyChange]);

    const parsedLines = useMemo(() => {
        const latestStockById = new Map(
            (aiRecommendations.data?.items ?? []).map((item) => [item.ingredientId, item.availableQuantity])
        );
        return lines.map((line) => {
            if (!isAiMode) {
                const amount = Number(line.quantity);
                const valid = Number.isFinite(amount) && amount > 0;
                return {
                    ...line,
                    amount,
                    lineTotal: valid ? amount * line.ingredient.retailPricePerUnit : 0,
                    inlineError: "",
                    parsed: null,
                    priceMeta: null
                };
            }
            const stockLimit = latestStockById.get(line.ingredient.id) ?? line.availableQuantity ?? null;
            const parsed = parseFlexibleWeightInput(
                line.quantity,
                line.ingredient.unitSymbol,
                line.ingredient.retailPricePerUnit,
                stockLimit
            );
            const priceMeta = formatKgPriceMeta(line.ingredient.retailPricePerUnit, line.ingredient.unitSymbol);
            return {
                ...line,
                amount: parsed.ok ? parsed.baseQuantity : NaN,
                lineTotal: parsed.ok ? parsed.lineTotal : 0,
                inlineError: parsed.ok ? "" : parsed.error,
                parsed,
                priceMeta
            };
        });
    }, [aiRecommendations.data?.items, isAiMode, lines]);

    const estimatedPrice = parsedLines.reduce((sum, line) => sum + line.lineTotal, 0);

    function addIngredient(ingredient: BundleIngredientOption, availableQuantity?: number | null) {
        setAvailability(null);
        setValidationError("");
        setLines((current) => {
            const existing = current.find((line) => line.ingredient.id === ingredient.id);
            if (existing) {
                return current.map((line) =>
                    line.ingredient.id === ingredient.id
                        ? {
                              ...line,
                              availableQuantity: availableQuantity ?? line.availableQuantity,
                              quantity: isAiMode
                                  ? addFlexibleWeightStep(line.quantity, ingredient.unitSymbol)
                                  : addQuantityStep(line.quantity, ingredient.unitSymbol)
                          }
                        : line
                );
            }
            if (current.length >= bundleLimits.maxIngredients) {
                setValidationError(`Bundle tối đa ${bundleLimits.maxIngredients} nguyên liệu.`);
                return current;
            }
            const initialText = isAiMode
                ? defaultFlexibleWeightText(ingredient.unitSymbol)
                : String(defaultBundleQuantity(ingredient.unitSymbol));
            return [
                ...current,
                { ingredient, quantity: initialText, availableQuantity }
            ];
        });
    }

    function toBundleOption(item: RecommendedIngredient): BundleIngredientOption {
        return {
            id: item.ingredientId,
            ingredientCode: item.ingredientCode,
            name: item.ingredientName,
            unitSymbol: item.unitSymbol,
            retailPricePerUnit: item.retailPricePerUnit
        };
    }

    function addRecommendedIngredient(item: RecommendedIngredient) {
        addIngredient(toBundleOption(item), item.availableQuantity);
    }

    function addAllRecommendedIngredients(items: RecommendedIngredient[]) {
        if (items.length === 0) return;
        setAvailability(null);
        setValidationError("");
        setLines((current) => {
            const existingIds = new Set(current.map((line) => line.ingredient.id));
            const additions = items
                .filter((item) => !existingIds.has(item.ingredientId))
                .map((item) => ({
                    ingredient: toBundleOption(item),
                    quantity: isAiMode
                        ? defaultFlexibleWeightText(item.unitSymbol)
                        : String(defaultBundleQuantity(item.unitSymbol)),
                    availableQuantity: item.availableQuantity
                }));
            if (current.length + additions.length > bundleLimits.maxIngredients) {
                setValidationError(`Bundle tối đa ${bundleLimits.maxIngredients} nguyên liệu.`);
                return current;
            }
            return [...current, ...additions];
        });
    }

    function changeQuantity(ingredientId: string, value: string) {
        setAvailability(null);
        setValidationError("");
        setLines((current) =>
            current.map((line) => (line.ingredient.id === ingredientId ? { ...line, quantity: value } : line))
        );
    }

    function removeLine(ingredientId: string) {
        setAvailability(null);
        setValidationError("");
        setLines((current) => current.filter((line) => line.ingredient.id !== ingredientId));
    }

    function validate() {
        if (parsedLines.length === 0) return "Bundle phải có ít nhất một nguyên liệu.";
        const invalid = parsedLines.find(
            (line) => Boolean(line.inlineError) || !Number.isFinite(line.amount) || line.amount <= 0
        );
        if (invalid) {
            return invalid.inlineError
                ? `${invalid.ingredient.name}: ${invalid.inlineError}`
                : `Khối lượng ${invalid.ingredient.name} phải lớn hơn 0.`;
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
        void onSubmit({
            bundleName: bundleName.trim(),
            note: note.trim(),
            lines: requestLines()
        });
    }

    return (
        <section
            className="grid min-h-0 min-w-0 flex-1 overflow-hidden rounded-xl border border-[#e8eef0] bg-white shadow-pos-card"
            style={{ gridTemplateRows: "minmax(0,1fr) auto" }}
        >
            <div
                className={`grid min-h-0 min-w-0 grid-cols-1 gap-3.5 overflow-hidden p-3.5 ${
                    isAiMode
                        ? "lg:grid-cols-[minmax(0,1.2fr)_minmax(360px,1fr)]"
                        : "lg:grid-cols-[minmax(0,1fr)_380px]"
                }`}
            >
                {/* Vùng trái: AI_RECOMMENDATION chỉ hiển thị Top 5 AI; MANUAL hiển thị tìm kiếm Catalog thủ công */}
                <div className="flex min-h-0 min-w-0 flex-col gap-2.5 overflow-y-auto overflow-x-hidden pr-1">
                    {isAiMode ? (
                        customerId ? (
                            <div className="shrink-0">
                                <IngredientRecommendationPanel
                                    data={aiRecommendations.data}
                                    loading={aiRecommendations.loading}
                                    error={aiRecommendations.error}
                                    selectedIngredientIds={selectedIngredientIds}
                                    onRefresh={aiRecommendations.refresh}
                                    onAdd={addRecommendedIngredient}
                                    onAddAll={addAllRecommendedIngredients}
                                />
                            </div>
                        ) : (
                            <p
                                role="status"
                                className="rounded-xl border border-[#dceade] bg-[#f4faf5] px-4 py-5 text-sm font-semibold text-[#536078]"
                            >
                                Vui lòng chọn khách hàng trên đơn hàng để xem AI gợi ý nguyên liệu cá nhân hóa.
                            </p>
                        )
                    ) : (
                        <div className="flex min-w-0 flex-col gap-2">
                            <div className="relative">
                                <Search aria-hidden="true" className="absolute left-3 top-3 text-[#697684]" size={16} />
                                <input
                                    className="h-10 w-full rounded-lg border border-pos-border pl-9 pr-3 text-sm font-bold outline-none focus:border-[#159734]"
                                    onChange={(event) => setSearch(event.currentTarget.value)}
                                    placeholder="Tìm nguyên liệu bán lẻ theo tên hoặc mã"
                                    value={search}
                                />
                            </div>
                            <div className="grid min-w-0 auto-rows-min grid-cols-1 sm:grid-cols-2 gap-2 rounded-xl border border-[#e8eef0] bg-[#f8fbf8] p-2">
                                {optionsLoading ? (
                                    <p className="col-span-2 px-3 py-4 text-sm font-bold text-[#697684]">
                                        Đang tải nguyên liệu...
                                    </p>
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
                                        className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-0.5 rounded-lg border border-transparent bg-white px-3 py-2 text-left hover:border-[#159734]"
                                        key={ingredient.id}
                                        onClick={() => addIngredient(ingredient)}
                                        type="button"
                                    >
                                        <span className="flex min-w-0 items-center gap-2 font-black text-[#172317]">
                                            <Leaf aria-hidden="true" className="shrink-0 text-[#159734]" size={15} />
                                            <span className="truncate">{ingredient.name}</span>
                                        </span>
                                        <span className="break-words text-xs font-bold text-[#697684]">
                                            {ingredient.ingredientCode} · {formatCurrency(ingredient.retailPricePerUnit)} /{" "}
                                            {ingredient.unitSymbol ?? "đơn vị"}
                                        </span>
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}
                </div>

                {/* Vùng phải: Gói trà đã chọn (danh sách, khối lượng, ghi chú & xác nhận thêm vào đơn) */}
                <aside className="grid min-h-0 min-w-0 grid-rows-[auto_minmax(0,1fr)_auto] gap-2 border-t pt-3 lg:border-t-0 lg:border-l lg:border-slate-200 lg:pt-0 lg:pl-3.5">
                    <div className="flex items-center justify-between gap-2 border-b border-slate-100 pb-2">
                        <span className="text-xs font-black uppercase tracking-wide text-slate-900">
                            {isAiMode ? "Gói trà đã chọn" : "Thành phần trong gói"}
                        </span>
                        {!isAiMode ? (
                            <span className="rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-xs font-bold text-emerald-700">
                                {parsedLines.length} / {bundleLimits.maxIngredients} nguyên liệu
                            </span>
                        ) : null}
                    </div>
                    <div className="grid min-h-0 auto-rows-min gap-2 overflow-y-auto pr-0.5">
                        {parsedLines.length === 0 ? (
                            <p className="rounded-lg border border-dashed border-slate-200 bg-slate-50/60 px-3 py-4 text-center text-xs font-semibold text-slate-500">
                                {isAiMode
                                    ? "Bấm \"Thêm\" ở danh sách AI gợi ý bên trái để bắt đầu tạo gói trà."
                                    : "Tìm kiếm và chọn nguyên liệu ở bên trái."}
                            </p>
                        ) : null}
                        {parsedLines.map((line) => {
                            const material = availability?.lines.find(
                                (item) => item.ingredientId === line.ingredient.id
                            );
                            const baseWeightUnit = isWeightUnit(line.ingredient.unitSymbol);
                            return (
                                <div className="grid gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2" key={line.ingredient.id}>
                                    <div className="flex items-start justify-between gap-2">
                                        <div className="min-w-0">
                                            <strong className="block text-sm font-bold text-slate-900 truncate">
                                                {line.ingredient.name}
                                            </strong>
                                            {isAiMode && line.priceMeta ? (
                                                <span className="text-[11px] font-semibold text-slate-500">
                                                    {formatCurrency(line.priceMeta.price)} / {line.priceMeta.unitLabel}
                                                </span>
                                            ) : null}
                                        </div>
                                        <IconButton
                                            aria-label={`Bỏ ${line.ingredient.name}`}
                                            onClick={() => removeLine(line.ingredient.id)}
                                            size="xs"
                                            tone="danger"
                                        >
                                            <Trash2 size={15} aria-hidden="true" />
                                        </IconButton>
                                    </div>
                                    {isAiMode ? (
                                        <div className="grid gap-1">
                                            <div className="flex items-center gap-2">
                                                <input
                                                    aria-label={`Khối lượng ${line.ingredient.name}`}
                                                    className={`h-8 min-w-0 flex-1 rounded-md border bg-slate-50/70 px-2.5 text-xs font-semibold text-slate-900 outline-none transition placeholder:font-normal placeholder:text-slate-400 focus:bg-white ${
                                                        line.inlineError
                                                            ? "border-red-300 focus:border-red-500"
                                                            : "border-slate-200 focus:border-emerald-500"
                                                    }`}
                                                    inputMode="decimal"
                                                    onChange={(event) => changeQuantity(line.ingredient.id, event.currentTarget.value)}
                                                    placeholder="Nhập khối lượng: 10g, 0.5kg..."
                                                    type="text"
                                                    value={line.quantity}
                                                />
                                                <span className="shrink-0 text-sm font-black text-emerald-700 whitespace-nowrap">
                                                    {formatCurrency(line.lineTotal)}
                                                </span>
                                            </div>
                                            {line.inlineError ? (
                                                <p role="alert" className="text-[11px] font-semibold text-red-600">
                                                    {line.inlineError}
                                                </p>
                                            ) : line.parsed?.ok &&
                                              baseWeightUnit &&
                                              line.parsed.enteredUnit &&
                                              line.parsed.enteredUnit !== baseWeightUnit ? (
                                                <span className="text-[11px] font-medium text-slate-500">
                                                    Quy đổi gửi kho: {line.parsed.baseQuantity.toLocaleString("vi-VN", { maximumFractionDigits: 3 })}{" "}
                                                    {line.ingredient.unitSymbol}
                                                </span>
                                            ) : null}
                                        </div>
                                    ) : (
                                        <div className="flex items-center gap-2">
                                            <input
                                                aria-label={`Khối lượng ${line.ingredient.name}`}
                                                className="h-8 w-24 rounded-lg border border-pos-border px-2.5 text-sm font-bold"
                                                min={0}
                                                step="any"
                                                onChange={(event) => changeQuantity(line.ingredient.id, event.currentTarget.value)}
                                                type="number"
                                                value={line.quantity}
                                            />
                                            <span className="text-xs font-bold text-slate-500">
                                                {line.ingredient.unitSymbol ?? ""}
                                            </span>
                                            <span className="ml-auto text-sm font-black text-emerald-700">
                                                {formatCurrency(line.lineTotal)}
                                            </span>
                                        </div>
                                    )}
                                    {material && material.missingQuantity > 0 ? (
                                        <span className="text-xs font-bold text-amber-700">
                                            Thiếu {material.missingQuantity}
                                            {line.ingredient.unitSymbol ?? ""} · dự kiến có{" "}
                                            {formatAvailabilityDate(material.expectedArrivalAt)}
                                        </span>
                                    ) : null}
                                </div>
                            );
                        })}
                    </div>

                    <div className="grid gap-2 border-t border-slate-100 pt-2">
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                            <TextInput
                                maxLength={255}
                                onChange={(event) => setBundleName(event.currentTarget.value)}
                                placeholder="Tên gói trà (tuỳ chọn)"
                                value={bundleName}
                            />
                            <TextInput
                                maxLength={1000}
                                onChange={(event) => setNote(event.currentTarget.value)}
                                placeholder="Ghi chú đóng gói (tuỳ chọn)"
                                value={note}
                            />
                        </div>
                        <div className="grid gap-1 rounded-lg border border-emerald-200 bg-emerald-50/50 px-3 py-2">
                            <div className="flex items-center justify-between gap-2">
                                <span className="text-xs font-bold text-slate-700">Giá dự kiến của Bundle</span>
                                <strong className="text-base font-black text-emerald-700">
                                    {formatCurrency(estimatedPrice)}
                                </strong>
                            </div>
                            {availability ? (
                                availability.enough ? (
                                    <span className="text-xs font-semibold text-emerald-700">
                                        Đủ nguyên liệu, đóng gói ngay sau khi thu tiền.
                                    </span>
                                ) : (
                                    <span className="text-xs font-semibold text-amber-700">
                                        Thiếu nguyên liệu, dự kiến có {formatAvailabilityDate(availability.estimatedAvailableAt)}.
                                        Khách có thể đặt cọc và nhận sau.
                                    </span>
                                )
                            ) : null}
                        </div>
                        {validationError || submitError ? (
                            <p
                                role="alert"
                                className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs font-semibold text-red-700"
                            >
                                {validationError || submitError}
                            </p>
                        ) : null}
                    </div>
                </aside>
            </div>

            <footer className="flex flex-wrap items-center justify-end gap-2.5 border-t border-slate-200 bg-slate-50/60 px-4 py-3">
                <Button size="sm" variant="secondary" disabled={submitLoading} onClick={onCancel}>
                    Hủy
                </Button>
                <Button size="sm" variant="secondary" disabled={submitLoading || availabilityLoading} onClick={() => void checkAvailability()}>
                    {availabilityLoading ? "Đang kiểm tra..." : "Kiểm tra tồn"}
                </Button>
                <Button size="sm" disabled={submitLoading} onClick={submit} variant="primary">
                    {submitLoading ? "Đang thêm..." : "Thêm vào đơn"}
                </Button>
            </footer>
        </section>
    );
}
