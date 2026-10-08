package com.mo.corecraft.config.security.authorize;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

public class CustomAuthorizationGrantType {

    //自定义对密码模式的支持，同时AuthorizationGrantType不支持继承，所以这里采用new的方式
    public static final AuthorizationGrantType PASSWORD = new AuthorizationGrantType("password");
}
