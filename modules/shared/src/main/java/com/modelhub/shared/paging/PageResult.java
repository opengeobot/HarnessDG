package com.modelhub.shared.paging;

import java.util.List;

/** 页码模式结果（04 §6.1）：total/page/pageSize/items 均 required。 */
public record PageResult<T>(long total, int page, int pageSize, List<T> items) {

    public static <T> PageResult<T> of(long total, PageQuery query, List<T> items) {
        return new PageResult<>(total, query.page(), query.pageSize(), List.copyOf(items));
    }
}
