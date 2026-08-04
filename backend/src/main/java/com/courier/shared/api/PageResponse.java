package com.courier.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Pagination envelope.
 *
 * <p>Spring's {@code Page} is deliberately not serialised directly: its JSON shape
 * is an implementation detail that has changed between Spring versions and leaks
 * {@code Pageable}/{@code Sort} internals into the public contract. This record is
 * ours, and it is stable.
 */
@Schema(name = "PageResponse", description = "Paginated collection")
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean hasNext
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext()
        );
    }

    /** Maps entities to DTOs without materialising an intermediate list. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}
