package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.dto.ExerciseRecordQuery;
import com.personal.backend.dto.ExerciseRecordSaveRequest;
import com.personal.backend.entity.ExerciseItem;
import com.personal.backend.entity.ExerciseRecord;
import com.personal.backend.entity.WeightRecord;
import com.personal.backend.mapper.ExerciseItemMapper;
import com.personal.backend.mapper.ExerciseRecordMapper;
import com.personal.backend.mapper.WeightRecordMapper;
import com.personal.backend.utils.OwnedUtil;
import com.personal.backend.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 锻炼 Service：动作字典（用户可自定义）+ 锻炼记录 CRUD
 * 消耗大卡/MET 由前端按公式计算，后端只存取原始参数与动作定义
 */
@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseItemMapper itemMapper;
    private final ExerciseRecordMapper recordMapper;
    private final WeightRecordMapper weightRecordMapper;
    private final OperationLogService operationLogService;

    /** 查询我的动作字典（按 sort_order 升序）；@Cacheable 本地缓存 5 分钟，写操作 @CacheEvict 立即失效 */
    @Cacheable(cacheNames = "exerciseItems", key = "T(com.personal.backend.common.UserContext).requireUserId()")
    public List<ExerciseItem> listItems() {
        Long userId = UserContext.requireUserId();
        return itemMapper.selectList(new LambdaQueryWrapper<ExerciseItem>()
                .eq(ExerciseItem::getUserId, userId)
                .orderByAsc(ExerciseItem::getSortOrder));
    }

    /** 新增动作（自定义） */
    @CacheEvict(cacheNames = "exerciseItems", allEntries = true)
    public ExerciseItem createItem(ExerciseItem item) {
        Long userId = UserContext.requireUserId();
        if (!StringUtils.hasText(item.getName())) {
            throw new BizException("动作名不能为空");
        }
        if (item.getName().length() > 20) {
            throw new BizException("动作名不能超过 20 字");
        }
        item.setId(null);
        item.setUserId(userId);
        if (item.getSortOrder() == null) item.setSortOrder(0);
        // 速度上限缺省 = 参考速度 × 3（防自定义动作速度比爆炸）
        if (item.getMaxSpeed() == null && item.getRefSpeed() != null && item.getRefSpeed() > 0) {
            item.setMaxSpeed(item.getRefSpeed() * 3);
        }
        itemMapper.insert(item);

        operationLogService.record("EXERCISE", "CREATE", item.getId(),
                "新增锻炼动作：" + item.getName());
        return item;
    }

    /** 修改动作（名称/基础 MET/参考速度等） */
    public ExerciseItem updateItem(Long id, ExerciseItem item) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(itemMapper, id, userId,
                ExerciseItem::getUserId, "动作不存在");
        if (StringUtils.hasText(item.getName()) && item.getName().length() > 20) {
            throw new BizException("动作名不能超过 20 字");
        }
        item.setId(id);
        item.setUserId(userId);
        itemMapper.updateById(item);

        operationLogService.record("EXERCISE", "UPDATE", id, "修改锻炼动作：" + item.getName());
        return item;
    }

    /** 删除动作（软删；不影响已有记录） */
    @CacheEvict(cacheNames = "exerciseItems", allEntries = true)
    public void deleteItem(Long id) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(itemMapper, id, userId,
                ExerciseItem::getUserId, "动作不存在");
        itemMapper.deleteById(id);

        operationLogService.record("EXERCISE", "DELETE", id, "删除锻炼动作");
    }

    /** 分页查询锻炼记录（最新在前） */
    public Map<String, Object> page(ExerciseRecordQuery query) {
        Long userId = UserContext.requireUserId();

        LambdaQueryWrapper<ExerciseRecord> wrapper = new LambdaQueryWrapper<ExerciseRecord>()
                .eq(ExerciseRecord::getUserId, userId);
        if (query.getStartDate() != null) {
            wrapper.ge(ExerciseRecord::getRecordDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(ExerciseRecord::getRecordDate, query.getEndDate());
        }
        if (query.getExerciseId() != null) {
            wrapper.eq(ExerciseRecord::getExerciseId, query.getExerciseId());
        }
        wrapper.orderByDesc(ExerciseRecord::getRecordDate).orderByDesc(ExerciseRecord::getId);

        Page<ExerciseRecord> page = recordMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        return PageUtil.ok(page, page.getRecords());
    }

    /** 锻炼统计：今日/本周/本月净消耗 + 连续天数 + 近 14 天趋势（原始记录，前端算大卡） */
    public Map<String, Object> statistics() {
        Long userId = UserContext.requireUserId();
        List<ExerciseRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<ExerciseRecord>()
                        .eq(ExerciseRecord::getUserId, userId)
                        .orderByAsc(ExerciseRecord::getRecordDate));

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        return result;
    }

    /** 某动作的最近一条记录（打卡页「上次」带出） */
    public ExerciseRecord latest(Long exerciseId) {
        Long userId = UserContext.requireUserId();
        return recordMapper.selectOne(new LambdaQueryWrapper<ExerciseRecord>()
                .eq(ExerciseRecord::getUserId, userId)
                .eq(ExerciseRecord::getExerciseId, exerciseId)
                .orderByDesc(ExerciseRecord::getRecordDate)
                .orderByDesc(ExerciseRecord::getId)
                .last("LIMIT 1"));
    }

    /** 记录日期当天或之前的最新体重（锻炼记录体重快照，历史消耗固定不随当前体重变） */
    private BigDecimal latestWeightBefore(LocalDate date) {
        Long userId = UserContext.requireUserId();
        WeightRecord w = weightRecordMapper.selectOne(new LambdaQueryWrapper<WeightRecord>()
                .eq(WeightRecord::getUserId, userId)
                .le(WeightRecord::getRecordDate, date)
                .orderByDesc(WeightRecord::getRecordDate)
                .orderByDesc(WeightRecord::getId)
                .last("LIMIT 1"));
        return w == null ? null : w.getWeight();
    }

    /** 新增锻炼记录 */
    public ExerciseRecord create(ExerciseRecordSaveRequest req) {
        Long userId = UserContext.requireUserId();
        ExerciseItem item = requireItem(req.getExerciseId(), userId);
        if (req.getRecordDate() == null) {
            throw new BizException("锻炼日期不能为空");
        }
        validateByType(item, req);

        ExerciseRecord record = new ExerciseRecord();
        record.setUserId(userId);
        record.setExerciseId(req.getExerciseId());
        record.setRecordDate(req.getRecordDate());
        record.setWeight(req.getWeight());
        record.setReps(req.getReps());
        record.setMinutes(req.getMinutes());
        record.setDistance(req.getDistance());
        record.setFloors(req.getFloors());
        record.setTimes(req.getTimes());
        record.setSeconds(req.getSeconds());
        record.setHand(req.getHand());
        record.setNote(req.getNote());
        // 体重快照：按记录日期取当时最新体重，历史消耗固定
        record.setBodyWeight(latestWeightBefore(req.getRecordDate()));
        recordMapper.insert(record);

        operationLogService.record("EXERCISE", "CREATE", record.getId(),
                "锻炼记录：" + item.getName());
        return record;
    }

    /** 修改锻炼记录 */
    public ExerciseRecord update(Long id, ExerciseRecordSaveRequest req) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(recordMapper, id, userId,
                ExerciseRecord::getUserId, "锻炼记录不存在");
        ExerciseItem item = requireItem(req.getExerciseId(), userId);
        if (req.getRecordDate() == null) {
            throw new BizException("锻炼日期不能为空");
        }
        validateByType(item, req);

        ExerciseRecord record = new ExerciseRecord();
        record.setId(id);
        record.setUserId(userId);
        record.setExerciseId(req.getExerciseId());
        record.setRecordDate(req.getRecordDate());
        record.setWeight(req.getWeight());
        record.setReps(req.getReps());
        record.setMinutes(req.getMinutes());
        record.setDistance(req.getDistance());
        record.setFloors(req.getFloors());
        record.setTimes(req.getTimes());
        record.setSeconds(req.getSeconds());
        record.setHand(req.getHand());
        record.setNote(req.getNote());
        // 体重快照：改日期后按新日期重新取当时最新体重
        record.setBodyWeight(latestWeightBefore(req.getRecordDate()));
        recordMapper.updateById(record);

        operationLogService.record("EXERCISE", "UPDATE", id, "修改锻炼记录：" + item.getName());
        return record;
    }

    /** 删除锻炼记录 */
    public void delete(Long id) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(recordMapper, id, userId,
                ExerciseRecord::getUserId, "锻炼记录不存在");
        recordMapper.deleteById(id);

        operationLogService.record("EXERCISE", "DELETE", id, "删除锻炼记录");
    }

    /** 按类型校验必填字段 */
    private void validateByType(ExerciseItem item, ExerciseRecordSaveRequest req) {
        switch (item.getType()) {
            case "strength", "cardio" -> {
                if (req.getReps() == null || req.getReps() <= 0) throw new BizException("请输入个数");
                // 时长：前端将「分钟+秒」合并为总秒数存 seconds（兼容旧数据 minutes）
                boolean hasDuration = (req.getSeconds() != null && req.getSeconds() > 0)
                        || (req.getMinutes() != null && req.getMinutes().signum() > 0);
                if (!hasDuration) throw new BizException("请输入这组花费的时长");
            }
            case "plank" -> {
                if (req.getSeconds() == null || req.getSeconds() <= 0) throw new BizException("请输入撑了几秒");
            }
            case "walk" -> {
                if (req.getDistance() == null || req.getDistance().signum() <= 0) throw new BizException("请输入走了多远");
                if (req.getMinutes() == null || req.getMinutes().signum() <= 0) throw new BizException("请输入花了多久");
            }
            case "cycling" -> {
                if (req.getDistance() == null || req.getDistance().signum() <= 0) throw new BizException("请输入骑了多远");
                if (req.getMinutes() == null || req.getMinutes().signum() <= 0) throw new BizException("请输入骑了多久");
            }
            case "stairs" -> {
                if (req.getFloors() == null || req.getFloors() <= 0) throw new BizException("请输入一次爬几层");
                if (req.getTimes() == null || req.getTimes() <= 0) throw new BizException("请输入爬了几次");
                // 时长：前端将「分钟+秒」合并为总秒数存 seconds（兼容旧数据 minutes）
                boolean hasDuration = (req.getSeconds() != null && req.getSeconds() > 0)
                        || (req.getMinutes() != null && req.getMinutes().signum() > 0);
                if (!hasDuration) throw new BizException("请输入爬楼梯用时的时长");
            }
            default -> throw new BizException("动作类型无效");
        }
    }

    /** 校验动作归属并返回 */
    private ExerciseItem requireItem(Long exerciseId, Long userId) {
        ExerciseItem item = itemMapper.selectById(exerciseId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BizException("锻炼动作不存在");
        }
        return item;
    }
}