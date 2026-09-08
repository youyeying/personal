package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.entity.ExerciseItem;
import com.personal.backend.entity.ExpenseCategory;
import com.personal.backend.entity.FoodItem;
import com.personal.backend.mapper.ExerciseItemMapper;
import com.personal.backend.mapper.ExpenseCategoryMapper;
import com.personal.backend.mapper.FoodItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 开发端基础数据模板管理（仅开发账号 user_type=2，拦截器 + 此处双校验）
 * 模板数据 user_id=0（系统全局共享），所有业务用户查询时与本人自定义取并集；
 * 用户的增删改仍是 user_id=自己的id，模板只由开发账号在此维护
 */
@Service
@RequiredArgsConstructor
public class AdminTemplateService {

    /** 全局模板用户标识 */
    private static final Long TEMPLATE_USER_ID = 0L;

    private final FoodItemMapper foodItemMapper;
    private final ExerciseItemMapper exerciseItemMapper;
    private final ExpenseCategoryMapper expenseCategoryMapper;
    private final OperationLogService operationLogService;

    // ===================== 食物模板 =====================

    /** 全部模板食物（user_id=0） */
    public List<FoodItem> listFoods() {
        UserContext.requireAdmin();
        return foodItemMapper.selectList(new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getUserId, TEMPLATE_USER_ID)
                .orderByAsc(FoodItem::getType)
                .orderByAsc(FoodItem::getSortOrder));
    }

    @CacheEvict(cacheNames = "foodItems", allEntries = true)
    public FoodItem createFood(FoodItem item) {
        UserContext.requireAdmin();
        checkFoodName(item.getName(), null);
        item.setId(null);
        item.setUserId(TEMPLATE_USER_ID);
        item.setFavorite(false);
        foodItemMapper.insert(item);
        operationLogService.record("ADMIN", "CREATE", item.getId(), "新增模板食物：" + item.getName());
        return item;
    }

    @CacheEvict(cacheNames = "foodItems", allEntries = true)
    public FoodItem updateFood(Long id, FoodItem item) {
        UserContext.requireAdmin();
        FoodItem exist = getFood(id);
        if (StringUtils.hasText(item.getName())) {
            checkFoodName(item.getName(), id);
            exist.setName(item.getName());
        }
        if (item.getKcal() != null) exist.setKcal(item.getKcal());
        if (item.getProtein() != null) exist.setProtein(item.getProtein());
        if (item.getFat() != null) exist.setFat(item.getFat());
        if (item.getCarbs() != null) exist.setCarbs(item.getCarbs());
        if (item.getSodium() != null) exist.setSodium(item.getSodium());
        if (item.getFiber() != null) exist.setFiber(item.getFiber());
        if (item.getType() != null) exist.setType(item.getType());
        if (item.getDefaultGrams() != null) exist.setDefaultGrams(item.getDefaultGrams());
        if (item.getUnitLabel() != null) exist.setUnitLabel(item.getUnitLabel());
        if (item.getSortOrder() != null) exist.setSortOrder(item.getSortOrder());
        foodItemMapper.updateById(exist);
        operationLogService.record("ADMIN", "UPDATE", id, "修改模板食物：" + exist.getName());
        return exist;
    }

    @CacheEvict(cacheNames = "foodItems", allEntries = true)
    public void deleteFood(Long id) {
        UserContext.requireAdmin();
        FoodItem exist = getFood(id);
        foodItemMapper.deleteById(id);
        operationLogService.record("ADMIN", "DELETE", id, "删除模板食物：" + exist.getName());
    }

    private FoodItem getFood(Long id) {
        FoodItem item = foodItemMapper.selectById(id);
        if (item == null || !TEMPLATE_USER_ID.equals(item.getUserId())) {
            throw new BizException("模板食物不存在");
        }
        return item;
    }

    private void checkFoodName(String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            throw new BizException("食物名不能为空");
        }
        Long count = foodItemMapper.selectCount(new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getUserId, TEMPLATE_USER_ID)
                .eq(FoodItem::getName, name)
                .ne(excludeId != null, FoodItem::getId, excludeId));
        if (count > 0) {
            throw new BizException("模板中已存在同名食物");
        }
    }

    // ===================== 动作模板 =====================

    /** 全部模板动作（user_id=0） */
    public List<ExerciseItem> listExercises() {
        UserContext.requireAdmin();
        return exerciseItemMapper.selectList(new LambdaQueryWrapper<ExerciseItem>()
                .eq(ExerciseItem::getUserId, TEMPLATE_USER_ID)
                .orderByAsc(ExerciseItem::getSortOrder));
    }

    @CacheEvict(cacheNames = "exerciseItems", allEntries = true)
    public ExerciseItem createExercise(ExerciseItem item) {
        UserContext.requireAdmin();
        checkExerciseName(item.getName(), null);
        item.setId(null);
        item.setUserId(TEMPLATE_USER_ID);
        exerciseItemMapper.insert(item);
        operationLogService.record("ADMIN", "CREATE", item.getId(), "新增模板动作：" + item.getName());
        return item;
    }

    @CacheEvict(cacheNames = "exerciseItems", allEntries = true)
    public ExerciseItem updateExercise(Long id, ExerciseItem item) {
        UserContext.requireAdmin();
        ExerciseItem exist = getExercise(id);
        if (StringUtils.hasText(item.getName())) {
            checkExerciseName(item.getName(), id);
            exist.setName(item.getName());
        }
        if (item.getType() != null) exist.setType(item.getType());
        if (item.getBaseMet() != null) exist.setBaseMet(item.getBaseMet());
        if (item.getRefSpeed() != null) exist.setRefSpeed(item.getRefSpeed());
        if (item.getMaxSpeed() != null) exist.setMaxSpeed(item.getMaxSpeed());
        if (item.getHasWeight() != null) exist.setHasWeight(item.getHasWeight());
        if (item.getHasHand() != null) exist.setHasHand(item.getHasHand());
        if (item.getSortOrder() != null) exist.setSortOrder(item.getSortOrder());
        exerciseItemMapper.updateById(exist);
        operationLogService.record("ADMIN", "UPDATE", id, "修改模板动作：" + exist.getName());
        return exist;
    }

    @CacheEvict(cacheNames = "exerciseItems", allEntries = true)
    public void deleteExercise(Long id) {
        UserContext.requireAdmin();
        ExerciseItem exist = getExercise(id);
        exerciseItemMapper.deleteById(id);
        operationLogService.record("ADMIN", "DELETE", id, "删除模板动作：" + exist.getName());
    }

    private ExerciseItem getExercise(Long id) {
        ExerciseItem item = exerciseItemMapper.selectById(id);
        if (item == null || !TEMPLATE_USER_ID.equals(item.getUserId())) {
            throw new BizException("模板动作不存在");
        }
        return item;
    }

    private void checkExerciseName(String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            throw new BizException("动作名不能为空");
        }
        Long count = exerciseItemMapper.selectCount(new LambdaQueryWrapper<ExerciseItem>()
                .eq(ExerciseItem::getUserId, TEMPLATE_USER_ID)
                .eq(ExerciseItem::getName, name)
                .ne(excludeId != null, ExerciseItem::getId, excludeId));
        if (count > 0) {
            throw new BizException("模板中已存在同名动作");
        }
    }

    // ===================== 分类模板 =====================

    /** 全部模板分类（user_id=0） */
    public List<ExpenseCategory> listCategories() {
        UserContext.requireAdmin();
        return expenseCategoryMapper.selectList(new LambdaQueryWrapper<ExpenseCategory>()
                .eq(ExpenseCategory::getUserId, TEMPLATE_USER_ID)
                .orderByAsc(ExpenseCategory::getType)
                .orderByAsc(ExpenseCategory::getSortOrder));
    }

    public ExpenseCategory createCategory(ExpenseCategory category) {
        UserContext.requireAdmin();
        checkCategoryName(category.getName(), category.getType(), null);
        category.setId(null);
        category.setUserId(TEMPLATE_USER_ID);
        expenseCategoryMapper.insert(category);
        operationLogService.record("ADMIN", "CREATE", category.getId(), "新增模板分类：" + category.getName());
        return category;
    }

    public ExpenseCategory updateCategory(Long id, ExpenseCategory category) {
        UserContext.requireAdmin();
        ExpenseCategory exist = getCategory(id);
        if (StringUtils.hasText(category.getName())) {
            checkCategoryName(category.getName(), category.getType() != null ? category.getType() : exist.getType(), id);
            exist.setName(category.getName());
        }
        if (category.getType() != null) exist.setType(category.getType());
        if (category.getSortOrder() != null) exist.setSortOrder(category.getSortOrder());
        expenseCategoryMapper.updateById(exist);
        operationLogService.record("ADMIN", "UPDATE", id, "修改模板分类：" + exist.getName());
        return exist;
    }

    public void deleteCategory(Long id) {
        UserContext.requireAdmin();
        ExpenseCategory exist = getCategory(id);
        expenseCategoryMapper.deleteById(id);
        operationLogService.record("ADMIN", "DELETE", id, "删除模板分类：" + exist.getName());
    }

    private ExpenseCategory getCategory(Long id) {
        ExpenseCategory category = expenseCategoryMapper.selectById(id);
        if (category == null || !TEMPLATE_USER_ID.equals(category.getUserId())) {
            throw new BizException("模板分类不存在");
        }
        return category;
    }

    private void checkCategoryName(String name, Integer type, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            throw new BizException("分类名不能为空");
        }
        Long count = expenseCategoryMapper.selectCount(new LambdaQueryWrapper<ExpenseCategory>()
                .eq(ExpenseCategory::getUserId, TEMPLATE_USER_ID)
                .eq(ExpenseCategory::getName, name)
                .eq(type != null, ExpenseCategory::getType, type)
                .ne(excludeId != null, ExpenseCategory::getId, excludeId));
        if (count > 0) {
            throw new BizException("模板中已存在同名分类");
        }
    }
}
