import {
  CheckCircle2,
  History,
  Loader2,
  Sparkles,
  User
} from "lucide-react";
import { useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/Button";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { toast } from "@/components/ui/Toast";
import {
  canPrefillTasteSurvey,
  orderedTasteAttributes,
  tasteSurveyApi,
  type TasteAttribute,
  type TasteCriteria
} from "../services/tasteSurvey.api";
import { useGuestTasteSurvey, useSaveGuestTasteSurvey } from "../services/guestTasteSurvey";
import { ApiError } from "@/lib/api-client";
import { TasteConsultationRecommendations } from "../../product-recommendation/components/TasteConsultationRecommendations";
import type { SaleCustomer, SaleProductPreview } from "../../sales/types/sale.types";
import { TasteSurveyHistoryEntry } from "./TasteSurveyHistoryEntry";
import { TastePreferenceFields, type TastePreferenceSelection } from "./TastePreferenceFields";

interface CustomerTasteSurveyDialogProps {
  open: boolean;
  customer: SaleCustomer | null;
  invoiceId?: string;
  guestScope?: string;
  localInvoiceId?: string;
  disabled?: boolean;
  onClose(): void;
  onAddToCart?(product: SaleProductPreview): Promise<boolean>;
  onSurveySubmitted?(): void;
}

function criteriaFromSelections(selections: Record<string, TastePreferenceSelection>): TasteCriteria {
  const criteria: TasteCriteria = { hardGroups: [], softGroups: [] };
  for (const [attribute, selection] of Object.entries(selections)) {
    if (selection.notApplicable) {
      (criteria.notApplicableAttributes ??= []).push(attribute);
    } else if (selection.values.length) {
      const priorities = selection.priorities?.filter(({ value }) => selection.values.includes(value)) ?? [];
      if (selection.hard) {
        criteria.hardGroups.push({ attribute, allowedValues: selection.values });
        if (priorities.length) criteria.softGroups.push({ attribute, priorities });
      } else {
        criteria.softGroups.push({ attribute, priorities: selection.values.map((value) =>
          priorities.find((priority) => priority.value === value) ?? { value, weight: 1 }) });
      }
    }
  }
  return criteria;
}

export function CustomerTasteSurveyDialog({
  open,
  customer,
  invoiceId,
  guestScope,
  localInvoiceId,
  disabled,
  onClose,
  onAddToCart,
  onSurveySubmitted
}: CustomerTasteSurveyDialogProps) {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<"survey" | "history">("survey");

  // Load taxonomy
  const taxonomyQuery = useQuery({
    queryKey: ["current-taste-taxonomy"],
    queryFn: tasteSurveyApi.currentTaxonomy,
    retry: false,
    enabled: open,
    staleTime: 0,
    refetchOnWindowFocus: false,
  });
  const taxonomy = taxonomyQuery.isFetching || taxonomyQuery.isError ? undefined : taxonomyQuery.data;

  const customerId = customer?.id;
  const latestQuery = useQuery({
    queryKey: ["latest-customer-taste-survey", customerId],
    enabled: open && Boolean(customerId),
    retry: false,
    queryFn: async ({ signal }) => {
      if (!customerId) return null;
      try {
        return await tasteSurveyApi.latest(customerId, signal);
      } catch (error) {
        if (error instanceof ApiError && error.isNotFound) return null;
        throw error;
      }
    }
  });

  // Load survey history
  const historyQuery = useQuery({
    queryKey: ["customer-taste-surveys", customer?.id],
    queryFn: () => tasteSurveyApi.history(customer?.id ?? "", 0),
    retry: false,
    enabled: open && !!customer?.id
  });

  const guestSurvey = useGuestTasteSurvey(customer ? undefined : guestScope);
  const saveGuestSurvey = useSaveGuestTasteSurvey();
  const [draft, setDraft] = useState<{ version: string; selections: Record<string, TastePreferenceSelection> }>();
  const [submitting, setSubmitting] = useState(false);
  const [savedNotice, setSavedNotice] = useState<string | null>(null);
  const sending = useRef(false);
  const previewRef = useRef<HTMLDivElement>(null);
  const attempt = useRef<{
    input: string;
    invoiceId: string;
    keys: { create: string; submit: string };
  } | null>(null);

  const attributes = useMemo(() => orderedTasteAttributes(taxonomy), [taxonomy]);
  const taxonomyIdentity = taxonomy ? JSON.stringify([customerId ?? guestScope, localInvoiceId ?? invoiceId,
    taxonomy.versionCode, taxonomy.manifestSha256]) : "";
  const saved = customerId ? latestQuery.data : guestSurvey;
  const savedCompatible = Boolean(taxonomy && saved &&
    canPrefillTasteSurvey(taxonomy, saved.taxonomyVersion, saved.taxonomySha256));
  const selections = useMemo(() => {
    if (draft?.version === taxonomyIdentity) return draft.selections;
    const initial: Record<string, TastePreferenceSelection> = {};
    if (!taxonomy || !saved || !savedCompatible) return initial;
    const byCode = new Map(attributes.map((attribute) => [attribute.attributeCode, attribute]));
    for (const group of saved.criteria.hardGroups) {
      const attribute = byCode.get(group.attribute);
      if (!attribute) continue;
      const allowedCodes = new Set(attribute.values.map((option) => option.valueCode));
      const limit = attribute.selectionKind === "ENUM_MULTI" ? Math.min(attribute.maxSelections, 32) : 1;
      const values = group.allowedValues.filter((code) => allowedCodes.has(code)).slice(0, limit);
      if (values.length) {
        initial[group.attribute] = { values, hard: true };
      }
    }
    for (const group of saved.criteria.softGroups) {
      const attribute = byCode.get(group.attribute);
      if (!attribute) continue;
      const allowedCodes = new Set(attribute.values.map((option) => option.valueCode));
      const limit = attribute.selectionKind === "ENUM_MULTI" ? Math.min(attribute.maxSelections, 32) : 1;
      const existing = initial[group.attribute];
      if (existing) {
        const priorities = group.priorities.filter((priority) => existing.values.includes(priority.value));
        if (priorities.length) {
          initial[group.attribute] = { ...existing, priorities };
        }
      } else {
        const priorities = group.priorities.filter((priority) => allowedCodes.has(priority.value)).slice(0, limit);
        if (priorities.length) {
          initial[group.attribute] = {
            values: priorities.map(({ value }) => value),
            hard: false,
            priorities,
          };
        }
      }
    }
    for (const attributeCode of saved.criteria.notApplicableAttributes ?? []) {
      const attribute = byCode.get(attributeCode);
      if (attribute?.allowsNotApplicable && !initial[attributeCode]) {
        initial[attributeCode] = { values: [], hard: false, notApplicable: true };
      }
    }
    return initial;
  }, [attributes, draft, saved, savedCompatible, taxonomy, taxonomyIdentity]);
  const criteria = useMemo(() => criteriaFromSelections(selections), [selections]);

  function storeGuestCriteria(next: TasteCriteria) {
    if (!guestScope || !taxonomy) return false;
    saveGuestSurvey(guestScope, {
      taxonomyVersion: taxonomy.versionCode,
      taxonomySha256: taxonomy.manifestSha256,
      criteria: next,
      savedAt: new Date().toISOString()
    });
    return true;
  }

  function updateSelection(attribute: string, selection: TastePreferenceSelection) {
    const next = { ...selections, [attribute]: selection };
    setDraft({ version: taxonomyIdentity, selections: next });
    setSavedNotice(null);
    if (!customerId) storeGuestCriteria(criteriaFromSelections(next));
  }

  function toggleValue(attr: TasteAttribute, valueCode: string) {
    const current = selections[attr.attributeCode] ?? { values: [], hard: false };
    const limit = Math.min(attr.maxSelections, 32);
    let values: string[];
    if (current.values.includes(valueCode)) {
      values = current.values.filter((value) => value !== valueCode);
    } else if (attr.selectionKind !== "ENUM_MULTI" || valueCode === "NONE" || current.values.includes("NONE")) {
      values = [valueCode];
    } else if (current.values.length < limit) {
      values = [...current.values, valueCode];
    } else {
      toast.warning(`Chỉ được chọn tối đa ${limit} lựa chọn`);
      return;
    }
    updateSelection(attr.attributeCode, { ...current, values, notApplicable: false,
      priorities: current.priorities?.filter(({ value }) => values.includes(value)) });
  }

  async function handleSubmitSurvey() {
    if (!taxonomy || sending.current || disabled) return;
    if (!criteria.hardGroups.length && !criteria.softGroups.length) {
      toast.warning("Vui lòng chọn ít nhất một tiêu chí khẩu vị cho khách hàng.");
      return;
    }
    if (!customerId) {
      if (!storeGuestCriteria(criteria)) {
        toast.error("Không xác định được hóa đơn để lưu tạm khảo sát.");
        return;
      }
      const guestMessage = "Đã lưu tiêu chí trên hóa đơn hiện tại.";
      toast.success(guestMessage);
      if (!onAddToCart) {
        onClose();
        return;
      }
      setSavedNotice(guestMessage);
      previewRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
      return;
    }

    sending.current = true;
    setSubmitting(true);
    try {
      const originInvoiceId = invoiceId ?? localInvoiceId;
      if (!originInvoiceId) throw new Error("Không xác định được hóa đơn hiện tại.");
      const input = JSON.stringify([customerId, localInvoiceId ?? invoiceId,
        taxonomy.versionCode, taxonomy.manifestSha256, criteria]);
      const submission = attempt.current?.input === input ? attempt.current : {
        input,
        invoiceId: originInvoiceId,
        keys: { create: crypto.randomUUID(), submit: crypto.randomUUID() }
      };
      attempt.current = submission;
      const submitted = await tasteSurveyApi.submit({
        customerId,
        originInvoiceId: submission.invoiceId,
        taxonomyVersion: taxonomy.versionCode,
        taxonomySha256: taxonomy.manifestSha256,
        criteria
      }, submission.keys);

      const detail = await tasteSurveyApi.detail(submitted.id, customerId);
      if (detail.status !== "SUBMITTED" || detail.id !== submitted.id || detail.customerId !== customerId ||
        detail.originInvoiceId !== submission.invoiceId || detail.taxonomyVersion !== taxonomy.versionCode ||
        detail.taxonomySha256 !== taxonomy.manifestSha256 || detail.rowVersion !== submitted.rowVersion) {
        throw new Error("Khảo sát đọc lại chưa khớp với dữ liệu đã lưu.");
      }

      attempt.current = null;
      const successMessage = "Đã ghi nhận khảo sát khẩu vị khách hàng thành công!";
      toast.success(successMessage);
      setDraft({ version: taxonomyIdentity, selections });
      queryClient.setQueryData(["latest-customer-taste-survey", customerId], detail);
      void historyQuery.refetch();
      onSurveySubmitted?.();
      if (!onAddToCart) {
        onClose();
        return;
      }
      setSavedNotice(successMessage);
      previewRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Không lưu được khảo sát khẩu vị.";
      toast.error(message);
    } finally {
      sending.current = false;
      setSubmitting(false);
    }
  }

  const isNewCustomer = !customer || customer.orderCount === 0;

  return (
    <Dialog open={open} onOpenChange={(value) => {
      if (!value && !sending.current) onClose();
    }}>
      <DialogContent size="wide" className="p-0 overflow-hidden">
        <div className="flex flex-col h-[85vh] max-h-[85vh]">
          {/* Header */}
          <div className="border-b border-neutral-200 bg-neutral-50/80 px-6 py-4 shrink-0">
          <div className="flex flex-wrap items-center justify-between gap-3 pr-10">
            <div className="flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-emerald-600 text-white shadow-sm">
                <Sparkles className="h-5 w-5" />
              </div>
              <div>
                <DialogTitle className="text-lg font-bold text-neutral-900">
                  Tư vấn trà
                </DialogTitle>
                <DialogDescription className="text-xs text-neutral-500">
                  Ghi nhận sở thích và khẩu vị thưởng trà của khách hàng
                </DialogDescription>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setActiveTab("survey")}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                  activeTab === "survey"
                    ? "bg-emerald-600 text-white shadow-sm"
                    : "text-neutral-600 hover:bg-neutral-200"
                }`}
              >
                Tư vấn trà
              </button>
              {customer ? (
              <button
                type="button"
                onClick={() => setActiveTab("history")}
                className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                  activeTab === "history"
                    ? "bg-emerald-600 text-white shadow-sm"
                    : "text-neutral-600 hover:bg-neutral-200"
                }`}
              >
                <History className="h-3.5 w-3.5" />
                Lịch sử ({historyQuery.data?.totalElements ?? 0})
              </button>
              ) : null}
            </div>
          </div>

          {customer && (
          <div className="mt-3 flex flex-wrap items-center gap-2 rounded-lg border border-neutral-200 bg-white px-3 py-2 text-xs text-neutral-700">
            <User className="h-3.5 w-3.5 text-emerald-600" />
            <strong className="font-semibold text-neutral-900">{customer.name}</strong>
            <span className="text-neutral-400">·</span>
            <span>{customer.phone || customer.customerCode}</span>
            <span className="text-neutral-400">·</span>
            <span className="rounded bg-neutral-100 px-1.5 py-0.5 text-neutral-600">
              Hạng: {customer.tier || "Mặc định"}
            </span>
            <span className="text-neutral-400">·</span>
            {isNewCustomer ? (
              <span className="inline-flex items-center gap-1 rounded bg-amber-50 px-2 py-0.5 font-semibold text-amber-700 ring-1 ring-inset ring-amber-600/20">
                Khách hàng mới (0 đơn hàng)
              </span>
            ) : (
              <span className="text-neutral-500">
                Đã mua: <strong>{customer.orderCount}</strong> đơn hàng
              </span>
            )}
          </div>
          )}
        </div>

        {/* Content Body */}
        {activeTab === "survey" ? (
          <div className="flex-1 min-h-0 overflow-y-auto p-6 space-y-6">
                <div className="space-y-4 rounded-xl border border-neutral-200 bg-white p-4 shadow-sm">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-bold uppercase tracking-wider text-neutral-700">
                      Tiêu chí khẩu vị ưa thích
                    </label>
                  </div>

                  {taxonomyQuery.isFetching && (
                    <div className="flex items-center justify-center py-6 text-xs text-neutral-500">
                      <Loader2 className="mr-2 h-4 w-4 animate-spin text-emerald-600" />
                      Đang tải bộ tiêu chí khẩu vị...
                    </div>
                  )}

                  {taxonomyQuery.isError && <p role="alert" className="text-sm text-amber-900">
                    Không tải được bộ tiêu chí khẩu vị.
                  </p>}
                  {latestQuery.isError && <p role="alert" className="text-sm text-amber-900">
                    Không tải được khảo sát gần nhất của khách hàng.
                  </p>}
                  {taxonomy && saved && !savedCompatible && <p role="status" className="text-sm text-amber-900">
                    Khảo sát trước dùng tiêu chí chưa xác nhận tương thích. Vui lòng chọn lại khẩu vị.
                  </p>}
                  {taxonomy && draft && draft.version !== taxonomyIdentity && <p role="status" className="text-sm text-amber-900">
                    Bộ tiêu chí hoặc hóa đơn đã thay đổi. Vui lòng kiểm tra lựa chọn trước khi lưu.
                  </p>}
                  <TastePreferenceFields attributes={attributes} selections={selections}
                    updateSelection={updateSelection} toggleValue={toggleValue} />
                </div>
                <div ref={previewRef} className="space-y-3">
                  {savedNotice && (
                    <p
                      role="status"
                      className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-xs font-semibold text-emerald-900"
                    >
                      <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" />
                      <span>{savedNotice} Kết quả tư vấn khẩu vị bên dưới vẫn đang được giữ nguyên.</span>
                    </p>
                  )}
                  <TasteConsultationRecommendations taxonomy={taxonomy} criteria={criteria}
                    disabled={disabled || submitting} onAdd={onAddToCart} />
                </div>
              </div>
        ) : (
          /* History Tab */
          <div className="flex-1 min-h-0 overflow-y-auto p-6 space-y-4">
            <h3 className="text-sm font-bold text-neutral-900">
              Lịch sử khảo sát khẩu vị của {customer?.name}
            </h3>

            {historyQuery.isLoading ? (
              <div className="flex items-center justify-center py-8 text-xs text-neutral-500">
                <Loader2 className="mr-2 h-4 w-4 animate-spin text-emerald-600" />
                Đang tải lịch sử khảo sát...
              </div>
            ) : !historyQuery.data?.content || historyQuery.data.content.length === 0 ? (
              <div className="py-8 text-center text-sm text-neutral-500">
                Khách hàng này chưa có khảo sát nào trước đây.
              </div>
            ) : (
              <div className="space-y-3">
                {historyQuery.data.content.map((survey) =>
                  <TasteSurveyHistoryEntry key={survey.id} survey={survey} current={taxonomy} />)}
              </div>
            )}
          </div>
        )}

        {/* Sticky Footer */}
        <div className="flex items-center justify-end gap-3 border-t border-neutral-200 bg-neutral-50 px-6 py-3 shrink-0">
          <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>
            Đóng
          </Button>
          {activeTab === "survey" && (
            <Button
              type="button"
              variant="primary"
              onClick={() => void handleSubmitSurvey()}
              disabled={submitting || disabled || !taxonomy}
            >
              {submitting ? (
                <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
              ) : (
                <CheckCircle2 className="mr-1.5 h-4 w-4" />
              )}
              {isNewCustomer ? "Lưu khảo sát khách mới" : "Cập nhật khảo sát"}
            </Button>
          )}
        </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
