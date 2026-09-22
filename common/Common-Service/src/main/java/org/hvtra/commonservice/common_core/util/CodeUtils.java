package org.hvtra.commonservice.common_core.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class CodeUtils {

    private CodeUtils() {}

    /**
     * Lấy các chữ cái đầu viết hoa từ chuỗi tiếng Việt (loại bỏ dấu và ký tự đặc biệt).
     * Ví dụ:
     * - "Trà Sen Vàng" -> "TSV"
     * - "Kho Tổng Miền Bắc" -> "KTMB"
     * - "Nguyễn Văn An" -> "NVA"
     */
    public static String getInitials(String name, String fallbackPrefix) {
        return getInitials(name, fallbackPrefix, null, null);
    }

    /**
     * Lấy các chữ cái đầu viết hoa từ chuỗi tiếng Việt, có thể kèm thêm định lượng (quantity) và đơn vị (symbolUnit) nếu có.
     * (Cả quantity và symbolUnit đều không bắt buộc, có thể null).
     * Ví dụ:
     * - name="Trà Sen Vàng", quantity=null, symbolUnit=null -> "TSV"
     * - name="Đường Đen", quantity=500, symbolUnit="g" -> "DD-500G"
     * - name="Trà Xanh", quantity=1, symbolUnit="kg" -> "TX-1KG"
     * - name="Trà Sữa", quantity=null, symbolUnit="ly" -> "TS-LY"
     * - name="Đá Viên", quantity=10, symbolUnit=null -> "DV-10"
     */
    public static String getInitials(String name, String fallbackPrefix, Integer quantity, String symbolUnit) {
        String initials = extractInitials(name, fallbackPrefix);
        StringBuilder sb = new StringBuilder(initials);

        boolean hasQuantity = quantity != null;
        boolean hasSymbol = symbolUnit != null && !symbolUnit.isBlank();

        if (hasQuantity || hasSymbol) {
            sb.append("-");
            if (hasQuantity) {
                sb.append(quantity);
            }
            if (hasSymbol) {
                String cleanSymbol = Normalizer.normalize(symbolUnit.trim(), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .replace("Đ", "D")
                        .replace("đ", "d")
                        .replaceAll("[^a-zA-Z0-9]", "")
                        .toUpperCase();
                sb.append(cleanSymbol);
            }
        }

        return sb.toString();
    }

    private static String extractInitials(String name, String fallbackPrefix) {
        if (name == null || name.trim().isEmpty()) {
            return fallbackPrefix != null && !fallbackPrefix.isBlank()
                    ? fallbackPrefix.trim().toUpperCase()
                    : "GEN";
        }

        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("Đ", "D")
                .replace("đ", "d");

        String clean = normalized.replaceAll("[^a-zA-Z0-9\\s]", " ").trim();
        String[] words = clean.split("\\s+");

        String initials = Arrays.stream(words)
                .filter(w -> !w.isEmpty())
                .map(w -> String.valueOf(Character.toUpperCase(w.charAt(0))))
                .collect(Collectors.joining());

        return initials.isEmpty()
                ? (fallbackPrefix != null && !fallbackPrefix.isBlank() ? fallbackPrefix.trim().toUpperCase() : "GEN")
                : initials;
    }
}
