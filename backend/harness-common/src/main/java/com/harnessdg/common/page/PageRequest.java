/**
 * 功能：分页请求参数
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.common.page;

import lombok.Data;

@Data
public class PageRequest {

    private int page = 1;
    private int pageSize = 20;
    private String sortBy;
    private String sortOrder = "desc";

    public int getOffset() {
        return (page - 1) * pageSize;
    }
}
