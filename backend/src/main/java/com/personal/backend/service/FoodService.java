package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.dto.FoodMealTemplateRequest;
import com.personal.backend.dto.FoodRecordQuery;
import com.personal.backend.dto.FoodRecordSaveRequest;
import com.personal.backend.entity.FoodItem;
import com.personal.backend.entity.FoodMealTemplate;
import com.personal.backend.entity.FoodRecord;
import com.personal.backend.mapper.FoodItemMapper;
import com.personal.backend.mapper.FoodMealTemplateMapper;
import com.personal.backend.mapper.FoodRecordMapper;
import com.personal.backend.utils.OwnedUtil;
import com.personal.backend.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 饮食 Service：食物字典（含收藏）+ 饮食记录 CRUD + 整餐模板
 * 营养（热量/蛋白/脂肪/碳水/钠/纤维）由前端按每100g数值 × 份量 ÷ 100 实时计算，
 * 后端只存取食物定义与份量
 */
@Service
@RequiredArgsConstructor
public class FoodService {

    private final FoodItemMapper itemMapper;
    private final FoodRecordMapper recordMapper;
    private final FoodMealTemplateMapper templateMapper;
    private final OperationLogService operationLogService;

    // ================= 食物字典 =================

    /** 查询我的食物字典（收藏优先，再按类型+排序）；@Cacheable 本地缓存 5 分钟，写操作 @CacheEvict 立即失效 */
    @Cacheable(cacheNames = "foodItems", key = "T(com.personal.backend.common.UserContext).requireUserId()")
    public List<FoodItem> listItems() {
        Long userId = UserContext.requireUserId();
        return itemMapper.selectList(new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getUserId, userId)
                .orderByDesc(FoodItem::getFavorite)
                .orderByAsc(FoodItem::getType)
                .orderByAsc(FoodItem::getSortOrder));
    }

    /** 新增自定义食物 */
    @CacheEvict(cacheNames = "foodItems", allEntries = true)
    public FoodItem createItem(FoodItem item) {
        Long userId = UserContext.requireUserId();
        validateItem(item);
        item.setId(null);
        item.setUserId(userId);
        if (item.getSortOrder() == null) item.setSortOrder(0);
        if (item.getFavorite() == null) item.setFavorite(false);
        itemMapper.insert(item);

        operationLogService.record("FOOD", "CREATE", item.getId(), "新增食物：" + item.getName());
        return item;
    }

    /** 修改食物（名称/营养/默认份量/收藏等） */
    public FoodItem updateItem(Long id, FoodItem item) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(itemMapper, id, userId, FoodItem::getUserId, "食物不存在");
        validateItem(item);
        item.setId(id);
        item.setUserId(userId);
        itemMapper.updateById(item);

        operationLogService.record("FOOD", "UPDATE", id, "修改食物：" + item.getName());
        return item;
    }

    /** 收藏/取消收藏 */
    public FoodItem toggleFavorite(Long id, boolean favorite) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(itemMapper, id, userId, FoodItem::getUserId, "食物不存在");
        FoodItem item = new FoodItem();
        item.setId(id);
        item.setFavorite(favorite);
        itemMapper.updateById(item);
        return itemMapper.selectById(id);
    }

    /** 删除食物（软删；不影响已有记录） */
    @CacheEvict(cacheNames = "foodItems", allEntries = true)
    public void deleteItem(Long id) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(itemMapper, id, userId, FoodItem::getUserId, "食物不存在");
        itemMapper.deleteById(id);

        operationLogService.record("FOOD", "DELETE", id, "删除食物");
    }

    // ================= 饮食记录 =================

    /** 分页查询饮食记录（最新在前） */
    public Map<String, Object> page(FoodRecordQuery query) {
        Long userId = UserContext.requireUserId();

        LambdaQueryWrapper<FoodRecord> wrapper = new LambdaQueryWrapper<FoodRecord>()
                .eq(FoodRecord::getUserId, userId);
        if (query.getStartDate() != null) {
            wrapper.ge(FoodRecord::getRecordDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(FoodRecord::getRecordDate, query.getEndDate());
        }
        if (StringUtils.hasText(query.getMealType())) {
            wrapper.eq(FoodRecord::getMealType, query.getMealType());
        }
        wrapper.orderByDesc(FoodRecord::getRecordDate).orderByDesc(FoodRecord::getId);

        Page<FoodRecord> page = recordMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        return PageUtil.ok(page, page.getRecords());
    }

    /** 区间全量记录（统计/分析页前端聚合；按日期升序保证折线顺序） */
    public Map<String, Object> statistics(FoodRecordQuery query) {
        Long userId = UserContext.requireUserId();
        List<FoodRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<FoodRecord>()
                        .eq(FoodRecord::getUserId, userId)
                        .ge(query.getStartDate() != null, FoodRecord::getRecordDate, query.getStartDate())
                        .le(query.getEndDate() != null, FoodRecord::getRecordDate, query.getEndDate())
                        .orderByAsc(FoodRecord::getRecordDate));

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        return result;
    }

    /** 新增饮食记录 */
    public FoodRecord create(FoodRecordSaveRequest req) {
        Long userId = UserContext.requireUserId();
        requireFood(req.getFoodId(), userId);
        if (req.getRecordDate() == null) {
            throw new BizException("饮食日期不能为空");
        }
        if (!StringUtils.hasText(req.getMealType())) {
            throw new BizException("请选择餐次");
        }
        if (req.getGrams() == null || req.getGrams().signum() <= 0) {
            throw new BizException("请输入份量");
        }

        FoodRecord record = new FoodRecord();
        record.setUserId(userId);
        record.setFoodId(req.getFoodId());
        record.setRecordDate(req.getRecordDate());
        record.setMealType(req.getMealType());
        record.setGrams(req.getGrams());
        record.setNote(req.getNote());
        recordMapper.insert(record);

        operationLogService.record("FOOD", "CREATE", record.getId(), "饮食记录：" + req.getMealType());
        return record;
    }

    /** 修改饮食记录 */
    public FoodRecord update(Long id, FoodRecordSaveRequest req) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(recordMapper, id, userId, FoodRecord::getUserId, "饮食记录不存在");
        requireFood(req.getFoodId(), userId);
        if (req.getRecordDate() == null) {
            throw new BizException("饮食日期不能为空");
        }
        if (!StringUtils.hasText(req.getMealType())) {
            throw new BizException("请选择餐次");
        }
        if (req.getGrams() == null || req.getGrams().signum() <= 0) {
            throw new BizException("请输入份量");
        }

        FoodRecord record = new FoodRecord();
        record.setId(id);
        record.setUserId(userId);
        record.setFoodId(req.getFoodId());
        record.setRecordDate(req.getRecordDate());
        record.setMealType(req.getMealType());
        record.setGrams(req.getGrams());
        record.setNote(req.getNote());
        recordMapper.updateById(record);

        operationLogService.record("FOOD", "UPDATE", id, "修改饮食记录");
        return record;
    }

    /** 删除饮食记录 */
    public void delete(Long id) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(recordMapper, id, userId, FoodRecord::getUserId, "饮食记录不存在");
        recordMapper.deleteById(id);

        operationLogService.record("FOOD", "DELETE", id, "删除饮食记录");
    }

    // ================= 整餐模板 =================

    /** 我的模板列表（按排序） */
    public List<FoodMealTemplate> listTemplates() {
        Long userId = UserContext.requireUserId();
        return templateMapper.selectList(new LambdaQueryWrapper<FoodMealTemplate>()
                .eq(FoodMealTemplate::getUserId, userId)
                .orderByAsc(FoodMealTemplate::getSortOrder));
    }

    /** 新增模板 */
    public FoodMealTemplate createTemplate(FoodMealTemplateRequest req) {
        Long userId = UserContext.requireUserId();
        validateTemplate(req);
        FoodMealTemplate t = new FoodMealTemplate();
        t.setUserId(userId);
        t.setName(req.getName());
        t.setItems(req.getItems());
        t.setSortOrder(0);
        templateMapper.insert(t);

        operationLogService.record("FOOD", "CREATE", t.getId(), "新增整餐模板：" + t.getName());
        return t;
    }

    /** 修改模板 */
    public FoodMealTemplate updateTemplate(Long id, FoodMealTemplateRequest req) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(templateMapper, id, userId, FoodMealTemplate::getUserId, "模板不存在");
        validateTemplate(req);
        FoodMealTemplate t = new FoodMealTemplate();
        t.setId(id);
        t.setName(req.getName());
        t.setItems(req.getItems());
        templateMapper.updateById(t);

        operationLogService.record("FOOD", "UPDATE", id, "修改整餐模板：" + t.getName());
        return t;
    }

    /** 删除模板 */
    public void deleteTemplate(Long id) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(templateMapper, id, userId, FoodMealTemplate::getUserId, "模板不存在");
        templateMapper.deleteById(id);

        operationLogService.record("FOOD", "DELETE", id, "删除整餐模板");
    }

    // ================= 私有校验 =================

    private void validateItem(FoodItem item) {
        if (!StringUtils.hasText(item.getName())) {
            throw new BizException("食物名不能为空");
        }
        if (item.getName().length() > 30) {
            throw new BizException("食物名不能超过 30 字");
        }
        if (!StringUtils.hasText(item.getType())) {
            throw new BizException("请选择食物类型");
        }
        if (item.getKcal() == null) {
            item.setKcal(BigDecimal.ZERO);
        }
        if (item.getProtein() == null) item.setProtein(BigDecimal.ZERO);
        if (item.getFat() == null) item.setFat(BigDecimal.ZERO);
        if (item.getCarbs() == null) item.setCarbs(BigDecimal.ZERO);
        if (item.getSodium() == null) item.setSodium(0);
        if (item.getFiber() == null) item.setFiber(BigDecimal.ZERO);
    }

    private void validateTemplate(FoodMealTemplateRequest req) {
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException("模板名不能为空");
        }
        if (req.getName().length() > 20) {
            throw new BizException("模板名不能超过 20 字");
        }
        if (!StringUtils.hasText(req.getItems())) {
            throw new BizException("模板内容不能为空");
        }
    }

    /** 校验食物归属并返回 */
    private FoodItem requireFood(Long foodId, Long userId) {
        FoodItem item = itemMapper.selectById(foodId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BizException("食物不存在");
        }
        return item;
    }
}
