package org.hvtra.promotionservice.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageUtils {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT_FIELD = "createdAt";

    private PageUtils() {
    }

    public static Pageable createPageable(Integer page, Integer size) {
        return PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, DEFAULT_SORT_FIELD)
        );
    }

    public static Pageable createPageable(Integer page, Integer size, String sortBy) {
        return PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, normalizeSortBy(sortBy))
        );
    }

    public static Pageable createPageable(Integer page, Integer size, String sortBy, Sort.Direction direction) {
        return PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(
                        direction == null ? Sort.Direction.DESC : direction,
                        normalizeSortBy(sortBy)
                )
        );
    }

    public static Pageable createDescPageable(Integer page, Integer size, String sortBy) {
        return createPageable(page, size, sortBy, Sort.Direction.DESC);
    }

    public static Pageable createAscPageable(Integer page, Integer size, String sortBy) {
        return createPageable(page, size, sortBy, Sort.Direction.ASC);
    }

    private static int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }

    private static String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return DEFAULT_SORT_FIELD;
        }

        return sortBy;
    }
}