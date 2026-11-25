import {
  Coffee,
  RefreshCw,
  Search,
  Sparkles,
  User,
  Users
} from "lucide-react";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { Button } from "@/components/ui/Button";
import { customerApi } from "../services/customer.api";
import { tasteSurveyApi } from "../services/tasteSurvey.api";
import { CustomerTasteSurveyDialog } from "../components/CustomerTasteSurveyDialog";
import type { CustomerResponse } from "../types/customer.types";
import type { SaleCustomer } from "../../sales/types/sale.types";
import { toSaleCustomer } from "../../sales/services/posCustomer.api";

export function CustomerTasteSurveyPage() {
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedCustomer, setSelectedCustomer] = useState<SaleCustomer | null>(null);
  const [surveyDialogOpen, setSurveyDialogOpen] = useState(false);

  // Search customers
  const { data: customerPage, isLoading: searching } = useQuery({
    queryKey: ["survey-customer-search", searchTerm],
    queryFn: () =>
      customerApi.list({
        search: searchTerm.trim() || undefined,
        page: 0,
        size: 15
      }),
    enabled: true
  });

  // Selected customer survey history
  const {
    data: surveyHistory,
    isLoading: historyLoading,
    refetch: refetchHistory
  } = useQuery({
    queryKey: ["customer-survey-history-page", selectedCustomer?.id],
    queryFn: () =>
      selectedCustomer?.id ? tasteSurveyApi.history(selectedCustomer.id, 0) : null,
    enabled: !!selectedCustomer?.id
  });

  const customers = customerPage?.content || [];
  const hasHistory = (surveyHistory?.totalElements || 0) > 0;
  const isNewCustomer = selectedCustomer ? selectedCustomer.orderCount === 0 || !hasHistory : false;

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold tracking-tight text-neutral-900">
            Khảo sát Khẩu vị Khách hàng
          </h1>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
            <Sparkles className="h-3.5 w-3.5" />
            Tư vấn cá nhân hóa
          </span>
        </div>
        <p className="mt-1 text-sm text-neutral-500">
          Thu thập sở thích vị trà, độ ngọt, nốt hương và mục đích thưởng thức để đưa ra đề xuất trà chuẩn xác nhất cho khách hàng.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-12">
        {/* Left: Customer Selection & Search */}
        <div className="space-y-4 lg:col-span-5">
          <div className="rounded-xl border border-neutral-200 bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between pb-3 border-b border-neutral-100">
              <div className="flex items-center gap-2">
                <Users className="h-4 w-4 text-emerald-600" />
                <h2 className="text-xs font-bold uppercase tracking-wider text-neutral-700">
                  Chọn khách hàng
                </h2>
              </div>
            </div>

            <div className="relative mt-3">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
              <input
                type="text"
                placeholder="Tìm theo tên, SĐT hoặc mã khách hàng..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full rounded-lg border border-neutral-200 bg-white py-2 pl-9 pr-3 text-sm outline-none focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600"
              />
            </div>

            <div className="mt-3 max-h-[500px] space-y-2 overflow-y-auto">
              {searching ? (
                <div className="py-8 text-center text-xs text-neutral-500">
                  <RefreshCw className="mx-auto mb-2 h-4 w-4 animate-spin text-emerald-600" />
                  Đang tìm kiếm khách hàng...
                </div>
              ) : customers.length === 0 ? (
                <div className="py-8 text-center text-xs text-neutral-500">
                  Không tìm thấy khách hàng nào.
                </div>
              ) : (
                customers.map((c: CustomerResponse) => {
                  const saleCust = toSaleCustomer(c);
                  const isSelected = selectedCustomer?.id === saleCust.id;
                  return (
                    <button
                      key={saleCust.id}
                      type="button"
                      onClick={() => setSelectedCustomer(saleCust)}
                      className={`w-full flex items-center justify-between rounded-lg border p-3 text-left transition ${
                        isSelected
                          ? "border-emerald-600 bg-emerald-50 text-emerald-900 ring-1 ring-emerald-600"
                          : "border-neutral-200 bg-white hover:border-emerald-300"
                      }`}
                    >
                      <div className="min-w-0 flex-1">
                        <div className="font-semibold text-sm text-neutral-900 truncate">
                          {saleCust.name}
                        </div>
                        <div className="text-xs text-neutral-500">
                          {saleCust.phone || saleCust.customerCode}
                        </div>
                      </div>
                      <div className="text-right">
                        <span className="inline-block rounded bg-neutral-100 px-2 py-0.5 text-[11px] font-medium text-neutral-600">
                          {saleCust.tier || "Mặc định"}
                        </span>
                        <div className="mt-1 text-[11px] text-neutral-400">
                          {saleCust.orderCount} đơn hàng
                        </div>
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>
        </div>

        {/* Right: Customer Taste Details & Actions */}
        <div className="space-y-4 lg:col-span-7">
          {selectedCustomer ? (
            <div className="rounded-xl border border-neutral-200 bg-white p-6 shadow-sm space-y-6">
              {/* Customer summary */}
              <div className="flex items-center justify-between border-b border-neutral-100 pb-4">
                <div className="flex items-center gap-3">
                  <div className="grid h-12 w-12 place-items-center rounded-full bg-emerald-100 text-emerald-700">
                    <User className="h-6 w-6" />
                  </div>
                  <div>
                    <h2 className="text-lg font-bold text-neutral-900">
                      {selectedCustomer.name}
                    </h2>
                    <p className="text-xs text-neutral-500">
                      SĐT: {selectedCustomer.phone || "—"} · Mã: {selectedCustomer.customerCode || "—"}
                    </p>
                  </div>
                </div>

                <Button
                  type="button"
                  variant="primary"
                  onClick={() => setSurveyDialogOpen(true)}
                >
                  <Sparkles className="mr-1.5 h-4 w-4" />
                  {isNewCustomer ? "Khảo sát khẩu vị" : "Cập nhật khẩu vị"}
                </Button>
              </div>

              {/* Status Banner */}
              {isNewCustomer ? (
                <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-4 text-sm text-amber-900">
                  <div className="flex items-center gap-2 font-semibold">
                    <Sparkles className="h-4 w-4 text-amber-600" />
                    Khách hàng mới / Chưa có hồ sơ khẩu vị
                  </div>
                  <p className="mt-1 text-xs text-amber-800">
                    Khách hàng này chưa từng hoàn thành khảo sát khẩu vị hoặc chưa mua đơn nào ({selectedCustomer.orderCount} đơn).
                    Hãy thực hiện khảo sát nhanh để tìm kiếm loại trà phù hợp nhất!
                  </p>
                </div>
              ) : (
                <div className="rounded-xl border border-emerald-200 bg-emerald-50/70 p-4 text-sm text-emerald-900">
                  <div className="flex items-center gap-2 font-semibold">
                    <Sparkles className="h-4 w-4 text-emerald-600" />
                    Đã có hồ sơ khẩu vị
                  </div>
                  <p className="mt-1 text-xs text-emerald-800">
                    Khách hàng đã có {surveyHistory?.totalElements} bản khảo sát trước đây. Bạn có thể nhấn &quot;Cập nhật khẩu vị&quot; để ghi nhận lại nếu sở thích của khách thay đổi.
                  </p>
                </div>
              )}

              {/* History list preview */}
              <div>
                <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-700 mb-3">
                  Lịch sử các lần khảo sát
                </h3>

                {historyLoading ? (
                  <div className="py-6 text-center text-xs text-neutral-500">
                    Đang tải lịch sử...
                  </div>
                ) : !surveyHistory?.content || surveyHistory.content.length === 0 ? (
                  <div className="rounded-lg border border-neutral-100 bg-neutral-50 p-6 text-center text-xs text-neutral-500">
                    Chưa có lịch sử khảo sát nào được lưu.
                  </div>
                ) : (
                  <div className="space-y-3">
                    {surveyHistory.content.map((survey) => (
                      <div
                        key={survey.id}
                        className="rounded-lg border border-neutral-200 bg-neutral-50/50 p-3 text-xs space-y-1.5"
                      >
                        <div className="flex items-center justify-between font-semibold text-neutral-800">
                          <span>Phiên bản: {survey.taxonomyVersion}</span>
                          <span className="text-[11px] font-normal text-neutral-400">
                            {survey.submittedAt ? new Date(survey.submittedAt).toLocaleDateString("vi-VN") : "—"}
                          </span>
                        </div>
                        {survey.criteria.hardGroups?.length > 0 && (
                          <div className="text-neutral-600">
                            <strong>Bắt buộc:</strong>{" "}
                            {survey.criteria.hardGroups.map((g) => `${g.attribute} (${g.allowedValues.join(", ")})`).join("; ")}
                          </div>
                        )}
                        {survey.criteria.softGroups?.length > 0 && (
                          <div className="text-neutral-600">
                            <strong>Ưu tiên:</strong>{" "}
                            {survey.criteria.softGroups.map((g) => `${g.attribute} (${g.priorities.map((p) => p.value).join(", ")})`).join("; ")}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="rounded-xl border border-neutral-200 bg-white p-12 text-center shadow-sm">
              <Coffee className="mx-auto h-12 w-12 text-neutral-300" />
              <h3 className="mt-3 text-sm font-semibold text-neutral-800">
                Chưa chọn khách hàng
              </h3>
              <p className="mt-1 text-xs text-neutral-500">
                Hãy chọn một khách hàng từ danh sách bên trái để xem hồ sơ khẩu vị hoặc thực hiện khảo sát mới.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Survey Dialog */}
      {selectedCustomer && surveyDialogOpen && (
        <CustomerTasteSurveyDialog
          key={selectedCustomer.id}
          open={surveyDialogOpen}
          customer={selectedCustomer}
          onClose={() => setSurveyDialogOpen(false)}
          onSurveySubmitted={() => {
            void refetchHistory();
          }}
        />
      )}
    </div>
  );
}
