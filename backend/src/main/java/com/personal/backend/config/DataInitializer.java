package com.personal.backend.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.backend.entity.ExerciseItem;
import com.personal.backend.entity.ExpenseCategory;
import com.personal.backend.entity.User;
import com.personal.backend.mapper.ExerciseItemMapper;
import com.personal.backend.mapper.ExpenseCategoryMapper;
import com.personal.backend.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动数据初始化：用户表为空时创建初始管理员账号
 * （schema.sql 已预置 user_id=1 的默认分类，初始用户自增 id=1 与之对应）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final ExpenseCategoryMapper expenseCategoryMapper;
    private final ExerciseItemMapper exerciseItemMapper;

    @Override
    public void run(String... args) {
        Long count = userMapper.selectCount(null);
        if (count > 0) {
            return;
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(encoder.encode("admin123"));
        admin.setPhone("13800138000");
        admin.setNickname("系统管理员");
        userMapper.insert(admin);

        // 校验默认分类是否存在，缺失则补建（防止 schema.sql 未预置时系统不可用）
        List<ExpenseCategory> defaults = expenseCategoryMapper.selectList(
                new LambdaQueryWrapper<ExpenseCategory>().eq(ExpenseCategory::getUserId, 1L));
        if (defaults.isEmpty()) {
            insertDefaultCategories();
        }

        // 校验默认锻炼动作是否存在，缺失则补建（schema.sql 已预置，双保险）
        List<ExerciseItem> items = exerciseItemMapper.selectList(
                new LambdaQueryWrapper<ExerciseItem>().eq(ExerciseItem::getUserId, 1L));
        if (items.isEmpty()) {
            insertDefaultExercises();
        }

        log.info("初始账号已创建：admin / admin123（首次登录后请尽快修改密码）");
    }

    /** 补建默认分类（与 schema.sql 预置一致） */
    private void insertDefaultCategories() {
        String[][] expense = {
                {"餐饮", "1", "1"}, {"交通", "1", "2"}, {"购物", "1", "3"},
                {"居住", "1", "4"}, {"娱乐", "1", "5"}, {"医疗", "1", "6"},
                {"学习", "1", "7"}, {"人情", "1", "8"}, {"其他", "1", "9"}
        };
        String[][] income = {
                {"工资", "2", "1"}, {"副业", "2", "2"}, {"理财", "2", "3"},
                {"红包", "2", "4"}, {"其他", "2", "5"}
        };
        for (String[] row : expense) {
            insertCategory(row[0], Integer.valueOf(row[1]), Integer.valueOf(row[2]));
        }
        for (String[] row : income) {
            insertCategory(row[0], Integer.valueOf(row[1]), Integer.valueOf(row[2]));
        }
    }

    private void insertCategory(String name, int type, int sortOrder) {
        ExpenseCategory c = new ExpenseCategory();
        c.setUserId(1L);
        c.setName(name);
        c.setType(type);
        c.setSortOrder(sortOrder);
        expenseCategoryMapper.insert(c);
    }

    /** 补建默认锻炼动作（与 schema.sql 预置一致，双保险） */
    private void insertDefaultExercises() {
        Object[][] rows = {
                {"床上平躺举哑铃", "strength", "3.5", 12, true, true},
                {"平肩俯卧撑", "strength", "4.0", 15, false, false},
                {"臀桥", "strength", "3.5", 15, false, false},
                {"臂力棒", "strength", "3.0", 10, true, false},
                {"平板支撑", "plank", "4.0", null, false, false},
                {"散步", "walk", "0.0", null, false, false},
                {"爬楼梯", "stairs", "8.0", null, false, false}
        };
        int order = 1;
        for (Object[] row : rows) {
            ExerciseItem e = new ExerciseItem();
            e.setUserId(1L);
            e.setName((String) row[0]);
            e.setType((String) row[1]);
            e.setBaseMet(new java.math.BigDecimal((String) row[2]));
            e.setRefSpeed(row[3] == null ? null : Integer.valueOf((String) row[3]));
            e.setHasWeight((Boolean) row[4]);
            e.setHasHand((Boolean) row[5]);
            e.setSortOrder(order++);
            exerciseItemMapper.insert(e);
        }
    }
}
