package com.modelhub.shared.paging;

import java.util.List;

/** Cursor 模式结果（04 §6.2）：items + 可空 nextCursor，不支持任意跳页。 */
public record CursorResult<T>(List<T> items, String nextCursor) {

    public static <T> CursorResult<T> of(List<T> items, Long lastKey) {
        return new CursorResult<>(List.copyOf(items), lastKey == null ? null : CursorQuery.encode(lastKey));
    }
}
