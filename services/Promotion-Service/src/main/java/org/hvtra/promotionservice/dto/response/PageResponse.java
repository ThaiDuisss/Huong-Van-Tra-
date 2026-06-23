package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response phân trang dữ liệu")
public class PageResponse<T> {

    @Schema(description = "Danh sách dữ liệu của trang hiện tại")
    List<T> content;

    @Schema(description = "Số trang hiện tại, bắt đầu từ 0", example = "0")
    int page;

    @Schema(description = "Số lượng phần tử trên mỗi trang", example = "10")
    int size;

    @Schema(description = "Tổng số phần tử", example = "100")
    long totalElements;

    @Schema(description = "Tổng số trang", example = "10")
    int totalPages;

    @Schema(description = "Có phải trang đầu tiên hay không", example = "true")
    boolean first;

    @Schema(description = "Có phải trang cuối cùng hay không", example = "false")
    boolean last;

    @Schema(description = "Trang hiện tại có rỗng hay không", example = "false")
    boolean empty;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }
}
