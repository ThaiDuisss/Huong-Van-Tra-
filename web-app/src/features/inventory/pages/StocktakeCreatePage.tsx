import { ArrowLeft, Check, LoaderCircle } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { Button } from "../../../components/ui/Button";
import { buttonClassName } from "../../../components/ui/button-styles";
import { Field, NativeSelect, TextArea, TextInput } from "../../../components/ui/Field";
import { stocktakeScopeLabels, stocktakeScopeOptions } from "../constants/stocktake.constants";
import { stocktakeApi } from "../services/stocktake.api";
import type {
  StocktakeCountType,
  StocktakeOptions,
  StocktakeScopeType,
} from "../types/stocktake.types";

const emptyOptions: StocktakeOptions = {
  batches: [],
  branches: [],
  items: [],
  locations: [],
  users: [],
};

export function StocktakeCreatePage() {
  const navigate = useNavigate();
  const [options, setOptions] = useState<StocktakeOptions>(emptyOptions);
  const [branchId, setBranchId] = useState("");
  const [scopeType, setScopeType] = useState<StocktakeScopeType>("FULL");
  const [countType, setCountType] = useState<StocktakeCountType>("FULL_COUNT");
  const [blindCount, setBlindCount] = useState(false);
  const [locationIds, setLocationIds] = useState<string[]>([]);
  const [inventoryItemIds, setInventoryItemIds] = useState<string[]>([]);
  const [note, setNote] = useState("");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    void stocktakeApi
      .getStocktakeOptions()
      .then((response) => {
        if (!active) return;
        setOptions(response);
        setBranchId(response.branches[0]?.id ?? "");
      })
      .catch((loadError: unknown) => {
        if (active)
          setError(
            loadError instanceof Error ? loadError.message : "Không thể tải dữ liệu tạo kiểm kê.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const locations = useMemo(
    () => options.locations.filter((item) => item.branchId === branchId),
    [branchId, options.locations],
  );
  const items = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase("vi-VN");
    return keyword
      ? options.items.filter((item) =>
          `${item.itemCode} ${item.itemName}`.toLocaleLowerCase("vi-VN").includes(keyword),
        )
      : options.items;
  }, [options.items, search]);

  function changeBranch(value: string) {
    setBranchId(value);
    setLocationIds([]);
    setInventoryItemIds([]);
    setError("");
  }

  function changeScope(value: StocktakeScopeType) {
    setScopeType(value);
    setLocationIds([]);
    setInventoryItemIds([]);
    setError("");
  }

  function changeCountType(value: StocktakeCountType) {
    setCountType(value);
    changeScope(value === "FULL_COUNT" ? "FULL" : value === "CYCLE_COUNT" ? "PARTIAL" : "LOCATION");
  }

  async function submit() {
    if (!branchId) return setError("Vui lòng chọn chi nhánh.");
    if (scopeType === "LOCATION" && locationIds.length === 0)
      return setError("Chọn ít nhất một vị trí kiểm kê.");
    if (scopeType === "PARTIAL" && inventoryItemIds.length === 0)
      return setError("Chọn ít nhất một mặt hàng kiểm kê.");
    setSubmitting(true);
    setError("");
    try {
      const result = await stocktakeApi.createStocktake({
        blindCount,
        branchId,
        countType,
        scopeType,
        note: note.trim() || undefined,
        locationIds: scopeType === "LOCATION" ? locationIds : undefined,
        inventoryItemIds: scopeType === "PARTIAL" ? inventoryItemIds : undefined,
      });
      navigate(`/management/inventory/stocktakes/${result.id}`);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Không thể tạo phiên kiểm kê.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="grid gap-4 text-[#172317]">
      <header className="flex items-start gap-3">
        <Link
          className="mt-1 grid size-9 place-items-center rounded-lg border border-[#dce5e1]"
          to="/management/inventory/stocktakes"
        >
          <ArrowLeft size={18} />
        </Link>
        <div>
          <h1 className="text-2xl font-bold sm:text-3xl">Tạo phiên kiểm kê</h1>
          <p className="mt-1 text-sm text-gray-500">
            Chọn chi nhánh và phạm vi hàng hóa cần đối chiếu.
          </p>
        </div>
      </header>
      <div className="grid gap-4 rounded-xl border border-[#dce5e1] bg-white p-5 shadow-[0_12px_32px_rgba(31,53,43,0.06)]">
        {loading ? (
          <div className="flex items-center gap-2 py-8 text-sm font-bold text-[#657282]">
            <LoaderCircle className="animate-spin" size={18} /> Đang tải dữ liệu...
          </div>
        ) : null}
        {!loading ? (
          <>
            <div className="grid gap-4 md:grid-cols-3">
              <SelectField
                label="Chi nhánh"
                value={branchId}
                onChange={changeBranch}
                options={options.branches.map((item) => ({ label: item.name, value: item.id }))}
              />
              <SelectField
                label="Hình thức"
                value={countType}
                onChange={(value) => changeCountType(value as StocktakeCountType)}
                options={[
                  { label: "Kiểm kê toàn bộ", value: "FULL_COUNT" },
                  { label: "Kiểm kê định kỳ theo nhóm hàng", value: "CYCLE_COUNT" },
                  { label: "Kiểm kê đột xuất", value: "SPOT_COUNT" },
                ]}
              />
              <SelectField
                label="Phạm vi"
                value={scopeType}
                onChange={(value) => changeScope(value as StocktakeScopeType)}
                options={stocktakeScopeOptions
                  .filter((item) => countType !== "FULL_COUNT" || item === "FULL")
                  .map((item) => ({ label: stocktakeScopeLabels[item], value: item }))}
              />
            </div>
            {scopeType === "LOCATION" ? (
              <ChoiceGrid
                label="Vị trí kiểm kê"
                empty="Chi nhánh chưa có vị trí hoạt động."
                options={locations.map((item) => ({ id: item.id, label: item.name }))}
                selected={locationIds}
                onToggle={(id) => setLocationIds(toggle(locationIds, id))}
              />
            ) : null}
            {scopeType === "PARTIAL" ? (
              <div className="grid gap-3">
                <Field label="Tìm mặt hàng">
                  <TextInput
                    onChange={(event) => setSearch(event.currentTarget.value)}
                    placeholder="Nhập mã hoặc tên mặt hàng"
                    value={search}
                  />
                </Field>
                <ChoiceGrid
                  label="Mặt hàng kiểm kê"
                  empty="Không có mặt hàng phù hợp."
                  options={items.map((item) => ({
                    id: item.id,
                    label: `${item.itemCode} · ${item.itemName}${item.unit ? ` (${item.unit})` : ""}`,
                  }))}
                  selected={inventoryItemIds}
                  onToggle={(id) => setInventoryItemIds(toggle(inventoryItemIds, id))}
                />
              </div>
            ) : null}
            <label className="flex items-start gap-3 rounded-xl border border-[#dce5e1] p-3 text-sm font-bold">
              <input
                className="mt-0.5"
                checked={blindCount}
                onChange={(event) => setBlindCount(event.currentTarget.checked)}
                type="checkbox"
              />
              <span>
                Kiểm kê kín
                <span className="mt-0.5 block text-xs font-semibold text-[#697684]">
                  Nhân viên không nhìn thấy số lượng trên hệ thống trong lúc nhập kết quả đếm.
                </span>
              </span>
            </label>
            <Field label="Ghi chú">
              <TextArea
                onChange={(event) => setNote(event.currentTarget.value)}
                value={note}
              />
            </Field>
          </>
        ) : null}
      </div>
      <footer className="flex flex-wrap items-center justify-end gap-3 rounded-xl border border-[#dce5e1] bg-white p-4">
        {error ? <p className="mr-auto text-sm font-bold text-red-700">{error}</p> : null}
        <Link className={buttonClassName()} to="/management/inventory/stocktakes">
          Hủy
        </Link>
        <Button disabled={loading || submitting} onClick={() => void submit()} variant="primary">
          {submitting ? <LoaderCircle className="animate-spin" size={17} /> : <Check size={17} />}{" "}
          Tạo phiên
        </Button>
      </footer>
    </section>
  );
}

function ChoiceGrid({
  empty,
  label,
  onToggle,
  options,
  selected,
}: {
  empty: string;
  label: string;
  onToggle: (id: string) => void;
  options: Array<{ id: string; label: string }>;
  selected: string[];
}) {
  return (
    <fieldset className="grid gap-2">
      <legend className="mb-2 text-sm font-bold">
        {label} <span className="text-[#159734]">({selected.length} đã chọn)</span>
      </legend>
      <div className="grid max-h-64 gap-2 overflow-auto rounded-xl border border-[#dce5e1] p-3 sm:grid-cols-2">
        {options.length ? (
          options.map((option) => (
            <label
              className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 text-sm font-semibold ${selected.includes(option.id) ? "border-[#159734] bg-[#eaf8ee]" : "border-[#e5ece8]"}`}
              key={option.id}
            >
              <input
                checked={selected.includes(option.id)}
                onChange={() => onToggle(option.id)}
                type="checkbox"
              />
              {option.label}
            </label>
          ))
        ) : (
          <p className="p-3 text-sm text-[#697684]">{empty}</p>
        )}
      </div>
    </fieldset>
  );
}

function SelectField({
  label,
  onChange,
  options,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  options: Array<{ label: string; value: string }>;
  value: string;
}) {
  return (
    <Field label={label}>
      <NativeSelect
        onChange={(event) => onChange(event.currentTarget.value)}
        value={value}
      >
        <option value="" disabled>
          Chọn {label.toLocaleLowerCase("vi-VN")}
        </option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </NativeSelect>
    </Field>
  );
}

function toggle(values: string[], id: string) {
  return values.includes(id) ? values.filter((value) => value !== id) : [...values, id];
}
