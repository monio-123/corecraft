package com.mo.corecraft.utils;

public class CacheHelper {

    public static String buildCacheKey(String className, String methodName, Object... params){
        StringBuilder sb = new StringBuilder();
        sb.append(className)
                .append(":")
                .append(methodName);
        for (Object param : params) {
            if (param == null) continue;
            if (param instanceof String || param instanceof Number || param instanceof Boolean) {
                sb.append(":").append(param);
            } else {
                sb.append(":").append(JsonUtils.toJsonString(param));
            }
        }
        return sb.toString();
    }
}
