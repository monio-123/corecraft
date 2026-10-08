package com.mo.corecraft.config.interceptor;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 注册数据权限插件
        interceptor.addInnerInterceptor(new DataPermissionInterceptor());
        // 注册分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

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
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
            }
        };
    }
}
