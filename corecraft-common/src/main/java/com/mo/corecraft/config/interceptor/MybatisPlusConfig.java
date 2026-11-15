package com.mo.corecraft.config.interceptor;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.mo.corecraft.model.DateTimeFormats;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 注册分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 填时间戳用的时区：**固定 {@link DateTimeFormats#TIMEZONE}（Asia/Shanghai），不跟 JVM 默认时区走**。
     *
     * <p>2026-11-09 踩坑实录：原来这里写的是 {@code LocalDateTime.now()}，值跟 JVM 默认时区 ——
     * 服务器容器的 JVM 是 UTC，于是 update_time 被写成比北京时间差 8 小时的值
     * （界面上「更新于」少了 8 小时；DB 时钟是 CST、JDBC 链路是干净的，都排查过）。
     * 时间戳的口径是「本地墙钟（北京时间）不带时区」（见 {@link DateTimeFormats} 类注释），
     * 所以这里必须显式给 {@link ZoneId}，**谁都别再把它改回无参 {@code now()}**。
     *
     * <p>⚠️ 历史行的时间戳没有回修（2026-11-09 用户拍板「历史不动」）——
     * 旧条目显示的时间仍差 8 小时属预期，从本次发版起新写入的才对。
     */
    private static final ZoneId ZONE = ZoneId.of(DateTimeFormats.TIMEZONE);

    /**
     * create_time / update_time 自动填。实体侧在字段上标 {@code @TableField(fill = ...)}，这里只管给值。
     *
     * <p>update 必须用无条件覆盖，不能用 {@code strictUpdateFill}。更新走的是
     * {@code selectById → 改字段 → updateById(entity)}，实体是从库里查出来的，
     * {@code updateTime} 本来就非 null，strict 只会因为"字段已有值"直接跳过。
     *
     * <p>而且 DDL 里的 {@code ON UPDATE CURRENT_TIMESTAMP} 在这里同样救不了：
     * MyBatis-Plus 默认把非 null 字段全写进 SET，于是 SQL 变成
     * {@code SET ..., update_time='旧值'} —— 显式赋了值，MySQL 就不会自动更新时间。
     * 两头都不动，时间戳永远停在几个月前。所以这一处必须是显式赋新值。
     *
     * <p>insert 侧相反：字段是 null，strict 才对，调用方显式 set 过的时间要能保住。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now(ZONE);
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                setFieldValByName("updateTime", LocalDateTime.now(ZONE), metaObject);
            }
        };
    }
}
