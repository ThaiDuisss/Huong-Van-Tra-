import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/Button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { tasteSurveyApi, type TasteAttribute } from "../../customer/services/tasteSurvey.api";
import type { GuestTasteSurvey } from "../../customer/services/guestTasteSurvey";
import { PosVariantSelector } from "../../sales/components/product/PosVariantSelector";
import { formatCurrency } from "../../sales/services/money";
import { posProductApi } from "../../sales/services/posProduct.api";
import { initialPosVariant, posVariantSelectionError } from "../../sales/services/posProductSelection";
import type { SaleProductPreview } from "../../sales/types/sale.types";
import { recommendationRequestOptions, resolveRecommendedVariant } from "../services/productRecommendation.api";
import {
  fetchTastePreview, fetchTasteVariantUnit, tastePreviewError, type TastePreviewProduct, type TastePreviewRequest,
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
  });
  const taxonomy = taxonomyQuery.data;
  const attributes = useMemo(() => taxonomy?.versionCode === "hvtra-tea-v3"
    ? [...taxonomy.manifest.attributes].sort((left, right) => left.displayOrder - right.displayOrder)
    : [], [taxonomy]);
  const [values, setValues] = useState<Record<string, string[]>>({});
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
  const hardGroups = useMemo(() => attributes.flatMap((attribute) => {
    const allowedValues = values[attribute.attributeCode]?.filter((value) =>
      attribute.values.some((option) => option.valueCode === value));
    return allowedValues?.length ? [{ attribute: attribute.attributeCode, allowedValues }] : [];
  }), [attributes, values]);
  const input = useMemo(() => !disabled && taxonomy &&
    preparationPattern.test(preparationCode) && hardGroups.length ? {
      taxonomyVersion: taxonomy.versionCode, taxonomySha256: taxonomy.manifestSha256,
      preparationCode, hardGroups, limit: 5,
    } : null, [disabled, hardGroups, preparationCode, taxonomy]);
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

  function updateValues(attribute: TasteAttribute, next: string[]) {
    setValues((previous) => ({ ...previous, [attribute.attributeCode]: next }));
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
      {taxonomyQuery.isPending && <p role="status">Đang tải bộ tiêu chí…</p>}
      {taxonomyQuery.isError && <p role="alert">{tastePreviewError(taxonomyQuery.error)}</p>}
      {taxonomy && !attributes.length && <p role="alert">Chưa có bộ tiêu chí khẩu vị v3 đang áp dụng.</p>}
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
            {attributes.map((attribute) => (
              <TasteFilter key={attribute.attributeCode} attribute={attribute}
                selected={values[attribute.attributeCode] ?? []}
                onChange={(next) => updateValues(attribute, next)} />
            ))}
          </div>
        </fieldset>
      )}
      {attributes.length > 0 && !preparationPattern.test(preparationCode) && (
        <p className="text-sm text-slate-600">Nhập mã cách pha dùng chữ in hoa, số và dấu gạch dưới.</p>
      )}
      {attributes.length > 0 && preparationPattern.test(preparationCode) && !hardGroups.length && (
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
    queryKey: ["current-taste-taxonomy"], queryFn: tasteSurveyApi.currentTaxonomy, retry: false,
  });
  const attributes = taxonomyQuery.data?.manifest.attributes ?? [];
  const previewQuery = useQuery({
    queryKey: ["guest-taste-preview", survey.savedAt, survey.taxonomySha256, survey.preparationCode],
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

  const result = previewQuery.data;
  const statusMessage = result ? previewMessages[result.status] : "";
  return (
    <div className="grid gap-3">
      <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-800">
        Gợi ý theo khảo sát khẩu vị của khách vãng lai (chỉ lưu tạm trên hóa đơn này).
      </p>
      {previewQuery.isPending && <p role="status" className="text-sm">Đang tìm sản phẩm phù hợp…</p>}
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

function TasteResultCard({ product, attributes, disabled, onAdd }: {
  product: TastePreviewProduct; attributes: TasteAttribute[]; disabled?: boolean; onAdd(variantId: string): void;
}) {
  const { data, isPending, isError } = useQuery({
    queryKey: ["pos-taste-variants", product.productId], retry: false,
    queryFn: ({ signal }) => posProductApi.listProductVariants(product.productId, { ...recommendationRequestOptions, signal }),
  });
  const variants = (data ?? []).filter((variant) => variant.productId === product.productId &&
    product.compatibleVariants.some((match) => match.variantId === (variant.productVariantId ?? variant.id)));
  const [selection, setSelection] = useState("");
  const selectedId = variants.some((variant) =>
    (variant.productVariantId ?? variant.id) === selection && !posVariantSelectionError(variant))
    ? selection : initialPosVariant(variants);
  const unitQuery = useQuery({
    queryKey: ["pos-taste-unit", product.productId, selectedId], enabled: Boolean(selectedId), retry: false,
    queryFn: ({ signal }) => fetchTasteVariantUnit(product.productId, selectedId, signal),
  });
  return (
    <article className="grid gap-2 rounded-xl border border-[#dce5e1] p-4">
      <h3 className="font-bold">{product.productName}</h3>
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
      {unitQuery.data && <p className="text-sm text-slate-600">Đơn vị bán: {unitQuery.data}</p>}
      <Button type="button" size="sm" className="justify-self-start" disabled={disabled || !selectedId}
        onClick={() => onAdd(selectedId)}>
        Thêm vào hóa đơn
      </Button>
    </article>
  );
}
