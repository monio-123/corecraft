package com.mo.corecraft.config.interceptor;


public class UserContext {
    private static final ThreadLocal<UserDataScope> THREAD_LOCAL = new ThreadLocal<>();

    public static void set(UserDataScope scope) {
        THREAD_LOCAL.set(scope);
    }

    public static UserDataScope get() {
        return THREAD_LOCAL.get();
    }

    public static void clear() {
        THREAD_LOCAL.remove();
    }
}


