package com.personal.backend.controller;

import com.personal.backend.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 数据导出：CSV（单模块）/ JSON（全量备份）
 * - GET /api/export/{module}.csv → 单模块 CSV（UTF-8 BOM，Excel 直开）
 * - GET /api/export/all.json     → 全模块 + 字典 JSON 打包
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /** 单模块 CSV：module ∈ expense/exercise/food/weight/learn/dailyNote */
    @GetMapping("/{module}.csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable String module) {
        List<String[]> rows = exportService.csvOf(module);
        StringBuilder sb = new StringBuilder("\uFEFF"); // UTF-8 BOM
        for (String[] row : rows) {
            String[] escaped = new String[row.length];
            for (int i = 0; i < row.length; i++) escaped[i] = csvEscape(row[i]);
            sb.append(String.join(",", escaped)).append("\r\n");
        }
        String fileName = URLEncoder.encode("个人记录-" + module + "-" + LocalDate.now(), StandardCharsets.UTF_8) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 全量 JSON 备份（记录 + 字典 + 导出时间）；返回 Map 由 Spring 自动序列化，attachment 头触发下载 */
    @GetMapping("/all.json")
    public ResponseEntity<java.util.Map<String, Object>> exportAll() {
        String fileName = URLEncoder.encode("个人记录-备份-" + LocalDate.now(), StandardCharsets.UTF_8) + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_JSON)
                .body(exportService.exportAll());
    }

    /** RFC 4180：含逗号/引号/换行的值包双引号，内部引号翻倍 */
    private static String csvEscape(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
