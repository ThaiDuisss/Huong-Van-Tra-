package org.hvtra.promotionservice.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionPreviewResponse {

    BigDecimal orderAmount;

    List<AvailablePromotionResponse> availablePromotions;

}
