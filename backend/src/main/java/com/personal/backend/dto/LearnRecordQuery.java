package com.personal.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 学习记录查询参数
 */
@Data
public class LearnRecordQuery {

    /** 学习方式：阅读/视频/课程/实践/其他 */
    private String way;

    /** 关键词（模糊匹配主题/笔记） */
    private String keyword;

    /** 开始日期 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 结束日期 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** 页码 */
    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer size = 20;
}
