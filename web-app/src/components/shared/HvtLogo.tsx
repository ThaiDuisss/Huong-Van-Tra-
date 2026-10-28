interface HvtLogoProps {
    compact?: boolean;
    /** Chỉ hiện con dấu tròn (menu thu gọn). */
    mark?: boolean;
}

export function HvtLogo({ compact = false, mark = false }: HvtLogoProps) {
    if (mark) {
        return (
            <svg className="size-11" viewBox="76 3 128 128" role="img" aria-label="Hương Vân Trà">
                <circle cx="140" cy="67" r="62" fill="#086329" />
                <circle cx="140" cy="67" r="55" fill="none" stroke="#d8d63d" strokeWidth="4" />
                <circle cx="140" cy="67" r="43" fill="#f2e83a" />
                <text
                    x="140"
                    y="91"
                    fill="#086329"
                    fontFamily="Arial, sans-serif"
                    fontSize="70"
                    fontWeight="900"
                    textAnchor="middle"
                >
                    H
                </text>
            </svg>
        );
    }

    return (
        <div
            className={
                compact
                    ? "grid justify-items-center gap-1"
                    : "grid justify-items-center gap-1.5"
            }
            aria-label="Hương Vân Trà"
        >
            <svg
                className={
                    compact
                        ? "h-[66px] w-[112px]"
                        : "h-[100px] w-[144px]"
                }
                viewBox="0 0 280 188"
                role="img"
                aria-label="Hương Vân Trà"
            >
                <defs>
                    <linearGradient
                        id="hvt-seal-green"
                        x1="0"
                        x2="1"
                        y1="0"
                        y2="1"
                    >
                        <stop offset="0" stopColor="#0c6f32" />
                        <stop offset="1" stopColor="#04441f" />
                    </linearGradient>
                    <linearGradient
                        id="hvt-seal-yellow"
                        x1="0"
                        x2="1"
                        y1="0"
                        y2="1"
                    >
                        <stop offset="0" stopColor="#fff45d" />
                        <stop offset="1" stopColor="#d9c916" />
                    </linearGradient>
                    <clipPath id="hvt-seal-clip">
                        <circle cx="140" cy="67" r="52" />
                    </clipPath>
                </defs>

                <circle
                    cx="140"
                    cy="67"
                    r="62"
                    fill="url(#hvt-seal-green)"
                />
                <circle
                    cx="140"
                    cy="67"
                    r="55"
                    fill="none"
                    stroke="#d8d63d"
                    strokeWidth="4"
                />
                <circle
                    cx="140"
                    cy="67"
                    r="43"
                    fill="url(#hvt-seal-yellow)"
                />
                <g
                    clipPath="url(#hvt-seal-clip)"
                    opacity="0.9"
                    stroke="#d7dd51"
                    strokeLinecap="round"
                    strokeWidth="2"
                >
                    <path d="M88 80c-8-21-3-42 14-58" fill="none" />
                    <path d="M192 80c8-21 3-42-14-58" fill="none" />
                    {[
                        0, 1, 2, 3, 4, 5, 6
                    ].map((item) => (
                        <g key={item}>
                            <path
                                d={`M${98 - item * 2} ${35 + item * 7}l-13 -4`}
                            />
                            <path
                                d={`M${182 + item * 2} ${35 + item * 7}l13 -4`}
                            />
                        </g>
                    ))}
                </g>
                <text
                    x="140"
                    y="85"
                    fill="#086329"
                    fontFamily="Arial, sans-serif"
                    fontSize="70"
                    fontWeight="900"
                    textAnchor="middle"
                >
                    H
                </text>
                <path
                    d="M90 73c37-39 82-38 118-9-39 9-78 15-118 9Z"
                    fill="#0b7a35"
                />
                <path
                    d="M97 71c34 3 68 1 100-8"
                    fill="none"
                    stroke="#dbe86b"
                    strokeLinecap="round"
                    strokeWidth="2.5"
                />
                <path
                    d="M132 42c5 18 8 35 10 52"
                    fill="none"
                    stroke="#dbe86b"
                    strokeLinecap="round"
                    strokeWidth="2.5"
                />
                <path
                    d="M36 100h208l-28 20 28 19H36l28-19-28-20Z"
                    fill="#086b2d"
                />
                <text
                    x="140"
                    y="126"
                    fill="#fff052"
                    fontFamily="Arial, sans-serif"
                    fontSize="19"
                    fontWeight="900"
                    textAnchor="middle"
                    textLength="135"
                    lengthAdjust="spacingAndGlyphs"
                >
                    HƯƠNG VÂN TRÀ
                </text>
                <text
                    x="140"
                    y="169"
                    fill="#0f6f2f"
                    fontFamily="Arial, sans-serif"
                    fontSize="19"
                    fontWeight="800"
                    textAnchor="middle"
                    textLength="230"
                    lengthAdjust="spacingAndGlyphs"
                >
                    Tinh Hoa Trà Việt - Khởi Sự Tâm Giao
                </text>
            </svg>
        </div>
    );
}
