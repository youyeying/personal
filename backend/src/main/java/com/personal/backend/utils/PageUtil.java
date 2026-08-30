package com.personal.backend.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * 通用分页响应组装工具
 * 各 Service 分页查询统一返回 {records, total, current, size} 结构，消除重复的 Map.of 样板
 */
public final class PageUtil {

    private PageUtil() {
    }

    /**
     * 组装分页响应
     *
     * @param page    分页对象（取 total/current/size）
     * @param records 当前页记录（实体或已转换的 Map 列表）
     */
    public static Map<String, Object> ok(Page<?> page, List<?> records) {
        return Map.of(
                "records", records,
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "size", page.getSize()
        );
    }
}