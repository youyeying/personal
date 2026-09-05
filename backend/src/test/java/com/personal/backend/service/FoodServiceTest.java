package com.personal.backend.service;

import com.personal.backend.common.UserContext;
import com.personal.backend.common.LoginUser;
import com.personal.backend.entity.FoodItem;
import com.personal.backend.mapper.FoodItemMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FoodService 单元测试（mock Mapper，不连数据库）
 * 覆盖：食物校验（名称/类型/营养空值补 0）与收藏切换参数组装
 */
@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

    @Mock
    private FoodItemMapper itemMapper;
    @Mock
    private OperationLogService operationLogService;

    private FoodService foodService;

    @BeforeEach
    void setUp() {
        foodService = new FoodService(itemMapper, null, null, operationLogService);
        UserContext.set(new LoginUser(1L, "tester"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private FoodItem item(String name, String type) {
        FoodItem item = new FoodItem();
        item.setName(name);
        item.setType(type);
        return item;
    }

    @Test
    @DisplayName("新增食物：营养字段为空时自动补 0")
    void createItemFillsZeroNutrition() {
        FoodItem item = item("杂粮饭", "staple");
        item.setKcal(new BigDecimal("116"));
        // 蛋白/脂肪/碳水/钠/纤维全部留空
        when(itemMapper.insert(any(FoodItem.class))).thenReturn(1);

        FoodItem saved = foodService.createItem(item);

        assertEquals(new BigDecimal("116"), saved.getKcal());
        assertEquals(BigDecimal.ZERO, saved.getProtein());
        assertEquals(BigDecimal.ZERO, saved.getFat());
        assertEquals(BigDecimal.ZERO, saved.getCarbs());
        assertEquals(BigDecimal.ZERO, saved.getFiber());
        assertEquals(0, saved.getSodium());
        assertEquals(1L, saved.getUserId()); // 归属当前登录用户
    }

    @Test
    @DisplayName("新增食物：名称为空抛业务异常")
    void createItemRejectsBlankName() {
        FoodItem item = item("  ", "staple");
        assertThrows(RuntimeException.class, () -> foodService.createItem(item));
    }

    @Test
    @DisplayName("新增食物：名称超过 30 字抛业务异常")
    void createItemRejectsLongName() {
        FoodItem item = item("a".repeat(31), "staple");
        assertThrows(RuntimeException.class, () -> foodService.createItem(item));
    }

    @Test
    @DisplayName("新增食物：未选类型抛业务异常")
    void createItemRejectsMissingType() {
        FoodItem item = item("鸡蛋", null);
        assertThrows(RuntimeException.class, () -> foodService.createItem(item));
    }

    @Test
    @DisplayName("新增食物：默认收藏为 false、排序为 0")
    void createItemDefaults() {
        FoodItem item = item("鸡蛋", "protein");
        item.setKcal(new BigDecimal("144"));
        when(itemMapper.insert(any(FoodItem.class))).thenReturn(1);

        FoodItem saved = foodService.createItem(item);

        assertEquals(Boolean.FALSE, saved.getFavorite());
        assertEquals(0, saved.getSortOrder());
    }

    @Test
    @DisplayName("未登录调用受保护方法抛 401")
    void requireUserIdWhenLoggedOut() {
        UserContext.clear();
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> foodService.createItem(item("鸡蛋", "protein")));
        assertTrue(ex.getMessage().contains("未登录"));
    }
}
