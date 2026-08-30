package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.dto.WeightRecordQuery;
import com.personal.backend.entity.WeightRecord;
import com.personal.backend.mapper.WeightRecordMapper;
import com.personal.backend.utils.OwnedUtil;
import com.personal.backend.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 体重记录 Service：打卡 / 查询 / 趋势 / 目标进度
 */
@Service
@RequiredArgsConstructor
public class WeightRecordService {

    private final WeightRecordMapper recordMapper;
    private final OperationLogService operationLogService;

    /** 分页查询（最新在前） */
    public Map<String, Object> page(WeightRecordQuery query) {
        Long userId = UserContext.requireUserId();

        LambdaQueryWrapper<WeightRecord> wrapper = new LambdaQueryWrapper<WeightRecord>()
                .eq(WeightRecord::getUserId, userId);
        if (query.getStartDate() != null) {
            wrapper.ge(WeightRecord::getRecordDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(WeightRecord::getRecordDate, query.getEndDate());
        }
        wrapper.orderByDesc(WeightRecord::getRecordDate).orderByDesc(WeightRecord::getId);

        Page<WeightRecord> page = recordMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        return PageUtil.ok(page, page.getRecords());
    }

    /** 打卡：新增一条体重记录 */
    public WeightRecord create(WeightRecord record) {
        Long userId = UserContext.requireUserId();
        validate(record);

        record.setId(null);
        record.setUserId(userId);
        recordMapper.insert(record);

        operationLogService.record("WEIGHT", "CREATE", record.getId(),
                "体重打卡：" + record.getWeight() + "kg");
        return record;
    }

    /** 修改体重记录 */
    public WeightRecord update(WeightRecord record) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(recordMapper, record.getId(), userId,
                WeightRecord::getUserId, "体重记录不存在");
        validate(record);

        record.setUserId(userId);
        recordMapper.updateById(record);

        operationLogService.record("WEIGHT", "UPDATE", record.getId(),
                "修改体重记录：" + record.getWeight() + "kg");
        return record;
    }

    /** 删除体重记录 */
    public void delete(Long id) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(recordMapper, id, userId,
                WeightRecord::getUserId, "体重记录不存在");
        recordMapper.deleteById(id);

        operationLogService.record("WEIGHT", "DELETE", id, "删除体重记录");
    }

    /** 体重趋势：日期升序的体重/体脂/腰围序列，供折线图 */
    public Map<String, Object> trend() {
        Long userId = UserContext.requireUserId();
        List<WeightRecord> list = recordMapper.selectList(
                new LambdaQueryWrapper<WeightRecord>()
                        .eq(WeightRecord::getUserId, userId)
                        .orderByAsc(WeightRecord::getRecordDate));

        List<String> dates = list.stream().map(r -> r.getRecordDate().toString()).toList();
        List<BigDecimal> weights = list.stream().map(WeightRecord::getWeight).toList();
        List<BigDecimal> bodyFats = list.stream().map(WeightRecord::getBodyFat).toList();
        List<BigDecimal> waists = list.stream().map(WeightRecord::getWaist).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("weights", weights);
        result.put("bodyFats", bodyFats);
        result.put("waists", waists);
        return result;
    }

    /** 校验：体重 > 0 */
    private void validate(WeightRecord record) {
        if (record.getWeight() == null
                || record.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("体重必须大于 0");
        }
        if (record.getRecordDate() == null) {
            throw new BizException("记录日期不能为空");
        }
    }
}
