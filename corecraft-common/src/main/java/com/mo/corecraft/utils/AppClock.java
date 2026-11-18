package com.mo.corecraft.utils;

import com.mo.corecraft.model.DateTimeFormats;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 统一应用时钟：**固定 {@link DateTimeFormats#TIMEZONE}（Asia/Shanghai），不跟 JVM 默认时区或远端 MySQL 时钟走**。
 *
 * <p>远端 MySQL 与部分容器 JVM 处于 UTC，若调用无参 {@code LocalDateTime.now()} 或 SQL {@code NOW()}，
 * 写入的时间戳会比北京时间慢 8 小时；全站统一走本类保证口径一致。
 */
public final class AppClock {

    public static final ZoneId ZONE = ZoneId.of(DateTimeFormats.TIMEZONE);

    private AppClock() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
