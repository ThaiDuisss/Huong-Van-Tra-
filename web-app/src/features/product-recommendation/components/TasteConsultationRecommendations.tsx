import { useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/Button";
import { toast } from "@/components/ui/Toast";
import { orderedTasteAttributes, type TasteCriteria, type TasteTaxonomy } from "../../customer/services/tasteSurvey.api";
import type { SaleProductPreview } from "../../sales/types/sale.types";
import { PosProductSelectionError } from "../../sales/services/posProductSelection";
import { resolveRecommendedVariant } from "../services/productRecommendation.api";
import { fetchTastePreview, tastePreviewError } from "../services/tastePreview.api";
import { TasteResultCard } from "./TastePreviewLauncher";

type Preview = Awaited<ReturnType<typeof fetchTastePreview>>;

export function TasteConsultationRecommendations({ taxonomy, criteria, disabled, onAdd }: {
  taxonomy?: TasteTaxonomy;
  criteria: TasteCriteria;
  disabled?: boolean;
  onAdd?(product: SaleProductPreview): Promise<boolean>;
}) {
  const sequence = useRef(0);
  const input = useMemo(() => {
    if (!taxonomy || (!criteria.hardGroups.length && !criteria.softGroups.length)) return null;
    return { taxonomyVersion: taxonomy.versionCode, taxonomySha256: taxonomy.manifestSha256, ...criteria, limit: 5 };
  }, [criteria, taxonomy]);
  const [retry, setRetry] = useState(0);
  const [outcome, setOutcome] = useState<{
    input: NonNullable<typeof input>; retry: number; result?: Preview; error?: string;
  }>();

  const current = outcome?.input === input && outcome?.retry === retry ? outcome : undefined;
  useEffect(() => {
    if (!input) return;
    const controller = new AbortController();
    const requestSequence = ++sequence.current;
    const timer = window.setTimeout(async () => {
      try {
        const result = await fetchTastePreview({ ...input, requestSequence }, controller.signal);
        if (!controller.signal.aborted) setOutcome({ input, retry, result });
      } catch (error) {
        if (!controller.signal.aborted) setOutcome({ input, retry, error: tastePreviewError(error) });
      }
    }, 400);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [input, retry]);

  const result = current?.result;
  return (
    <section aria-label="Sản phẩm phù hợp với khẩu vị"
      className="grid gap-3 rounded-xl border border-neutral-200 bg-white p-4 shadow-sm">
      <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-700">
        Sản phẩm phù hợp với khẩu vị
      </h3>
      {!input && <p className="text-sm text-neutral-600">Chọn tiêu chí khẩu vị để xem sản phẩm phù hợp.</p>}
      {input && !current && <p role="status" className="text-sm">Đang tìm sản phẩm phù hợp…</p>}
      {current?.error && <div role="alert" className="flex items-center gap-3 text-sm text-amber-900">
        <span>{current.error}</span>
        <Button type="button" size="sm" variant="secondary" onClick={() => setRetry((value) => value + 1)}>Thử lại</Button>
      </div>}
      {result?.status === "NO_MATCH" && <p role="status" className="text-sm">Không có sản phẩm phù hợp với các tiêu chí đã chọn.</p>}
      {result?.status === "NO_CRITERIA" && <p role="status" className="text-sm">Chọn tiêu chí khẩu vị để xem sản phẩm phù hợp.</p>}
      {result?.status === "TASTE_PROFILE_NOT_READY" &&
        <p role="status" className="text-sm">Chưa có sản phẩm còn tồn với hồ sơ khẩu vị đủ điều kiện.</p>}
      {result?.status === "TASTE_CATALOG_UNAVAILABLE" &&
        <p role="alert" className="text-sm">Dữ liệu tư vấn chưa sẵn sàng. Vui lòng thử lại sau.</p>}
      {result?.status === "TAXONOMY_MAPPING_REQUIRED" &&
        <p role="alert" className="text-sm">Bộ tiêu chí đã thay đổi. Vui lòng mở lại tư vấn để tải bản hiện hành.</p>}
      {result?.status === "INSUFFICIENT_MATCHES" &&
        <p role="status" className="text-sm">Tìm thấy {result.items.length} sản phẩm phù hợp.</p>}
      {result?.items.length ? <ConsultationResults key={result.requestSequence} result={result}
        taxonomy={taxonomy} disabled={disabled} onAdd={onAdd} /> : null}
    </section>
  );
}

function ConsultationResults({ result, taxonomy, disabled, onAdd }: {
  result: Preview;
  taxonomy?: TasteTaxonomy;
  disabled?: boolean;
  onAdd?(product: SaleProductPreview): Promise<boolean>;
}) {
  const queryClient = useQueryClient();
  const addRequest = useRef<AbortController | null>(null);
  const [pendingProductId, setPendingProductId] = useState<string | null>(null);

  useEffect(() => () => addRequest.current?.abort(), []);

  async function add(productId: string, variantId: string, sku: string) {
    if (!onAdd || disabled || addRequest.current) return;
    const controller = new AbortController();
    addRequest.current = controller;
    setPendingProductId(productId);
    const toastKey = `taste-invoice-add:${variantId}`;
    function showError(message: string) {
      toast.error({ key: toastKey, title: "Không thêm được vào hóa đơn", message });
    }
    try {
      const product = await resolveRecommendedVariant(productId, variantId, controller.signal);
      if (controller.signal.aborted) return;
      if (product.sku !== sku) {
        showError("Thông tin biến thể đã thay đổi. Vui lòng chọn lại sản phẩm.");
        return;
      }
      const name = product.variant || product.name;
      if ((product.availableQuantity ?? 0) <= 0) {
        showError(`${name} vừa hết hàng. Vui lòng chọn biến thể khác.`);
        return;
      }
      const added = await onAdd(product);
      if (added) {
        toast.success({
          key: toastKey,
          title: "Thêm vào hóa đơn thành công",
          message: `Đã thêm ${name} vào hóa đơn.`,
        }, 3000);
      } else if (!controller.signal.aborted) {
        showError("Hóa đơn hiện tại chưa thể thêm hàng. Vui lòng thử lại.");
      }
    } catch (failure) {
      if (!controller.signal.aborted) {
        showError(failure instanceof PosProductSelectionError ? failure.message :
          "Không xác minh được thông tin bán hàng hiện tại. Vui lòng thử lại.");
      }
    } finally {
      addRequest.current = null;
      if (!controller.signal.aborted) {
        setPendingProductId(null);
        void queryClient.invalidateQueries({ queryKey: ["pos-taste-variants", productId] });
      }
    }
  }

  const topScore = result.items[0]?.score;
  const topTied = topScore !== undefined && result.items[1]?.score === topScore;
  const attributes = orderedTasteAttributes(taxonomy);
  return <div className="grid gap-3">
    {result.items.map((product, index) => <TasteResultCard key={product.productId} product={product}
      attributes={attributes} requireAvailable
      rank={index + 1} topTied={topTied} adding={pendingProductId === product.productId}
      disabled={disabled || pendingProductId !== null || !onAdd}
      onAdd={(variantId) => {
        const variant = product.compatibleVariants.find((match) => match.variantId === variantId);
        if (variant) void add(product.productId, variantId, variant.sku);
      }} />)}
  </div>;
}
