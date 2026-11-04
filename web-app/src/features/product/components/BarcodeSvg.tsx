import { useEffect, useRef } from "react";
import JsBarcode from "jsbarcode";

export interface BarcodeSvgProps {
    value?: string | null;
    width?: number;
    height?: number;
    displayValue?: boolean;
    fontSize?: number;
    className?: string;
}

export function BarcodeSvg({
    value,
    width = 1.5,
    height = 42,
    displayValue = true,
    fontSize = 12,
    className = ""
}: BarcodeSvgProps) {
    const svgRef = useRef<SVGSVGElement | null>(null);

    useEffect(() => {
        if (!svgRef.current || !value || !value.trim()) return;
        try {
            JsBarcode(svgRef.current, value.trim(), {
                format: "CODE128",
                width,
                height,
                displayValue,
                fontSize,
                margin: 2,
                background: "transparent",
                lineColor: "#111827"
            });
        } catch (err) {
            console.error("Lỗi tạo mã vạch qua JsBarcode:", err);
        }
    }, [value, width, height, displayValue, fontSize]);

    if (!value || !value.trim()) {
        return (
            <div className="flex items-center justify-center text-xs text-gray-400 italic py-2">
                Chưa có mã vạch
            </div>
        );
    }

    return <svg ref={svgRef} className={className} />;
}
