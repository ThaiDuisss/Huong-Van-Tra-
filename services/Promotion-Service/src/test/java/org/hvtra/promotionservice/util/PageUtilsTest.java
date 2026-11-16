package org.hvtra.promotionservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageUtils")
class PageUtilsTest {

    @Test
    @DisplayName("defaults to page 0, size 10, createdAt DESC")
    void defaults() {
        Pageable pageable = PageUtils.createPageable(null, null);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @ParameterizedTest(name = "page {0} -> {2}, size {1} -> {3}")
    @CsvSource({
            "-1, 0, 0, 10",
            "0, -5, 0, 10",
            "3, 1, 3, 1",
            "7, 100, 7, 100",
            "2, 101, 2, 100",
            "2, 5000, 2, 100"
    })
    void normalizesPageAndSize(int page, int size, int expectedPage, int expectedSize) {
        Pageable pageable = PageUtils.createPageable(page, size);

        assertThat(pageable.getPageNumber()).isEqualTo(expectedPage);
        assertThat(pageable.getPageSize()).isEqualTo(expectedSize);
    }

    @Test
    @DisplayName("sortBy falls back to createdAt when null or blank")
    void sortByFallback() {
        assertThat(PageUtils.createPageable(0, 10, null).getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(PageUtils.createPageable(0, 10, "  ").getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(PageUtils.createPageable(0, 10, "name").getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "name"));
    }

    @Test
    @DisplayName("an explicit direction is used and a null direction means DESC")
    void direction() {
        assertThat(PageUtils.createPageable(0, 10, "priority", Sort.Direction.ASC).getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "priority"));
        assertThat(PageUtils.createPageable(0, 10, "priority", null).getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "priority"));
    }

    @Test
    @DisplayName("asc and desc shortcuts keep the normalisation rules")
    void shortcuts() {
        Pageable asc = PageUtils.createAscPageable(-2, 500, "");
        Pageable desc = PageUtils.createDescPageable(4, 20, "startAt");

        assertThat(asc.getPageNumber()).isZero();
        assertThat(asc.getPageSize()).isEqualTo(100);
        assertThat(asc.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt"));
        assertThat(desc.getPageNumber()).isEqualTo(4);
        assertThat(desc.getPageSize()).isEqualTo(20);
        assertThat(desc.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "startAt"));
    }
}
