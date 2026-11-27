import type { TasteAttribute, TasteCriteria } from "../services/tasteSurvey.api";

export interface TastePreferenceSelection {
  values: string[];
  hard: boolean;
  notApplicable?: boolean;
  priorities?: TasteCriteria["softGroups"][number]["priorities"];
}

export function TastePreferenceFields({ attributes, selections, updateSelection, toggleValue }: {
  attributes: TasteAttribute[];
  selections: Record<string, TastePreferenceSelection>;
  updateSelection(attribute: string, selection: TastePreferenceSelection): void;
  toggleValue(attribute: TasteAttribute, value: string): void;
}) {
  return (
    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
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
                  onChange={(event) => updateSelection(attr.attributeCode, { ...sel, hard: event.target.checked, priorities: undefined })}
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
                  aria-pressed={isChosen}
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
  );
}
