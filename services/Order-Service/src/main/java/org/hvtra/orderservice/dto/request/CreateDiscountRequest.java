package org.hvtra.orderservice.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.DiscountSource;
import org.hvtra.orderservice.domain.enums.DiscountType;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateDiscountRequest {

    private UUID usageId;

    @NotNull(message = "discountType không được để trống")
    private DiscountType discountType;

    @NotNull(message = "discountValue không được để trống")
    @DecimalMin(
            value = "0.0001",
            message = "discountValue phải lớn hơn 0"
    )
    @Digits(integer = 15, fraction = 4)
    private BigDecimal discountValue;

    @NotNull(message = "source không được để trống")
    private DiscountSource source;

    @NotBlank(message = "reasonCode không được để trống")
    @Size(max = 255)
    private String reasonCode;

    @Size(max = 500)
    private String note;

    @AssertTrue(message = "Giảm giá phần trăm không được vượt quá 100%")
    public boolean isPercentageValid() {
        if (discountType == null || discountValue == null) {
            return true;
        }

        return discountType != DiscountType.PERCENTAGE
                || discountValue.compareTo(new BigDecimal("100")) <= 0;
    }
}