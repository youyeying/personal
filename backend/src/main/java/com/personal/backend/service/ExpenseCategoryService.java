package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.entity.ExpenseCategory;
import com.personal.backend.mapper.ExpenseCategoryMapper;
import com.personal.backend.utils.OwnedUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 收支分类 Service：当前用户的分类列表 / 新增 / 修改 / 删除
 */
@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    private final ExpenseCategoryMapper categoryMapper;
    private final OperationLogService operationLogService;

    /** 查询当前用户分类列表，按类型 + 排序 */
    public List<ExpenseCategory> list(Integer type) {
        Long userId = UserContext.requireUserId();
        LambdaQueryWrapper<ExpenseCategory> wrapper = new LambdaQueryWrapper<ExpenseCategory>()
                .eq(ExpenseCategory::getUserId, userId);
        if (type != null) {
            wrapper.eq(ExpenseCategory::getType, type);
        }
        wrapper.orderByAsc(ExpenseCategory::getType).orderByAsc(ExpenseCategory::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    /** 新增分类：同用户同类型下名称不重复 */
    public ExpenseCategory create(ExpenseCategory category) {
        Long userId = UserContext.requireUserId();
        checkNameUnique(userId, category.getName(), category.getType(), null);

        category.setId(null);
        category.setUserId(userId);
        categoryMapper.insert(category);

        operationLogService.record("EXPENSE", "CREATE", category.getId(),
                "新增分类：" + category.getName());
        return category;
    }

    /** 修改分类（名称 / 排序） */
    @CacheEvict(cacheNames = "expenseCategories", allEntries = true)
    public ExpenseCategory update(ExpenseCategory category) {
        Long userId = UserContext.requireUserId();
        ExpenseCategory exist = OwnedUtil.requireOwned(categoryMapper, category.getId(), userId,
                ExpenseCategory::getUserId, "分类不存在");
        checkNameUnique(userId, category.getName(), exist.getType(), category.getId());

        exist.setName(category.getName());
        if (category.getSortOrder() != null) {
            exist.setSortOrder(category.getSortOrder());
        }
        categoryMapper.updateById(exist);

        operationLogService.record("EXPENSE", "UPDATE", exist.getId(),
                "修改分类：" + exist.getName());
        return exist;
    }

    /** 删除分类（软删除） */
    public void delete(Long id) {
        Long userId = UserContext.requireUserId();
        ExpenseCategory exist = OwnedUtil.requireOwned(categoryMapper, id, userId,
                ExpenseCategory::getUserId, "分类不存在");
        categoryMapper.deleteById(id);

        operationLogService.record("EXPENSE", "DELETE", id,
                "删除分类：" + exist.getName());
    }

    /** 同用户同类型下名称唯一校验 */
    private void checkNameUnique(Long userId, String name, Integer type, Long excludeId) {
        LambdaQueryWrapper<ExpenseCategory> wrapper = new LambdaQueryWrapper<ExpenseCategory>()
                .eq(ExpenseCategory::getUserId, userId)
                .eq(ExpenseCategory::getName, name)
                .eq(ExpenseCategory::getType, type);
        if (excludeId != null) {
            wrapper.ne(ExpenseCategory::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BizException("同类型下已存在同名分类：" + name);
        }
    }
}
