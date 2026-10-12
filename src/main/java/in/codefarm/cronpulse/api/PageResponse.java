package in.codefarm.cronpulse.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * The envelope for every paginated list. We don't serialise Spring's {@link Page} directly: its JSON
 * shape is an implementation detail that has changed between versions, and our API must not.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
