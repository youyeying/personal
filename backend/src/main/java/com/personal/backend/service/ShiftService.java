package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.dto.ShiftBatchRequest;
import com.personal.backend.entity.ShiftRecord;
import com.personal.backend.mapper.ShiftRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 班表 Service：一个班期（21日 → 次月20日）批量保存 + 按日期范围查询
 */
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRecordMapper shiftRecordMapper;
    private final OperationLogService operationLogService;

    /**
     * 批量保存一个班期：按 (user, date) 有则更新、无则插入
     */
    @Transactional
    public Map<String, Object> batchSave(ShiftBatchRequest request) {
        Long userId = UserContext.requireUserId();
        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();

        if (end.isBefore(start)) {
            throw new BizException("班期结束日期不能早于开始日期");
        }

        int created = 0;
        int updated = 0;
        for (ShiftBatchRequest.ShiftItem item : request.getShifts()) {
            LocalDate date = item.getDate();
            if (date.isBefore(start) || date.isAfter(end)) {
                throw new BizException("班表明细日期超出班期范围：" + date);
            }
            ShiftRecord exist = selectByDate(userId, date);
            if (exist == null) {
                ShiftRecord rec = new ShiftRecord();
                rec.setUserId(userId);
                rec.setShiftDate(date);
                rec.setShiftName(item.getShiftName());
                rec.setNote(item.getNote());
                shiftRecordMapper.insert(rec);
                created++;
            } else {
                exist.setShiftName(item.getShiftName());
                exist.setNote(item.getNote());
                shiftRecordMapper.updateById(exist);
                updated++;
            }
        }

        operationLogService.record("NOTE", "UPDATE", null,
                "上传班表：" + start + " ~ " + end);
        return Map.of("created", created, "updated", updated, "count", request.getShifts().size());
    }

    /** 按日期范围查班表（升序） */
    public List<ShiftRecord> list(LocalDate startDate, LocalDate endDate) {
        Long userId = UserContext.requireUserId();
        LambdaQueryWrapper<ShiftRecord> wrapper = new LambdaQueryWrapper<ShiftRecord>()
                .eq(ShiftRecord::getUserId, userId);
        if (startDate != null) {
            wrapper.ge(ShiftRecord::getShiftDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(ShiftRecord::getShiftDate, endDate);
        }
        wrapper.orderByAsc(ShiftRecord::getShiftDate);
        return shiftRecordMapper.selectList(wrapper);
    }

    private ShiftRecord selectByDate(Long userId, LocalDate date) {
        return shiftRecordMapper.selectOne(
                new LambdaQueryWrapper<ShiftRecord>()
                        .eq(ShiftRecord::getUserId, userId)
                        .eq(ShiftRecord::getShiftDate, date)
                        .last("LIMIT 1"));
    }
}