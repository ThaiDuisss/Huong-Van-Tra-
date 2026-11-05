import { CalendarDays } from "lucide-react";

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../../ui/select";
import { TIME_PRESETS, type TimePreset, type TimeRangeValue } from "./timeRange.utils";

interface TimeRangeSelectProps {
    value: TimeRangeValue;
    onChange: (value: TimeRangeValue) => void;
}

export function TimeRangeSelect({ value, onChange }: TimeRangeSelectProps) {
    return (
        <div className="flex flex-wrap items-center gap-2">
            <Select
                onValueChange={(preset) => onChange({ preset: preset as TimePreset, from: value.from, to: value.to })}
                value={value.preset}
            >
                <SelectTrigger aria-label="Lọc theo thời gian" className="h-11 min-w-[190px]">
                    <CalendarDays size={17} aria-hidden="true" />
                    <SelectValue />
                </SelectTrigger>
                <SelectContent className="w-52">
                    {TIME_PRESETS.map((preset) => (
                        <SelectItem key={preset.value} value={preset.value}>
                            {preset.label}
                        </SelectItem>
                    ))}
                </SelectContent>
            </Select>
            {value.preset === "custom" ? (
                <div className="flex items-center gap-1.5 text-xs font-bold text-[#697684]">
                    <input
                        aria-label="Từ ngày"
                        className="h-11 rounded-xl border border-[#dce5e1] bg-white px-3 text-sm font-bold text-[#273a2b] shadow-sm"
                        max={value.to || undefined}
                        onChange={(event) => onChange({ ...value, from: event.currentTarget.value || undefined })}
                        type="date"
                        value={value.from ?? ""}
                    />
                    <span aria-hidden="true">–</span>
                    <input
                        aria-label="Đến ngày"
                        className="h-11 rounded-xl border border-[#dce5e1] bg-white px-3 text-sm font-bold text-[#273a2b] shadow-sm"
                        min={value.from || undefined}
                        onChange={(event) => onChange({ ...value, to: event.currentTarget.value || undefined })}
                        type="date"
                        value={value.to ?? ""}
                    />
                </div>
            ) : null}
        </div>
    );
}
