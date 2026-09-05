package com.personal.backend.controller;

import com.personal.backend.common.BizException;
import com.personal.backend.service.ExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV 导出工具测试
 * - csvEscape 为 ExportController 的 private 静态方法，经反射调用验证 RFC 4180 转义
 * - csvOf 未知模块的分发校验在 ExportService 上验证（不依赖数据库）
 */
class ExportControllerTest {

    private String csvEscape(String v) throws Exception {
        Method m = ExportController.class.getDeclaredMethod("csvEscape", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, v);
    }

    @Test
    @DisplayName("普通值原样返回")
    void plainValue() throws Exception {
        assertEquals("米饭", csvEscape("米饭"));
        assertEquals("116", csvEscape("116"));
    }

    @Test
    @DisplayName("空值返回空串")
    void nullValue() throws Exception {
        assertEquals("", csvEscape(null));
    }

    @Test
    @DisplayName("含逗号：包双引号")
    void commaEscaped() throws Exception {
        assertEquals("\"a,b\"", csvEscape("a,b"));
    }

    @Test
    @DisplayName("含引号：包双引号且内部引号翻倍（RFC 4180）")
    void quoteEscaped() throws Exception {
        assertEquals("\"说\"\"你好\"\"\"", csvEscape("说\"你好\""));
    }

    @Test
    @DisplayName("含换行：包双引号")
    void newlineEscaped() throws Exception {
        assertEquals("\"a\nb\"", csvEscape("a\nb"));
        assertEquals("\"a\rb\"", csvEscape("a\rb"));
    }

    @Test
    @DisplayName("未知导出模块抛业务异常（csvOf 在 ExportService）")
    void rejectsUnknownModule() {
        ExportService service = new ExportService(null, null, null, null, null, null, null, null, null);
        BizException ex = assertThrows(BizException.class, () -> service.csvOf("unknown"));
        assertTrue(ex.getMessage().contains("未知导出模块"));
    }
}
