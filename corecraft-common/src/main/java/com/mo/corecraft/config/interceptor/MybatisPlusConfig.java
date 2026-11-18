package com.mo.corecraft.config.interceptor;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.mo.corecraft.model.DateTimeFormats;
import com.mo.corecraft.utils.AppClock;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

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
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = AppClock.now();
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                setFieldValByName("updateTime", AppClock.now(), metaObject);
            }
        };
    }
}
