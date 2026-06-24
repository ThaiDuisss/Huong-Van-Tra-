package org.hvtra.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CustomerFilterRequest(
        @Schema(
                description = "Tìm theo tên, số điện thoại, email hoặc mã khách hàng"
        )
        String q,

        @Schema(
                description = "Trạng thái khách hàng",
                allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"}
        )
        CustomerStatus status,

        @Schema(
                description = "Danh sách ID hạng khách hàng; có thể truyền nhiều lần"
        )
        List<UUID> tierIds,

        @Schema(
                description = "Ngày giờ tạo từ thời điểm này, ISO-8601 UTC",
                example = "2026-06-01T00:00:00Z"
        )
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdFrom,

        @Schema(
                description = "Ngày giờ tạo đến thời điểm này, ISO-8601 UTC",
                example = "2026-06-30T23:59:59Z"
        )
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdTo,

        @Schema(
                description = "Ngày mua gần nhất từ ngày này",
                example = "2026-05-25"
        )
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate lastPurchaseFrom,

        @Schema(
                description = "Ngày mua gần nhất đến ngày này",
                example = "2026-03-26"
        )
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate lastPurchaseTo,

        @Schema(
                description = "Khi lọc lastPurchaseTo, có bao gồm khách chưa từng mua hay không",
                example = "true"
        )
        Boolean includeNeverPurchased,

        @Schema(description = "Tổng chi tiêu tối thiểu", example = "1000000")
        @DecimalMin("0")
        BigDecimal minTotalSpent,

        @Schema(description = "Tổng chi tiêu tối đa", example = "10000000")
        @DecimalMin("0")
        BigDecimal maxTotalSpent,

        @Schema(
                description = "true: đã từng mua; false: chưa từng mua",
                example = "false"
        )
        Boolean hasPurchased,

        @Schema(
                description = "Tháng sinh, từ 1 đến 12",
                minimum = "1",
                maximum = "12"
        )
        @Min(1) @Max(12)
        Integer birthMonth,

        @Schema(
                description = "Năm sinh",
                minimum = "1900"
        )
        @Min(1900)
        Integer birthYear,

        @Schema(
                description = "Danh sách tier VIP"
        )
        List<UUID> vipTierIds
) {
    @Schema(hidden = true)
    @AssertTrue(message = "createdFrom phải nhỏ hơn hoặc bằng createdTo")
    public boolean isCreatedRangeValid() {
        return createdFrom == null || createdTo == null || !createdFrom.isAfter(createdTo);
    }

    @Schema(hidden = true)
    @AssertTrue(message = "lastPurchaseFrom phải nhỏ hơn hoặc bằng lastPurchaseTo")
    public boolean isLastPurchaseRangeValid() {
        return lastPurchaseFrom == null
                || lastPurchaseTo == null
                || !lastPurchaseFrom.isAfter(lastPurchaseTo);
    }

    @Schema(hidden = true)
    @AssertTrue(message = "minTotalSpent phải nhỏ hơn hoặc bằng maxTotalSpent")
    public boolean isTotalSpentRangeValid() {
        return minTotalSpent == null
                || maxTotalSpent == null
                || minTotalSpent.compareTo(maxTotalSpent) <= 0;
    }
}
