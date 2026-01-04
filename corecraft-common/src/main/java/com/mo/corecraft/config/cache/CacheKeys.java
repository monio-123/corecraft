package com.mo.corecraft.config.cache;

import com.mo.corecraft.config.security.resource.SecurityUser;
import com.mo.corecraft.config.security.resource.SecurityUserRecord;

public class CacheKeys {

    public static final CacheKey<String> TEST =
            CacheKey.of("meta-resource-provider", "EmailService", "test", String.class);

    public static final CacheKey<SecurityUserRecord> SECURITY_USER_RECORD =
            CacheKey.of("security", SecurityUserRecord.class);

}
