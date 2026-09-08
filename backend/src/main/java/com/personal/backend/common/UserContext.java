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

    /** 校验当前用户类型（1=业务用户 / 2=开发账号），不匹配抛 403 */
    public static void requireUserType(int type) {
        LoginUser user = HOLDER.get();
        if (user == null || user.getUserType() == null || user.getUserType() != type) {
            throw new BizException(403, "无权访问");
        }
    }

    /** 开发账号（管理员）专用接口校验：userType==2 */
    public static void requireAdmin() {
        requireUserType(2);
    }

    /** 业务用户专用接口校验：userType==1 */
    public static void requireBusinessUser() {
        requireUserType(1);
    }

    /** 当前是否为开发账号（userType==2）；未登录返回 false */
    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && user.getUserType() != null && user.getUserType() == 2;
    }

    /** 请求结束后清理，避免线程复用导致串号 */
    public static void clear() {
        HOLDER.remove();
    }
}
