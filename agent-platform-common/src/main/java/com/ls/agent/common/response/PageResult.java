package com.ls.agent.common.response;

import java.util.List;
import java.util.Objects;

/**
 * Standard page result for list APIs.
 */
public record PageResult<T>(
        List<T> records,
        long pageNo,
        long pageSize,
        long total,
        long totalPages
) {

    public PageResult {
        records = List.copyOf(Objects.requireNonNull(records, "records must not be null"));
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be greater than or equal to 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be greater than or equal to 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must not be negative");
        }
    }

    public static <T> PageResult<T> of(List<T> records, long pageNo, long pageSize, long total) {
        long totalPages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResult<>(records, pageNo, pageSize, total, totalPages);
    }

    public static <T> PageResult<T> empty(long pageNo, long pageSize) {
        return of(List.of(), pageNo, pageSize, 0);
    }
}
