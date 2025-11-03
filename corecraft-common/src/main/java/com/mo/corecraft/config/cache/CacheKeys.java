package com.mo.corecraft.config.cache;

import com.mo.corecraft.DO.SysUser;

public class CacheKeys {

    public static final CacheKey<String> TEST =
            CacheKey.of("meta-resource-provider", "EmailService", "test", String.class);

    public static final CacheKey<SysUser> SYS_USER =
            CacheKey.of("security", SysUser.class);

}
