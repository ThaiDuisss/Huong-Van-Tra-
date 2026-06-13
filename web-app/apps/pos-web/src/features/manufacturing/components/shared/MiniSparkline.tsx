interface MiniSparklineProps {
    values: number[];
    tone?: "green" | "orange";
}

export function MiniSparkline({
    values,
    tone = "green"
}: MiniSparklineProps) {
    const max = Math.max(...values);
    const min = Math.min(...values);
    const range = Math.max(1, max - min);
    const points = values
        .map((value, index) => {
            const x =
                values.length === 1
                    ? 0
                    : (index / (values.length - 1)) * 84;
            const y = 28 - ((value - min) / range) * 24;

            return `${x},${y}`;
        })
        .join(" ");

    return (
        <svg
            width="86"
            height="32"
            viewBox="0 0 86 32"
            role="img"
            aria-label="Xu hướng chỉ số"
        >
            <polyline
                points={points}
                fill="none"
                stroke={tone === "green" ? "#08724f" : "#f97316"}
                strokeWidth="2"
            />
        </svg>
    );
}
