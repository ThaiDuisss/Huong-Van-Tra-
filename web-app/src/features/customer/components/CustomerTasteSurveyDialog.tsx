import {
  CheckCircle2,
  History,
  Loader2,
  Sparkles,
  User
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { Button } from "@/components/ui/Button";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { toast } from "@/components/ui/Toast";
import {
  tasteSurveyApi,
  type TasteAttribute,
  type TasteCriteria,
  type TasteSurvey
} from "../services/tasteSurvey.api";
import { PREPARATION_CODE_OPTIONS } from "../../product/types/variantTaste.types";
import type { SaleCustomer, SaleProductPreview } from "../../sales/types/sale.types";

interface CustomerTasteSurveyDialogProps {
  open: boolean;
  customer: SaleCustomer;
  invoiceId?: string;
  onClose(): void;
  onAddToCart?(product: SaleProductPreview): Promise<boolean>;
  onSurveySubmitted?(): void;
}

interface Selection {
  values: string[];
  hard: boolean;
  notApplicable?: boolean;
}

export function CustomerTasteSurveyDialog({
  open,
  customer,
  invoiceId,
  onClose,
  onSurveySubmitted
}: CustomerTasteSurveyDialogProps) {
  const [activeTab, setActiveTab] = useState<"survey" | "history">("survey");

  // Load taxonomy
  const taxonomyQuery = useQuery({
    queryKey: ["current-taste-taxonomy"],
    queryFn: tasteSurveyApi.currentTaxonomy,
    retry: false,
    enabled: open
  });
  const taxonomy = taxonomyQuery.data;

  // Load survey history
  const historyQuery = useQuery({
    queryKey: ["customer-taste-surveys", customer.id],
    queryFn: () => tasteSurveyApi.history(customer.id, 0),
    retry: false,
    enabled: open && !!customer.id
  });

  const [preparationCode, setPreparationCode] = useState<string>("DAILY");
  const [selections, setSelections] = useState<Record<string, Selection>>({});
  const [submitting, setSubmitting] = useState(false);
  const sending = useRef(false);
  const edited = useRef(false);
  const attempt = useRef<{
    input: string;
    invoiceId: string;
    keys: { create: string; submit: string };
  } | null>(null);

  const attributes = useMemo(() => {
    if (!taxonomy?.manifest?.attributes) return [];
    return [...taxonomy.manifest.attributes].sort((a, b) => a.displayOrder - b.displayOrder);
  }, [taxonomy]);

  // Pre-fill latest survey when available
  useEffect(() => {
    if (!taxonomy || edited.current) return;
    const latest = historyQuery.data?.content.find((survey) => survey.status === "SUBMITTED" &&
      survey.taxonomyVersion === taxonomy.versionCode && survey.taxonomySha256 === taxonomy.manifestSha256);
    if (latest) {
      const initialSel: Record<string, Selection> = {};
      latest.criteria.hardGroups?.forEach((g) => {
        initialSel[g.attribute] = { values: g.allowedValues, hard: true };
      });
      latest.criteria.softGroups?.forEach((g) => {
        const vals = g.priorities?.map((p) => p.value) || [];
        if (!initialSel[g.attribute]) {
          initialSel[g.attribute] = { values: vals, hard: false };
        }
      });
      latest.criteria.notApplicableAttributes?.forEach((attr) => {
        initialSel[attr] = { values: [], hard: false, notApplicable: true };
      });
      setSelections(initialSel);
    }
  }, [historyQuery.data, taxonomy]);

  function toggleValue(attr: TasteAttribute, valueCode: string) {
    edited.current = true;
    setSelections((prev) => {
      const current = prev[attr.attributeCode] || { values: [], hard: false };
      const isMulti = attr.selectionKind === "ENUM_MULTI";
      const limit = Math.min(attr.maxSelections, 32);

      let nextValues: string[];
      if (current.values.includes(valueCode)) {
        nextValues = current.values.filter((v) => v !== valueCode);
      } else if (!isMulti || valueCode === "NONE" || current.values.includes("NONE")) {
        nextValues = [valueCode];
      } else if (current.values.length < limit) {
        nextValues = [...current.values, valueCode];
      } else {
        toast.warning(`Chỉ được chọn tối đa ${limit} lựa chọn`);
        return prev;
      }

      return {
        ...prev,
        [attr.attributeCode]: {
          ...current,
          values: nextValues,
          notApplicable: false
        }
      };
    });
  }

  function toggleHard(attrCode: string, hard: boolean) {
    edited.current = true;
    setSelections((prev) => {
      const current = prev[attrCode] || { values: [], hard: false };
      return {
        ...prev,
        [attrCode]: { ...current, hard }
      };
    });
  }

  // Handle submit survey
  async function handleSubmitSurvey() {
    if (!taxonomy || sending.current) return;

    const criteria: TasteCriteria = { hardGroups: [], softGroups: [] };
    const notApplicableAttributes: string[] = [];

    Object.entries(selections).forEach(([attr, sel]) => {
      if (sel.notApplicable) {
        notApplicableAttributes.push(attr);
      } else if (sel.values.length > 0) {
        if (sel.hard) {
          criteria.hardGroups.push({ attribute: attr, allowedValues: sel.values });
        } else {
          criteria.softGroups.push({
            attribute: attr,
            priorities: sel.values.map((v) => ({ value: v, weight: 1 }))
          });
        }
      }
    });

    if (notApplicableAttributes.length > 0) {
      criteria.notApplicableAttributes = notApplicableAttributes;
    }

    const hasAnySelection = criteria.hardGroups.length > 0 || criteria.softGroups.length > 0;
    if (!hasAnySelection) {
      toast.warning("Vui lòng chọn ít nhất một tiêu chí khẩu vị cho khách hàng.");
      return;
    }

    sending.current = true;
    setSubmitting(true);
    try {
      const input = JSON.stringify([customer.id, invoiceId, taxonomy.versionCode, taxonomy.manifestSha256, criteria]);
      const submission = attempt.current?.input === input ? attempt.current : {
        input,
        invoiceId: invoiceId || crypto.randomUUID(),
        keys: { create: crypto.randomUUID(), submit: crypto.randomUUID() }
      };
      attempt.current = submission;
      await tasteSurveyApi.submit(
        {
          customerId: customer.id,
          originInvoiceId: submission.invoiceId,
          taxonomyVersion: taxonomy.versionCode,
          taxonomySha256: taxonomy.manifestSha256,
          criteria
        },
        submission.keys
      );

      attempt.current = null;
      toast.success("Đã ghi nhận khảo sát khẩu vị khách hàng thành công!");
      void historyQuery.refetch();
      onSurveySubmitted?.();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Không lưu được khảo sát khẩu vị.";
      toast.error(msg);
    } finally {
      sending.current = false;
      setSubmitting(false);
    }
  }

  const isNewCustomer = customer.orderCount === 0;

  return (
    <Dialog open={open} onOpenChange={(val) => !val && onClose()}>
      <DialogContent className="max-w-2xl p-0 overflow-hidden">
        <div className="flex flex-col h-[85vh] max-h-[85vh]">
          {/* Header */}
          <div className="border-b border-neutral-200 bg-neutral-50/80 px-6 py-4 shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-emerald-600 text-white shadow-sm">
                <Sparkles className="h-5 w-5" />
              </div>
              <div>
                <DialogTitle className="text-lg font-bold text-neutral-900">
                  Khảo sát khẩu vị
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
                Khảo sát khẩu vị
              </button>
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
            </div>
          </div>

          {/* Customer info pill */}
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
        </div>

        {/* Content Body */}
        {activeTab === "survey" ? (
          <div className="flex-1 min-h-0 overflow-y-auto p-6 space-y-6">
            {/* 1. Preparation / Purpose */}
            <div className="rounded-xl border border-neutral-200 bg-white p-4 shadow-sm">
                  <label className="block text-xs font-bold uppercase tracking-wider text-neutral-700 mb-2">
                    1. Cách thức thưởng thức / Mục đích sử dụng
                  </label>
                  <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                    {PREPARATION_CODE_OPTIONS.map((opt) => {
                      const isSelected = preparationCode === opt.value;
                      return (
                        <button
                          key={opt.value}
                          type="button"
                          onClick={() => setPreparationCode(opt.value)}
                          className={`flex flex-col items-start gap-1 rounded-lg border p-2.5 text-left transition ${
                            isSelected
                              ? "border-emerald-600 bg-emerald-50 text-emerald-900 ring-1 ring-emerald-600"
                              : "border-neutral-200 bg-white text-neutral-700 hover:border-emerald-300"
                          }`}
                        >
                          <span className="text-xs font-bold">{opt.label}</span>
                          <span className="line-clamp-2 text-[10px] text-neutral-500">
                            {opt.description}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* 2. Sensory Attributes */}
                <div className="space-y-4 rounded-xl border border-neutral-200 bg-white p-4 shadow-sm">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-bold uppercase tracking-wider text-neutral-700">
                      2. Tiêu chí khẩu vị ưa thích
                    </label>
                    {taxonomy && (
                      <span className="text-[11px] text-neutral-400">
                        Phiên bản {taxonomy.versionCode}
                      </span>
                    )}
                  </div>

                  {taxonomyQuery.isLoading && (
                    <div className="flex items-center justify-center py-6 text-xs text-neutral-500">
                      <Loader2 className="mr-2 h-4 w-4 animate-spin text-emerald-600" />
                      Đang tải bộ tiêu chí khẩu vị...
                    </div>
                  )}

                  {attributes.map((attr) => {
                    const sel = selections[attr.attributeCode] || { values: [], hard: false };
                    const isMulti = attr.selectionKind === "ENUM_MULTI";

                    return (
                      <div
                        key={attr.attributeCode}
                        className="rounded-lg border border-neutral-100 bg-neutral-50/50 p-3"
                      >
                        <div className="flex items-center justify-between mb-2">
                          <span className="text-xs font-semibold text-neutral-800">
                            {attr.label}
                            {isMulti && (
                              <span className="ml-1 text-[11px] font-normal text-neutral-400">
                                (tối đa {attr.maxSelections})
                              </span>
                            )}
                          </span>

                          {sel.values.length > 0 && (
                            <label className="flex items-center gap-1.5 text-xs text-neutral-600 cursor-pointer">
                              <input
                                type="checkbox"
                                checked={sel.hard}
                                onChange={(e) => toggleHard(attr.attributeCode, e.target.checked)}
                                className="rounded text-emerald-600 focus:ring-emerald-500"
                              />
                              <span className="text-[11px] font-medium text-emerald-700">Bắt buộc</span>
                            </label>
                          )}
                        </div>

                        <div className="flex flex-wrap gap-1.5">
                          {attr.values.map((val) => {
                            const isChosen = sel.values.includes(val.valueCode);
                            return (
                              <button
                                key={val.valueCode}
                                type="button"
                                onClick={() => toggleValue(attr, val.valueCode)}
                                className={`rounded-md px-2.5 py-1 text-xs font-medium transition ${
                                  isChosen
                                    ? "bg-emerald-600 text-white shadow-xs"
                                    : "bg-white text-neutral-700 border border-neutral-200 hover:bg-neutral-100"
                                }`}
                              >
                                {val.label}
                              </button>
                            );
                          })}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
        ) : (
          /* History Tab */
          <div className="flex-1 min-h-0 overflow-y-auto p-6 space-y-4">
            <h3 className="text-sm font-bold text-neutral-900">
              Lịch sử khảo sát khẩu vị của {customer.name}
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
                {historyQuery.data.content.map((survey: TasteSurvey) => {
                  const dateStr = survey.submittedAt
                    ? new Date(survey.submittedAt).toLocaleString("vi-VN")
                    : "Chưa ghi nhận ngày";
                  return (
                    <div
                      key={survey.id}
                      className="rounded-xl border border-neutral-200 bg-white p-4 shadow-xs"
                    >
                      <div className="flex items-center justify-between border-b border-neutral-100 pb-2">
                        <span className="text-xs font-semibold text-neutral-800">
                          Phiên bản: {survey.taxonomyVersion}
                        </span>
                        <span className="text-xs text-neutral-400">{dateStr}</span>
                      </div>

                      <div className="mt-3 space-y-2 text-xs">
                        {survey.criteria.hardGroups?.length > 0 && (
                          <div>
                            <span className="font-semibold text-emerald-800">Tiêu chí bắt buộc:</span>
                            <ul className="mt-1 list-disc pl-5 text-neutral-600">
                              {survey.criteria.hardGroups.map((g) => (
                                <li key={g.attribute}>
                                  <strong>{g.attribute}:</strong> {g.allowedValues.join(", ")}
                                </li>
                              ))}
                            </ul>
                          </div>
                        )}

                        {survey.criteria.softGroups?.length > 0 && (
                          <div>
                            <span className="font-semibold text-neutral-800">Tiêu chí ưu tiên:</span>
                            <ul className="mt-1 list-disc pl-5 text-neutral-600">
                              {survey.criteria.softGroups.map((g) => (
                                <li key={g.attribute}>
                                  <strong>{g.attribute}:</strong>{" "}
                                  {g.priorities.map((p) => p.value).join(", ")}
                                </li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
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
              disabled={submitting}
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
