package org.hvtra.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.dto.request.PromotionPreviewRequest;
import org.hvtra.promotionservice.dto.response.PromotionPreviewResponse;
import org.hvtra.promotionservice.service.PromotionApplyService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionApplyController {

    PromotionApplyService promotionApplyService;

    @PostMapping("/preview")
    @Operation(summary = "Xem trước khuyến mãi có thể áp dụng cho đơn POS")
    public PromotionPreviewResponse preview(@Valid @RequestBody PromotionPreviewRequest request) {
        return promotionApplyService.preview(request);
    }
}