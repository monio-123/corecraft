package com.mo.corecraft.config.cache;

import com.mo.corecraft.DTO.SysUserDTO;

public class CacheKeys {

    public static final CacheKey<String> TEST =
            CacheKey.of("meta-resource-provider", "EmailService", "test", String.class);

    public static final CacheKey<SysUserDTO> SYS_USER =
            CacheKey.of("security", SysUserDTO.class);

}
