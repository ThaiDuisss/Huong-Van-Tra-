import { useId } from "react";

import { formatVietnamNumber, formatVnd } from "../../inventory/utils/inventory-format";
import type { DashboardTrendPoint } from "../types/dashboard.types";
import { formatCompactVnd } from "../utils/dashboard-mappers";

export type TrendMetric = "revenue" | "orders";

const WIDTH = 640;
const HEIGHT = 220;
const PADDING = { bottom: 28, left: 56, right: 16, top: 12 };

/**
 * Chart xu hướng dạng area/line SVG nhẹ (không thêm chart library), mở rộng từ ý tưởng
 * `MiniSparkline` của dự án. Có trục, lưới, tooltip title và bảng ẩn cho screen reader.
 */
export function TrendChart({
  metric,
  points,
  title,
}: {
  metric: TrendMetric;
  points: DashboardTrendPoint[];
  title: string;
}) {
  const gradientId = useId();
  const values = points.map((point) => point.value);
  const max = Math.max(1, ...values);
  const niceMax = niceCeil(max);
  const innerWidth = WIDTH - PADDING.left - PADDING.right;
  const innerHeight = HEIGHT - PADDING.top - PADDING.bottom;
  const stepX = points.length > 1 ? innerWidth / (points.length - 1) : 0;
  const toX = (index: number) => PADDING.left + index * stepX;
  const toY = (value: number) => PADDING.top + innerHeight - (value / niceMax) * innerHeight;
  const formatValue = (value: number) =>
    metric === "revenue" ? formatVnd(value) : `${formatVietnamNumber(value)} đơn`;
  const formatAxis = (value: number) =>
    metric === "revenue" ? formatCompactVnd(value) : formatVietnamNumber(value);
  const linePath = points
    .map((point, index) => `${index === 0 ? "M" : "L"}${toX(index).toFixed(1)},${toY(point.value).toFixed(1)}`)
    .join(" ");
  const areaPath =
    points.length > 0
      ? `${linePath} L${toX(points.length - 1).toFixed(1)},${(PADDING.top + innerHeight).toFixed(1)} L${PADDING.left},${(PADDING.top + innerHeight).toFixed(1)} Z`
      : "";
  const gridSteps = [0, 0.5, 1];

  return (
    <figure className="grid min-w-0 gap-2">
      <svg
        aria-label={title}
        className="h-auto w-full"
        role="img"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      >
        <title>{title}</title>
        <defs>
          <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="#159734" stopOpacity="0.22" />
            <stop offset="100%" stopColor="#159734" stopOpacity="0.02" />
          </linearGradient>
        </defs>
        {gridSteps.map((step) => {
          const y = PADDING.top + innerHeight - step * innerHeight;

          return (
            <g key={step}>
              <line
                stroke="#edf0f4"
                strokeDasharray={step === 0 ? undefined : "4 4"}
                x1={PADDING.left}
                x2={WIDTH - PADDING.right}
                y1={y}
                y2={y}
              />
              <text
                fill="#697684"
                fontSize="11"
                fontWeight="700"
                textAnchor="end"
                x={PADDING.left - 8}
                y={y + 4}
              >
                {formatAxis(niceMax * step)}
              </text>
            </g>
          );
        })}
        {points.length > 1 ? <path d={areaPath} fill={`url(#${gradientId})`} /> : null}
        <path d={linePath} fill="none" stroke="#159734" strokeLinejoin="round" strokeWidth="2.5" />
        {points.map((point, index) => (
          <g key={point.label}>
            <circle cx={toX(index)} cy={toY(point.value)} fill="#ffffff" r="4" stroke="#159734" strokeWidth="2">
              <title>{`${point.label}: ${formatValue(point.value)}`}</title>
            </circle>
            <text
              fill="#536078"
              fontSize="11"
              fontWeight="700"
              textAnchor={index === 0 ? "start" : index === points.length - 1 ? "end" : "middle"}
              x={toX(index)}
              y={HEIGHT - 8}
            >
              {point.label}
            </text>
          </g>
        ))}
      </svg>
      <table className="sr-only">
        <caption>{title}</caption>
        <thead>
          <tr>
            <th scope="col">Thời điểm</th>
            <th scope="col">{metric === "revenue" ? "Doanh thu" : "Số đơn"}</th>
          </tr>
        </thead>
        <tbody>
          {points.map((point) => (
            <tr key={point.label}>
              <td>{point.label}</td>
              <td>{formatValue(point.value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

function niceCeil(value: number) {
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const normalized = value / magnitude;
  const factor = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;

  return factor * magnitude;
}
