package com.mo.corecraft.config.interceptor;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.mo.corecraft.config.security.resource.SecurityUser;
import com.mo.corecraft.model.dto.SysUserDTO;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Method;
import java.sql.Connection;

@Slf4j
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class DataPermissionInterceptor implements InnerInterceptor {

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        MetaObject metaObject = SystemMetaObject.forObject(sh);
        BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
        String originalSql = boundSql.getSql();

        // 只拦 SELECT
        if (!originalSql.trim().toLowerCase().startsWith("select")) {
            return;
        }

        // 判断是否需要忽略
        if (isIgnoreDataScope(metaObject)) {
            log.debug("跳过数据权限控制: {}", originalSql);
            return;
        }

        // 获取用户数据范围
        SecurityUser user = SecurityUtil.getUser();
        if (user == null) return;
        SysUserDTO sysUserDTO = user.getSysUserDTO();
        if (sysUserDTO != null && sysUserDTO.getDeptId() != null) {
            String newSql = "SELECT * FROM (" + originalSql + ") tmp WHERE tmp.dept_id = (" +
                    sysUserDTO.getDeptId() + ")";
            metaObject.setValue("delegate.boundSql.sql", newSql);
            log.debug("数据权限过滤后 SQL: {}", newSql);
        }
    }

    /**
     * 判断 Mapper 方法上是否有 @IgnoreDataScope 注解
     */
    private boolean isIgnoreDataScope(MetaObject metaObject) {
        try {
            MappedStatement ms = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
            String id = ms.getId(); // 全限定方法名：com.xxx.mapper.UserMapper.selectUserList
            String className = id.substring(0, id.lastIndexOf('.'));
            String methodName = id.substring(id.lastIndexOf('.') + 1);
            Class<?> clazz = Class.forName(className);

            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName)
                        && method.isAnnotationPresent(IgnoreDataScope.class)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("检查 @IgnoreDataScope 注解失败", e);
        }
        return false;
    }
}
