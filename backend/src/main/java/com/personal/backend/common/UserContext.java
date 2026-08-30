package com.personal.backend.common;

/**
 * 当前登录用户上下文（ThreadLocal），请求内可随时取当前用户
 */
public class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 取当前用户 id，未登录时抛业务异常 */
    public static Long requireUserId() {
        LoginUser user = HOLDER.get();
        if (user == null || user.getId() == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return user.getId();
    }

    /** 请求结束后清理，避免线程复用导致串号 */
    public static void clear() {
        HOLDER.remove();
    }
}
