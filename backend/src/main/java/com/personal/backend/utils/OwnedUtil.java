package com.personal.backend.utils;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personal.backend.common.BizException;

import java.util.function.Function;

/**
 * 数据归属校验工具（防越权操作他人数据）
 * 消除各 Service 里「selectById + userId 判断 + 抛 BizException」的重复样板
 */
public final class OwnedUtil {

    private OwnedUtil() {
    }

    /**
     * 校验目标记录的归属：存在且属于当前用户才返回，否则抛业务异常
     *
     * @param mapper      对应实体的 MyBatis-Plus Mapper（BaseMapper 提供 selectById）
     * @param id          记录 id
     * @param userId      当前登录用户 id
     * @param userIdGetter 实体 → 其 userId 的取值方法引用（如 XXX::getUserId）
     * @param errMsg      不存在或不属于当前用户时的错误文案
     */
    public static <T> T requireOwned(BaseMapper<T> mapper, Long id, Long userId,
                                     Function<T, Long> userIdGetter, String errMsg) {
        T entity = mapper.selectById(id);
        if (entity == null || !userId.equals(userIdGetter.apply(entity))) {
            throw new BizException(errMsg);
        }
        return entity;
    }
}