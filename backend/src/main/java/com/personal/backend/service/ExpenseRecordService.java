package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.dto.ExpenseRecordQuery;
import com.personal.backend.entity.ExpenseCategory;
import com.personal.backend.entity.ExpenseRecord;
import com.personal.backend.mapper.ExpenseCategoryMapper;
import com.personal.backend.mapper.ExpenseRecordMapper;
import com.personal.backend.utils.OwnedUtil;
import com.personal.backend.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收支记录 Service：增删改查 + 统计
 */
@Service
@RequiredArgsConstructor
public class ExpenseRecordService {

    private final ExpenseRecordMapper recordMapper;
    private final ExpenseCategoryMapper categoryMapper;
    private final OperationLogService operationLogService;

    /** 分页查询（附带分类名，供前端展示） */
    public Map<String, Object> page(ExpenseRecordQuery query) {
        Long userId = UserContext.requireUserId();

        LambdaQueryWrapper<ExpenseRecord> wrapper = buildWrapper(query, userId);
        wrapper.orderByDesc(ExpenseRecord::getRecordDate).orderByDesc(ExpenseRecord::getId);

        Page<ExpenseRecord> page = recordMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        // 批量补分类名，避免 N+1
        Map<Long, String> categoryNames = loadCategoryNames(userId);

        List<Map<String, Object>> records = page.getRecords().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("type", r.getType());
            m.put("categoryId", r.getCategoryId());
            m.put("categoryName", categoryNames.getOrDefault(r.getCategoryId(), ""));
            m.put("amount", r.getAmount());
            m.put("note", r.getNote());
            m.put("recordDate", r.getRecordDate());
            m.put("createdAt", r.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        return PageUtil.ok(page, records);
    }

    /** 新增收支记录 */
    public ExpenseRecord create(ExpenseRecord record) {
        Long userId = UserContext.requireUserId();
        validate(record, userId);

        record.setId(null);
        record.setUserId(userId);
        recordMapper.insert(record);

        operationLogService.record("EXPENSE", "CREATE", record.getId(),
                buildContent(record));
        return record;
    }

    /** 修改收支记录 */
    public ExpenseRecord update(ExpenseRecord record) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(recordMapper, record.getId(), userId,
                ExpenseRecord::getUserId, "记账记录不存在");
        validate(record, userId);

        record.setUserId(userId);
        recordMapper.updateById(record);

        operationLogService.record("EXPENSE", "UPDATE", record.getId(),
                buildContent(record));
        return record;
    }

    /** 删除收支记录（软删除） */
    public void delete(Long id) {
        Long userId = UserContext.requireUserId();
        ExpenseRecord exist = OwnedUtil.requireOwned(recordMapper, id, userId,
                ExpenseRecord::getUserId, "记账记录不存在");
        recordMapper.deleteById(id);

        operationLogService.record("EXPENSE", "DELETE", id,
                "删除记账：" + buildContent(exist));
    }

    /** 收支统计：按日期范围汇总总支出 / 总收入 / 分类汇总 */
    public Map<String, Object> statistics(ExpenseRecordQuery query) {
        Long userId = UserContext.requireUserId();

        List<ExpenseRecord> all = recordMapper.selectList(buildWrapper(query, userId));

        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;
        Map<Long, BigDecimal> expenseByCategory = new HashMap<>();
        Map<Long, BigDecimal> incomeByCategory = new HashMap<>();

        for (ExpenseRecord r : all) {
            if (r.getType() == 1) {
                totalExpense = totalExpense.add(r.getAmount());
                expenseByCategory.merge(r.getCategoryId(), r.getAmount(), BigDecimal::add);
            } else {
                totalIncome = totalIncome.add(r.getAmount());
                incomeByCategory.merge(r.getCategoryId(), r.getAmount(), BigDecimal::add);
            }
        }

        Map<Long, String> categoryNames = loadCategoryNames(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("totalExpense", totalExpense);
        result.put("totalIncome", totalIncome);
        result.put("balance", totalIncome.subtract(totalExpense));
        result.put("expenseByCategory", toCategoryStat(expenseByCategory, categoryNames));
        result.put("incomeByCategory", toCategoryStat(incomeByCategory, categoryNames));
        return result;
    }

    /** 组装查询条件 */
    private LambdaQueryWrapper<ExpenseRecord> buildWrapper(ExpenseRecordQuery query, Long userId) {
        LambdaQueryWrapper<ExpenseRecord> wrapper = new LambdaQueryWrapper<ExpenseRecord>()
                .eq(ExpenseRecord::getUserId, userId);
        if (query.getType() != null) {
            wrapper.eq(ExpenseRecord::getType, query.getType());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(ExpenseRecord::getCategoryId, query.getCategoryId());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(ExpenseRecord::getRecordDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(ExpenseRecord::getRecordDate, query.getEndDate());
        }
        return wrapper;
    }

    /** 校验：金额 > 0、分类归属当前用户 */
    private void validate(ExpenseRecord record, Long userId) {
        if (record.getAmount() == null || record.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("金额必须大于 0");
        }
        if (record.getType() == null || (record.getType() != 1 && record.getType() != 2)) {
            throw new BizException("类型必须为 1(支出) 或 2(收入)");
        }
        if (record.getRecordDate() == null) {
            throw new BizException("记账日期不能为空");
        }
        ExpenseCategory category = categoryMapper.selectById(record.getCategoryId());
        if (category == null || !category.getUserId().equals(userId)) {
            throw new BizException("分类不存在");
        }
        if (!category.getType().equals(record.getType())) {
            throw new BizException("分类与收支类型不匹配");
        }
    }

    /** 批量加载分类名 */
    private Map<Long, String> loadCategoryNames(Long userId) {
        return categoryMapper.selectList(
                        new LambdaQueryWrapper<ExpenseCategory>().eq(ExpenseCategory::getUserId, userId))
                .stream().collect(Collectors.toMap(ExpenseCategory::getId, ExpenseCategory::getName));
    }

    /** 记录内容：如 "餐饮 -25.00" / "工资 +8000.00" */
    private String buildContent(ExpenseRecord record) {
        Map<Long, String> names = loadCategoryNames(record.getUserId());
        String name = names.getOrDefault(record.getCategoryId(), "");
        String prefix = record.getType() == 1 ? "-" : "+";
        return name + " " + prefix + record.getAmount();
    }

    /** 分类统计转展示结构 */
    private List<Map<String, Object>> toCategoryStat(Map<Long, BigDecimal> stat,
                                                     Map<Long, String> names) {
        return stat.entrySet().stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("categoryId", e.getKey());
            m.put("categoryName", names.getOrDefault(e.getKey(), ""));
            m.put("amount", e.getValue());
            return m;
        }).collect(Collectors.toList());
    }
}
