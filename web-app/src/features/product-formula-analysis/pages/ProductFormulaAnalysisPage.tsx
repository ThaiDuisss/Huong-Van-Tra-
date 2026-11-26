import {
  Boxes,
  Download,
  FileSpreadsheet,
  Layers,
  Package,
  Sparkles,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { TablePagination } from "../../../components/shared/list/TablePagination";
import { Button } from "../../../components/ui/Button";
import { Field, TextInput } from "../../../components/ui/Field";
import { DashboardSection } from "../../dashboard/components/DashboardSection";
import { RankList } from "../../dashboard/components/RankList";
import { InventoryPageHeader } from "../../inventory/components/InventoryManagementComponents";
import { formatVietnamNumber } from "../../inventory/utils/inventory-format";
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import { useLookupDropdown } from "../../manufacturing/components/shared/useLookupDropdown";
import { AiFormulaProposalSection } from "../components/AiFormulaProposalSection";
import {
  type IngredientAnalysisReportResponse,
  type ProductFormulaReportResponse,
  productFormulaAnalysisApi,
} from "../services/productFormulaAnalysis.api";
import type {
  AiFormulaProposal,
  AnalysisFilters,
  AnalysisTab,
  ProposalStatus,
} from "../types/product-formula-analysis.types";
import {
  buildAiFormulaProposals,
  exportProductFormulaExcel,
} from "../utils/formulaAnalysisUtils";

function toIsoDate(date: Date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function getPresetRange(days: number) {
  const end = new Date();
  const start = new Date(end);
  start.setDate(end.getDate() - (days - 1));
  return { fromDate: toIsoDate(start), toDate: toIsoDate(end) };
}

export function ProductFormulaAnalysisPage() {
  const [activeTab, setActiveTab] = useState<AnalysisTab>("overview");
  const [filters, setFilters] = useState<AnalysisFilters>(() => ({
    ...getPresetRange(30),
    categoryId: "",
    productId: "",
  }));

  const categoryDropdown = useLookupDropdown(productFormulaAnalysisApi.lookupCategories, { lazy: true });
  const productDropdown = useLookupDropdown(productFormulaAnalysisApi.lookupProducts, { lazy: true });

  const [formulaPage, setFormulaPage] = useState(0);
  const [formulaPageSize, setFormulaPageSize] = useState(10);
  const [ingredientPage, setIngredientPage] = useState(0);
  const [ingredientPageSize, setIngredientPageSize] = useState(10);

  const [formulaReport, setFormulaReport] = useState<ProductFormulaReportResponse | null>(null);
  const [ingredientReport, setIngredientReport] = useState<IngredientAnalysisReportResponse | null>(null);
  const [exporting, setExporting] = useState(false);
  const [proposalStatuses, setProposalStatuses] = useState<Record<string, ProposalStatus>>({});

  const updateFilters = useCallback((updater: (prev: AnalysisFilters) => AnalysisFilters) => {
    setFilters(updater);
    setFormulaPage(0);
    setIngredientPage(0);
  }, []);

  const loadFormulaReport = useCallback(async () => {
    if (filters.fromDate && filters.toDate && filters.fromDate > filters.toDate) {
      return;
    }
    const res = await productFormulaAnalysisApi.getProductFormulaReport(
      filters,
      formulaPage,
      formulaPageSize,
    );
    setFormulaReport(res);
  }, [filters, formulaPage, formulaPageSize]);

  const loadIngredientReport = useCallback(async () => {
    if (filters.fromDate && filters.toDate && filters.fromDate > filters.toDate) {
      return;
    }
    const res = await productFormulaAnalysisApi.getIngredientAnalysisReport(
      filters,
      ingredientPage,
      ingredientPageSize,
    );
    setIngredientReport(res);
  }, [filters, ingredientPage, ingredientPageSize]);

  useEffect(() => {
    if (activeTab !== "overview" && activeTab !== "ai-recommend") return;
    const timer = window.setTimeout(() => {
      void loadFormulaReport();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [activeTab, loadFormulaReport]);

  useEffect(() => {
    if (activeTab !== "ingredients" && activeTab !== "ai-recommend") return;
    const timer = window.setTimeout(() => {
      void loadIngredientReport();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [activeTab, loadIngredientReport]);

  const topProducts = useMemo(() => formulaReport?.topProducts ?? [], [formulaReport]);
  const pagedBomRecords = useMemo(() => formulaReport?.items.content ?? [], [formulaReport]);
  const topIngredients = useMemo(() => ingredientReport?.topIngredients ?? [], [ingredientReport]);
  const topPairs = useMemo(() => ingredientReport?.topPairs ?? [], [ingredientReport]);
  const pagedIngredients = useMemo(() => ingredientReport?.items.content ?? [], [ingredientReport]);

  const proposals: AiFormulaProposal[] = useMemo(() => {
    const base = buildAiFormulaProposals(topProducts, topIngredients);
    return base.map((item) => ({
      ...item,
      status: proposalStatuses[item.id] ?? item.status,
    }));
  }, [topProducts, topIngredients, proposalStatuses]);

  const handleExportExcel = async () => {
    setExporting(true);
    try {
      const [fullFormula, fullIngredient] = await Promise.all([
        productFormulaAnalysisApi.getProductFormulaReport(filters, 0, 200),
        productFormulaAnalysisApi.getIngredientAnalysisReport(filters, 0, 200),
      ]);
      exportProductFormulaExcel({
        filters,
        records: fullFormula.items.content,
        ingredients: fullIngredient.items.content,
        pairs: fullIngredient.topPairs,
      });
    } finally {
      setExporting(false);
    }
  };

  const handleChangeProposalStatus = (id: string, status: ProposalStatus) => {
    setProposalStatuses((prev) => ({ ...prev, [id]: status }));
  };

  const tabs: Array<{ key: AnalysisTab; label: string; icon: typeof Boxes; highlight?: boolean }> = [
    { key: "overview", label: "Sản phẩm & Công thức", icon: Package },
    { key: "ingredients", label: "Phân tích nguyên liệu", icon: Layers },
    { key: "ai-recommend", label: "AI Recommend công thức mới", icon: Sparkles, highlight: true },
  ];

  return (
    <section className="grid min-w-0 gap-5 print:gap-3">
      <InventoryPageHeader
        actions={
          <div className="flex flex-wrap items-center gap-2 print:hidden">
            <Button
              disabled={exporting}
              onClick={() => void handleExportExcel()}
            >
              <FileSpreadsheet size={16} aria-hidden="true" />
              {exporting ? "Đang xuất..." : "Xuất Excel"}
            </Button>
            <Button onClick={() => window.print()}>
              <Download size={16} aria-hidden="true" />
              Xuất PDF
            </Button>
          </div>
        }
        description="Phân tích mối liên hệ giữa sản lượng bán sản phẩm, công thức BOM và thành phần nguyên liệu phục vụ AI đề xuất công thức mới."
        title="Phân tích sản phẩm & Đề xuất công thức"
      />

      {/* Bộ lọc */}
      <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card print:hidden">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Từ ngày">
            <TextInput
              max={filters.toDate || undefined}
              onChange={(e) => updateFilters((p) => ({ ...p, fromDate: e.target.value }))}
              type="date"
              value={filters.fromDate}
            />
          </Field>
          <Field label="Đến ngày">
            <TextInput
              min={filters.fromDate || undefined}
              onChange={(e) => updateFilters((p) => ({ ...p, toDate: e.target.value }))}
              type="date"
              value={filters.toDate}
            />
          </Field>
          <Field label="Nhóm sản phẩm">
            <SearchableDropdown
              value={filters.categoryId}
              onChange={(val) => updateFilters((p) => ({ ...p, categoryId: val }))}
              options={categoryDropdown.options}
              onSearch={categoryDropdown.onSearch}
              onLoadMore={categoryDropdown.onLoadMore}
              hasMore={categoryDropdown.hasMore}
              loading={categoryDropdown.loading}
              onOpen={categoryDropdown.onOpen}
              placeholder="Chọn nhóm sản phẩm..."
            />
          </Field>
          <Field label="Sản phẩm">
            <SearchableDropdown
              value={filters.productId}
              onChange={(val) => updateFilters((p) => ({ ...p, productId: val }))}
              options={productDropdown.options}
              onSearch={productDropdown.onSearch}
              onLoadMore={productDropdown.onLoadMore}
              hasMore={productDropdown.hasMore}
              loading={productDropdown.loading}
              onOpen={productDropdown.onOpen}
              placeholder="Chọn sản phẩm..."
            />
          </Field>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => updateFilters((p) => ({ ...p, ...getPresetRange(7) }))}>
            7 ngày qua
          </Button>
          <Button onClick={() => updateFilters((p) => ({ ...p, ...getPresetRange(30) }))}>
            30 ngày qua
          </Button>
          <Button
            onClick={() =>
              updateFilters(() => ({ ...getPresetRange(30), categoryId: "", productId: "" }))
            }
          >
            Đặt lại
          </Button>
        </div>
      </section>

      {/* Tabs */}
      <nav aria-label="Phân tích sản phẩm và công thức" className="flex flex-wrap gap-2 print:hidden" role="tablist">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const active = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              type="button"
              role="tab"
              aria-selected={active}
              onClick={() => setActiveTab(tab.key)}
              className={`inline-flex items-center gap-2 rounded-xl border px-4 py-2.5 text-sm font-black transition ${
                active
                  ? "border-[#159734] bg-[#159734] text-white shadow-sm"
                  : tab.highlight
                    ? "border-[#9ad4a8] bg-[#edf8ef] text-[#159734] hover:bg-[#dff4e4]"
                    : "border-[#e4e9ed] bg-white text-[#334155] hover:bg-[#f7f9fa]"
              }`}
            >
              <Icon size={16} aria-hidden="true" />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </nav>

      {/* 1. SẢN PHẨM & CÔNG THỨC */}
      {activeTab === "overview" ? (
        <div className="grid gap-5">
          <div className="grid gap-5 lg:grid-cols-2">
            <DashboardSection title="Top sản phẩm bán chạy nhất">
              <RankList
                items={topProducts.map((r) => ({
                  id: r.variantId,
                  label: `${r.productName} - ${r.variantName}`,
                  sub: `${r.sku} • ${r.formulaName}`,
                  value: r.quantitySold,
                  valueText: `${formatVietnamNumber(r.quantitySold)} sp`,
                }))}
              />
            </DashboardSection>

            <DashboardSection title="Top công thức gắn với sản phẩm bán chạy">
              <RankList
                items={topProducts.map((r) => ({
                  id: `bom-${r.variantId}`,
                  label: r.formulaName,
                  sub: `${r.ingredients.length} nguyên liệu • ${r.categoryName}`,
                  value: r.quantitySold,
                  valueText: `${formatVietnamNumber(r.quantitySold)} sp`,
                }))}
              />
            </DashboardSection>
          </div>

          <DashboardSection title="Danh sách sản phẩm kèm công thức BOM tương ứng">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] border-collapse text-left text-xs">
                <thead>
                  <tr className="border-b border-[#e8eef0] font-bold uppercase text-[#697684]">
                    <th className="py-2.5 pr-3">Sản phẩm</th>
                    <th className="py-2.5 pr-3">Nhóm</th>
                    <th className="py-2.5 pr-3">Công thức BOM</th>
                    <th className="py-2.5 pr-3">Thành phần & Tỷ lệ</th>
                    <th className="py-2.5 text-right">Sản lượng bán</th>
                  </tr>
                </thead>
                <tbody>
                  {pagedBomRecords.map((rec) => (
                    <tr key={rec.variantId} className="border-b border-[#f1f5f9]">
                      <td className="py-2.5 pr-3 font-bold text-[#172317]">
                        {rec.productName} - {rec.variantName} ({rec.sku})
                      </td>
                      <td className="py-2.5 pr-3 text-[#536078]">{rec.categoryName}</td>
                      <td className="py-2.5 pr-3 font-semibold text-[#315f3c]">
                        {rec.formulaName}
                      </td>
                      <td className="py-2.5 pr-3 text-[#334155]">
                        {rec.ingredients
                          .map((i) => `${i.ingredientName} (${i.quantity}${i.unitSymbol} - ${i.ratioPercent}%)`)
                          .join("; ")}
                      </td>
                      <td className="py-2.5 text-right font-black tabular-nums text-[#159734]">
                        {formatVietnamNumber(rec.quantitySold)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <TablePagination
              page={formulaReport?.items.page ?? formulaPage}
              totalPages={formulaReport?.items.totalPages ?? 1}
              totalItems={formulaReport?.items.totalElements ?? 0}
              pageSize={formulaPageSize}
              itemLabel="sản phẩm"
              onPageChange={setFormulaPage}
              pageSizeOptions={[10, 20, 50]}
              onPageSizeChange={(nextSize) => {
                setFormulaPageSize(nextSize);
                setFormulaPage(0);
              }}
            />
          </DashboardSection>
        </div>
      ) : null}

      {/* 2. PHÂN TÍCH NGUYÊN LIỆU */}
      {activeTab === "ingredients" ? (
        <div className="grid gap-5">
          <div className="grid gap-5 lg:grid-cols-2">
            <DashboardSection title="Nguyên liệu xuất hiện nhiều nhất trong công thức bán chạy">
              <RankList
                items={topIngredients.map((ing) => ({
                  id: ing.ingredientId,
                  label: `${ing.ingredientName} (${ing.ingredientCode})`,
                  sub: `${ing.formulaCount} công thức sử dụng • Tỷ lệ TB ${ing.averageRatioPercent}%`,
                  value: ing.weightedSalesVolume || ing.formulaCount,
                  valueText: `${formatVietnamNumber(ing.weightedSalesVolume)} sp`,
                }))}
              />
            </DashboardSection>

            <DashboardSection title="Những nguyên liệu thường xuyên kết hợp với nhau">
              <RankList
                items={topPairs.map((pair) => ({
                  id: pair.pairKey,
                  label: `${pair.ingredientA} + ${pair.ingredientB}`,
                  sub: `Kết hợp trong ${pair.coOccurrenceCount} công thức`,
                  value: pair.combinedSalesVolume || pair.coOccurrenceCount,
                  valueText: `${formatVietnamNumber(pair.combinedSalesVolume)} sp`,
                }))}
              />
            </DashboardSection>
          </div>

          <DashboardSection title="Tần suất sử dụng & tỷ lệ từng nguyên liệu">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[600px] border-collapse text-left text-xs">
                <thead>
                  <tr className="border-b border-[#e8eef0] font-bold uppercase text-[#697684]">
                    <th className="py-2.5 pr-3">Mã NL</th>
                    <th className="py-2.5 pr-3">Tên nguyên liệu</th>
                    <th className="py-2.5 pr-3 text-right">Số công thức dùng</th>
                    <th className="py-2.5 pr-3 text-right">Tỷ lệ TB trong BOM</th>
                    <th className="py-2.5 text-right">Sản lượng bán gắn kết</th>
                  </tr>
                </thead>
                <tbody>
                  {pagedIngredients.map((ing) => (
                    <tr key={ing.ingredientId} className="border-b border-[#f1f5f9]">
                      <td className="py-2.5 pr-3 font-bold text-[#536078]">{ing.ingredientCode}</td>
                      <td className="py-2.5 pr-3 font-bold text-[#172317]">{ing.ingredientName}</td>
                      <td className="py-2.5 pr-3 text-right font-bold tabular-nums">
                        {formatVietnamNumber(ing.formulaCount)}
                      </td>
                      <td className="py-2.5 pr-3 text-right font-bold tabular-nums text-[#315f3c]">
                        {ing.averageRatioPercent}%
                      </td>
                      <td className="py-2.5 text-right font-black tabular-nums text-[#159734]">
                        {formatVietnamNumber(ing.weightedSalesVolume)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <TablePagination
              page={ingredientReport?.items.page ?? ingredientPage}
              totalPages={ingredientReport?.items.totalPages ?? 1}
              totalItems={ingredientReport?.items.totalElements ?? 0}
              pageSize={ingredientPageSize}
              itemLabel="nguyên liệu"
              onPageChange={setIngredientPage}
              pageSizeOptions={[10, 20, 50]}
              onPageSizeChange={(nextSize) => {
                setIngredientPageSize(nextSize);
                setIngredientPage(0);
              }}
            />
          </DashboardSection>
        </div>
      ) : null}

      {/* 4. AI RECOMMEND CÔNG THỨC MỚI */}
      {activeTab === "ai-recommend" ? (
        <AiFormulaProposalSection
          onChangeStatus={handleChangeProposalStatus}
          proposals={proposals}
        />
      ) : null}
    </section>
  );
}
