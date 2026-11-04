package com.mo.corecraft.model;

/**
 * **时间戳在接口上的唯一格式**。
 *
 * <p><b>为什么要有这一份</b>：{@code yyyy-MM-dd HH:mm:ss} 这个串原来在 9 个 DTO 的
 * {@code @JsonFormat} + 1 个查询对象的 {@code @DateTimeFormat} + 1 个
 * {@code SimpleDateFormat} 里各写一遍（18 处字面量）。
 *
 * <p><b>为什么它必须处处相同</b>：前端拿这个字符串**按字典序比较**来决定
 * "这条在不在时间窗内"（{@code corecraft-web/src/utils/timeRange.js} 的
 * {@code withinRange}），而服务端那边是 {@code LocalDateTime} 的真实比较 ——
 * 两边格式只要有一个字符不同，分面数字（时间下拉里每项的条数）和列表实际条数
 * 就会对不上，**而界面上什么异常都看不到**。
 *
 * <p><b>口径</b>：**本地墙钟时间，不带时区**。库里 {@code create_time} 存的就是本地时间，
 * 接口也原样给出去 ⇒ 两端比的是同一个东西，不会因为时区换算差一天。
 *
 * <p>⚠️ {@link #TIMEZONE} 对 {@code LocalDateTime} **其实不生效**（LocalDateTime 不带时区，
 * Jackson 直接忽略它）—— 留着是为了口径统一、别让人以为它在做什么；别把它当成"现在改成
 * 某个时区就生效"。
 *
 * <p>⚠️ **前端也有一份同样的串**（{@code utils/timeRange.js} 的 {@code stamp()}）——
 * 跨栈没法共用一份代码，那边**只在这一处**；两边要改就一起改（这里 ↔ 那里）。
 */
public final class DateTimeFormats {

    /** 时间的唯一格式：{@code yyyy-MM-dd HH:mm:ss}。 */
    public static final String DATETIME = "yyyy-MM-dd HH:mm:ss";

    /** 时区名（对 LocalDateTime 不生效，见类注释）。 */
    public static final String TIMEZONE = "Asia/Shanghai";

    private DateTimeFormats() {
    }
}
