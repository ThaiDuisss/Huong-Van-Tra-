import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { orderedTasteAttributes, tasteSurveyApi, type TasteAttribute } from "../../customer/services/tasteSurvey.api";
import type { GuestTasteSurvey } from "../../customer/services/guestTasteSurvey";
import { PosVariantSelector } from "../../sales/components/product/PosVariantSelector";
import { formatCurrency } from "../../sales/services/money";
import { posProductApi } from "../../sales/services/posProduct.api";
import { initialPosVariant, posVariantSelectionError } from "../../sales/services/posProductSelection";
import type { SaleProductPreview } from "../../sales/types/sale.types";
import { recommendationRequestOptions, resolveRecommendedVariant } from "../services/productRecommendation.api";
import {
  fetchTastePreview, fetchTasteVariantDetails, tastePreviewError, type TastePreviewProduct, type TastePreviewRequest,
  type TastePreviewVariant,
} from "../services/tastePreview.api";

interface TastePreviewLauncherProps {
  disabled?: boolean;
  onAdd(product: SaleProductPreview): Promise<boolean>;
  onManualConsultation(): void;
}

const preparationPattern = /^[A-Z][A-Z0-9_]{0,79}$/;
const previewMessages = {
  INSUFFICIENT_MATCHES: "Đang hiển thị các sản phẩm phù hợp hiện có.",
  NO_MATCH: "Không có sản phẩm phù hợp với các tiêu chí đã chọn.",
  NO_CRITERIA: "Chọn ít nhất một tiêu chí để xem gợi ý.",
  TASTE_PROFILE_NOT_READY: "Chưa có hồ sơ khẩu vị đủ điều kiện cho cách pha này.",
  TASTE_CATALOG_UNAVAILABLE: "Dữ liệu tư vấn chưa sẵn sàng. Vui lòng thử lại sau.",
  TAXONOMY_MAPPING_REQUIRED: "Bộ tiêu chí đã thay đổi. Vui lòng mở lại tư vấn để tải bản hiện hành.",
  OK: "",
};

export function TastePreviewLauncher({ disabled, onAdd, onManualConsultation }: TastePreviewLauncherProps) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);

  function changeOpen(value: boolean) {
    setOpen(value);
    if (!value) trigger.current?.focus();
  }

  return (
    <>
      <Button ref={trigger} type="button" size="sm" variant="secondary" disabled={disabled}
        onClick={() => changeOpen(true)}>
        Tư vấn chọn trà
      </Button>
      <Dialog open={open} onOpenChange={changeOpen}>
        {open && (
          <DialogContent className="max-w-3xl">
            <DialogHeader>
              <DialogTitle>Tư vấn chọn trà</DialogTitle>
              <DialogDescription>Chọn khẩu vị của khách để tìm trà phù hợp.</DialogDescription>
            </DialogHeader>
            <TastePreviewPanel disabled={disabled} onAdd={(product) => {
              changeOpen(false);
              return onAdd(product);
            }} onManualConsultation={() => {
              changeOpen(false);
              onManualConsultation();
            }} />
          </DialogContent>
        )}
      </Dialog>
    </>
  );
}

function TastePreviewPanel({ disabled, onAdd, onManualConsultation }: TastePreviewLauncherProps) {
  const taxonomyQuery = useQuery({
    queryKey: ["current-taste-taxonomy"], queryFn: tasteSurveyApi.currentTaxonomy, retry: false,
    staleTime: 0, refetchOnWindowFocus: false,
  });
  const taxonomy = taxonomyQuery.isFetching || taxonomyQuery.isError ? undefined : taxonomyQuery.data;
  const identity = taxonomy && `${taxonomy.versionCode}:${taxonomy.manifestSha256}`;
  const attributes = useMemo(() => orderedTasteAttributes(taxonomy), [taxonomy]);
  const [draft, setDraft] = useState<{
    identity: string; groups: Record<string, { values: string[]; hard: boolean; notApplicable?: boolean }>;
  }>();
  const groups = draft?.identity === identity ? draft?.groups : undefined;
  const [preparation, setPreparation] = useState("");
  const [revision, setRevision] = useState(0);
  const [outcome, setOutcome] = useState<{
    input: Omit<TastePreviewRequest, "requestSequence">; revision: number;
    result?: Awaited<ReturnType<typeof fetchTastePreview>>; error?: string;
  }>();
  const [adding, setAdding] = useState(false);
  const [addError, setAddError] = useState("");
  const addRequest = useRef<AbortController | null>(null);
  const sequence = useRef(0);
  const preparationCode = preparation.trim();
  const criteria = useMemo(() => {
    const hardGroups: TastePreviewRequest["hardGroups"] = [];
    const softGroups: NonNullable<TastePreviewRequest["softGroups"]> = [];
    const notApplicableAttributes: string[] = [];
    for (const attribute of attributes) {
      const selection = groups?.[attribute.attributeCode];
      if (!selection) continue;
      if (selection.notApplicable && attribute.allowsNotApplicable) {
        notApplicableAttributes.push(attribute.attributeCode);
      } else if (selection.values.length && selection.hard) {
        hardGroups.push({ attribute: attribute.attributeCode, allowedValues: selection.values });
      } else if (selection.values.length) {
        softGroups.push({ attribute: attribute.attributeCode, priorities: selection.values.map((value) => ({ value, weight: 1 })) });
      }
    }
    return { hardGroups, softGroups, notApplicableAttributes };
  }, [attributes, groups]);
  const hasCriteria = Boolean(criteria.hardGroups.length || criteria.softGroups.length);
  const input = useMemo(() => !disabled && taxonomy && preparationPattern.test(preparationCode) && hasCriteria ? {
      taxonomyVersion: taxonomy.versionCode, taxonomySha256: taxonomy.manifestSha256,
      preparationCode, ...criteria, limit: 5,
    } : null, [disabled, criteria, hasCriteria, preparationCode, taxonomy]);
  const current = outcome?.input === input && outcome.revision === revision ? outcome : undefined;

  useEffect(() => {
    if (!input) return;
    const controller = new AbortController();
    const requestSequence = ++sequence.current;
    const timer = window.setTimeout(async () => {
      try {
        const result = await fetchTastePreview({ ...input, requestSequence }, controller.signal);
        if (!controller.signal.aborted) setOutcome({ input, revision, result });
      } catch (error) {
        if (!controller.signal.aborted) setOutcome({ input, revision, error: tastePreviewError(error) });
      }
    }, 250);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [input, revision]);

  useEffect(() => () => addRequest.current?.abort(), []);

  function updateGroup(attribute: string, selection: NonNullable<typeof groups>[string]) {
    if (!identity) return;
    setDraft({ identity, groups: { ...groups, [attribute]: selection } });
    setAddError("");
  }

  async function addVariant(productId: string, variantId: string) {
    if (disabled || addRequest.current) return;
    const request = new AbortController();
    addRequest.current = request;
    setAdding(true);
    setAddError("");
    try {
      const product = await resolveRecommendedVariant(productId, variantId, request.signal);
      if (!request.signal.aborted) await onAdd(product);
    } catch {
      if (!request.signal.aborted) setAddError("Không xác minh được biến thể đang bán. Vui lòng thử lại.");
    } finally {
      addRequest.current = null;
      if (!request.signal.aborted) setAdding(false);
    }
  }

  const statusMessage = current?.result ? previewMessages[current.result.status] : "";
  return (
    <div className="grid max-h-[72vh] gap-4 overflow-y-auto px-6 py-4">
      {taxonomyQuery.isFetching && <p role="status">Đang tải bộ tiêu chí…</p>}
      {taxonomyQuery.isError && <p role="alert">{tastePreviewError(taxonomyQuery.error)}</p>}
      {taxonomy && !attributes.length && <p role="alert">Chưa có bộ tiêu chí khẩu vị đang áp dụng.</p>}
      {attributes.length > 0 && (
        <fieldset disabled={disabled || adding} className="grid gap-3">
          <label className="grid max-w-sm gap-1 text-sm font-semibold">
            Mã cách pha đã duyệt
            <input className="rounded-lg border border-[#dce5e1] px-3 py-2 font-normal" maxLength={80}
              value={preparation} onChange={(event) => {
                setPreparation(event.target.value);
                setAddError("");
              }} />
          </label>
          <div className="grid gap-3 sm:grid-cols-2">
            {attributes.map((attribute) => {
              const code = attribute.attributeCode;
              const selection = groups?.[code] ?? { values: [], hard: true };
              return <div key={code} className="grid gap-2">
                <TasteFilter attribute={attribute} selected={selection.values}
                  onChange={(values) => updateGroup(code, { ...selection, values, notApplicable: false })} />
                {selection.values.length > 0 && <label className="flex items-center gap-2 text-xs">
                  <input type="checkbox" checked={selection.hard}
                    onChange={(event) => updateGroup(code, { ...selection, hard: event.target.checked })} />
                  Bắt buộc
                </label>}
                {attribute.allowsNotApplicable && <Button type="button" size="sm" aria-pressed={Boolean(selection.notApplicable)}
                  className="justify-self-start" onClick={() => updateGroup(code, {
                    ...selection, values: [], notApplicable: !selection.notApplicable,
                  })}>Không áp dụng</Button>}
              </div>;
            })}
          </div>
        </fieldset>
      )}
      {attributes.length > 0 && !preparationPattern.test(preparationCode) && (
        <p className="text-sm text-slate-600">Nhập mã cách pha dùng chữ in hoa, số và dấu gạch dưới.</p>
      )}
      {attributes.length > 0 && preparationPattern.test(preparationCode) && !hasCriteria && (
        <p className="text-sm text-slate-600">Chọn ít nhất một tiêu chí để xem gợi ý.</p>
      )}
      {input && !current && <p role="status">Đang tìm sản phẩm phù hợp…</p>}
      {current?.error && <div role="alert" className="flex items-center gap-3 text-sm text-amber-900">
        <span>{current.error}</span>
        <Button type="button" size="sm" onClick={() => setRevision((value) => value + 1)}>Thử lại</Button>
      </div>}
      {statusMessage && <p role="status" className="text-sm text-slate-600">{statusMessage}</p>}
      {addError && <p role="alert" className="text-sm text-amber-900">{addError}</p>}
      {current?.result?.items.map((product) => (
        <TasteResultCard key={product.productId} product={product} attributes={attributes}
          disabled={disabled || adding} onAdd={(variantId) => void addVariant(product.productId, variantId)} />
      ))}
      <Button type="button" size="sm" variant="secondary" className="justify-self-start"
        disabled={disabled || adding} onClick={onManualConsultation}>
        Tư vấn pha chế theo yêu cầu
      </Button>
    </div>
  );
}

export function TasteSurveyRecommendations({ survey, disabled, onAdd }: {
  survey: GuestTasteSurvey; disabled?: boolean; onAdd(product: SaleProductPreview): Promise<boolean>;
}) {
  const taxonomyQuery = useQuery({
    queryKey: ["taste-taxonomy-release", survey.taxonomyVersion, survey.taxonomySha256],
    queryFn: ({ signal }) => tasteSurveyApi.taxonomyRelease(survey.taxonomyVersion, survey.taxonomySha256, signal),
    retry: false,
  });
  const attributes = taxonomyQuery.data?.manifest.attributes ?? [];
  const previewQuery = useQuery({
    queryKey: ["guest-taste-preview", survey.taxonomyVersion, survey.taxonomySha256, survey.preparationCode, survey.criteria],
    enabled: taxonomyQuery.data?.state === "PUBLISHED",
    retry: false,
    queryFn: ({ signal }) => fetchTastePreview({
      taxonomyVersion: survey.taxonomyVersion,
      taxonomySha256: survey.taxonomySha256,
      preparationCode: survey.preparationCode,
      requestSequence: 1,
      limit: 5,
      hardGroups: survey.criteria.hardGroups,
      softGroups: survey.criteria.softGroups,
      notApplicableAttributes: survey.criteria.notApplicableAttributes ?? [],
    }, signal),
  });
  const [adding, setAdding] = useState(false);
  const [addError, setAddError] = useState("");
  const addRequest = useRef<AbortController | null>(null);

  useEffect(() => () => addRequest.current?.abort(), []);

  async function addVariant(productId: string, variantId: string) {
    if (disabled || addRequest.current) return;
    const request = new AbortController();
    addRequest.current = request;
    setAdding(true);
    setAddError("");
    try {
      const product = await resolveRecommendedVariant(productId, variantId, request.signal);
      if (!request.signal.aborted) await onAdd(product);
    } catch {
      if (!request.signal.aborted) setAddError("Không xác minh được biến thể đang bán. Vui lòng thử lại.");
    } finally {
      addRequest.current = null;
      if (!request.signal.aborted) setAdding(false);
    }
  }

  const result = taxonomyQuery.data?.state === "PUBLISHED" ? previewQuery.data : undefined;
  const statusMessage = result ? previewMessages[result.status] : "";
  return (
    <div className="grid gap-3">
      <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-800">
        Gợi ý theo khảo sát khẩu vị của khách vãng lai (chỉ lưu tạm trên hóa đơn này).
      </p>
      {taxonomyQuery.isError && <p role="alert" className="text-sm">Không đọc được bộ tiêu chí đã lưu.</p>}
      {taxonomyQuery.data?.state === "RETIRED" &&
        <p role="status" className="text-sm">Bộ tiêu chí đã ngừng sử dụng. Mở Tư vấn trà để chọn lại khẩu vị.</p>}
      {(taxonomyQuery.isPending || taxonomyQuery.data?.state === "PUBLISHED" && previewQuery.isPending) &&
        <p role="status" className="text-sm">Đang tìm sản phẩm phù hợp…</p>}
      {previewQuery.isError && <div role="alert" className="flex items-center gap-3 text-sm text-amber-900">
        <span>{tastePreviewError(previewQuery.error)}</span>
        <Button type="button" size="sm" onClick={() => void previewQuery.refetch()}>Thử lại</Button>
      </div>}
      {statusMessage && <p role="status" className="text-sm text-slate-600">{statusMessage}</p>}
      {addError && <p role="alert" className="text-sm text-amber-900">{addError}</p>}
      {result?.items.map((product) => (
        <TasteResultCard key={product.productId} product={product} attributes={attributes}
          disabled={disabled || adding} onAdd={(variantId) => void addVariant(product.productId, variantId)} />
      ))}
    </div>
  );
}

function TasteFilter({ attribute, selected, onChange }: {
  attribute: TasteAttribute; selected: string[]; onChange(next: string[]): void;
}) {
  const options = [...attribute.values].sort((left, right) => left.displayOrder - right.displayOrder);
  if (attribute.selectionKind !== "ENUM_MULTI") {
    return (
      <label className="grid gap-1 text-sm font-semibold">
        {attribute.label}
        <select className="rounded-lg border border-[#dce5e1] bg-white px-3 py-2 font-normal"
          value={selected[0] ?? ""} onChange={(event) => onChange(event.target.value ? [event.target.value] : [])}>
          <option value="">Bất kỳ</option>
          {options.map((option) => <option key={option.valueCode} value={option.valueCode}>{option.label}</option>)}
        </select>
      </label>
    );
  }
  return (
    <fieldset className="flex flex-wrap gap-x-3 gap-y-2 rounded-lg border border-[#dce5e1] px-3 py-2 text-sm">
      <legend className="px-1 font-semibold">{attribute.label}</legend>
      {options.map(({ valueCode, label }) => {
        const checked = selected.includes(valueCode);
        const disabled = !checked && valueCode !== "NONE" && !selected.includes("NONE") &&
          selected.length >= attribute.maxSelections;
        return <label key={valueCode} className="inline-flex items-center gap-1.5">
          <input type="checkbox" checked={checked} disabled={disabled} onChange={() => onChange(
            checked ? selected.filter((value) => value !== valueCode) :
              valueCode === "NONE" ? [valueCode] : [...selected.filter((value) => value !== "NONE"), valueCode],
          )} />
          {label}
        </label>;
      })}
    </fieldset>
  );
}

function TasteMatchSummary({ match, attributes }: {
  match: TastePreviewVariant;
  attributes: TasteAttribute[];
}) {
  const attributesByCode = new Map(attributes.map((attribute) => [attribute.attributeCode, attribute]));
  const valueLabelCounts = new Map<string, number>();
  for (const attribute of attributes) {
    for (const option of attribute.values) {
      valueLabelCounts.set(option.label, (valueLabelCounts.get(option.label) ?? 0) + 1);
    }
  }
  function labelText(values: Array<{ attribute: string; value: string }>) {
    const labels = new Map<string, string>();
    for (const { attribute: attributeCode, value: valueCode } of values) {
      const attribute = attributesByCode.get(attributeCode);
      const value = attribute?.values.find((option) => option.valueCode === valueCode);
      if (!attribute || !value || valueCode === "UNKNOWN") continue;

      const ambiguousLabel = (valueLabelCounts.get(value.label) ?? 0) > 1;
      const qualified = (attribute.selectionKind === "ORDINAL_SINGLE" || ambiguousLabel) && valueCode !== "NONE";
      const attributeName = attribute.label.replace(/\s+\([^()]*\)$/, "");
      labels.set(`${attributeCode}:${valueCode}`, qualified ? `${attributeName} (${value.label})` : value.label);
    }
    return [...labels.values()].join(", ");
  }
  const matched = labelText([
    ...match.matchedHard.flatMap(({ attribute, matchedValues }) =>
      matchedValues.map((value) => ({ attribute, value }))),
    ...(match.matchedSoft ?? []).filter(({ status }) => status === "MATCHED"),
  ]);
  const unmatched = labelText((match.unmatchedSoft ?? []).filter(({ status }) => status === "NOT_MATCHED"));

  return <>
    {matched && <p className="text-xs text-emerald-800"><strong>Phù hợp:</strong> {matched}</p>}
    {unmatched && <p className="text-xs text-slate-600"><strong>Chưa khớp:</strong> {unmatched}</p>}
  </>;
}

export function TasteResultCard({
  product, attributes, disabled, onAdd, requireAvailable = false, rank, topTied, adding,
}: {
  product: TastePreviewProduct; attributes: TasteAttribute[]; disabled?: boolean; onAdd(variantId: string): void;
  requireAvailable?: boolean; rank?: number; topTied?: boolean; adding?: boolean;
}) {
  const { data, isPending, isError } = useQuery({
    queryKey: ["pos-taste-variants", product.productId], retry: false,
    queryFn: ({ signal }) => posProductApi.listProductVariants(product.productId, { ...recommendationRequestOptions, signal }),
  });
  const variants = (data ?? []).filter((variant) => variant.productId === product.productId &&
    (!requireAvailable || ((variant.availableQuantity ?? 0) > 0 && !posVariantSelectionError(variant))) &&
    product.compatibleVariants.some((match) => match.variantId === (variant.productVariantId ?? variant.id) &&
      match.sku === variant.sku));
  const [selection, setSelection] = useState("");
  const eligibleMatches = product.compatibleVariants.filter((match) => variants.some((variant) =>
    (variant.productVariantId ?? variant.id) === match.variantId && !posVariantSelectionError(variant)));
  const selectedMatch = eligibleMatches.find((match) => match.variantId === selection);
  const defaultId = rank === undefined ? initialPosVariant(variants) : eligibleMatches[0]?.variantId ?? "";
  const selectedId = selectedMatch?.variantId ?? defaultId;
  const detailId = selectedId || (variants[0]?.productVariantId ?? variants[0]?.id) || "";
  const details = useQuery({
    queryKey: ["pos-taste-variant-details", product.productId, detailId], enabled: Boolean(detailId), retry: false,
    queryFn: ({ signal }) => fetchTasteVariantDetails(product.productId, detailId, signal),
  });
  const selected = variants.find((variant) => (variant.productVariantId ?? variant.id) === selectedId);
  if (rank !== undefined) {
    const match = selectedMatch ?? eligibleMatches[0];
    return (
      <article className={`grid gap-2 rounded-xl border p-3 ${rank === 1 ? "border-emerald-200 bg-emerald-50/40" : "border-[#dce5e1] bg-white"}`}>
        <div className="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-3">
          <div className="flex min-w-0 items-start gap-2.5">
            {details.data?.imageUrl && <img src={details.data.imageUrl} alt="" loading="lazy"
              className="size-12 shrink-0 rounded-md object-cover" />}
            <div className="grid min-w-0 gap-1">
              <span className={`text-xs font-semibold ${rank === 1 ? "text-emerald-800" : "text-slate-600"}`}>
                Top {rank}{rank === 1 && (topTied ? " — Phù hợp nhất (đồng điểm)" : " — Phù hợp nhất")}
              </span>
              <h4 className="text-sm font-bold text-[#172317]">{product.productName}</h4>
              {eligibleMatches.length > 1 ? (
                <select aria-label={`Chọn biến thể cho ${product.productName}`} value={selectedId}
                  disabled={disabled} onChange={(event) => setSelection(event.target.value)}
                  className="min-w-0 rounded-md border border-[#dce5e1] bg-white px-2 py-1 text-xs text-[#172317]">
                  {eligibleMatches.map((item) => <option key={item.variantId} value={item.variantId}>
                    {item.variantName}
                  </option>)}
                </select>
              ) : match && <p className="text-xs text-slate-700">{match.variantName}</p>}
            </div>
          </div>
          {selected && <span className="whitespace-nowrap text-right text-sm font-bold tabular-nums text-[#172317]">
            {formatCurrency(selected.price)}
          </span>}
        </div>
        {match && <TasteMatchSummary match={match} attributes={attributes} />}
        {isPending && <p role="status" className="text-xs text-slate-600">Đang lấy giá và quy cách bán…</p>}
        {(isError || (!isPending && !selected)) && <p className="text-xs text-amber-900">
          Chưa xác minh được biến thể đang bán trên POS.
        </p>}
        <Button type="button" size="sm" variant="primary" className="justify-self-end"
          disabled={disabled || !selectedId || adding} aria-busy={adding}
          onClick={() => onAdd(selectedId)}>
          {adding ? <><LoaderCircle className="size-4 animate-spin" aria-hidden="true" /> Đang thêm…</> : "Thêm vào hóa đơn"}
        </Button>
      </article>
    );
  }
  return (
    <article className="grid gap-2 rounded-xl border border-[#dce5e1] p-4">
      <div className="flex items-center gap-3">
        {details.data?.imageUrl && <img src={details.data.imageUrl} alt="" loading="lazy"
          className="size-12 rounded-md object-cover" />}
        <h3 className="font-bold">{product.productName}</h3>
      </div>
      {product.compatibleVariants.map((variant) => (
        <div key={variant.variantId} className="text-sm">
          <p className="font-semibold">{variant.variantName}{variant.sku ? ` · ${variant.sku}` : ""}</p>
          {variant.matchedHard.map((match) => {
            const attribute = attributes.find((item) => item.attributeCode === match.attribute);
            return attribute ? <p key={match.attribute} className="text-[#196a3c]">
              {attribute.label} · {match.matchedValues.map((value) =>
                attribute.values.find((option) => option.valueCode === value)?.label).filter(Boolean).join(", ")}
            </p> : null;
          })}
          <p className="text-slate-600">{variant.reason}</p>
        </div>
      ))}
      {isPending && <p role="status" className="text-sm">Đang lấy giá và quy cách bán…</p>}
      {(isError || (!isPending && !variants.length)) && <p className="text-sm text-amber-900">
        Chưa xác minh được biến thể đang bán trên POS.
      </p>}
      <PosVariantSelector variants={variants} selectedId={selectedId} disabled={disabled}
        onSelect={setSelection} formatCurrency={formatCurrency} />
      {details.data?.unitName && selectedId && <p className="text-sm text-slate-600">Đơn vị bán: {details.data.unitName}</p>}
      <Button type="button" size="sm" className="justify-self-start" disabled={disabled || !selectedId}
        onClick={() => onAdd(selectedId)}>
        Thêm vào hóa đơn
      </Button>
    </article>
  );
}
