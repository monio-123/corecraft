package com.mo.corecraft.config.security.authorize.password;

import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

import java.util.Map;

@Getter
public class PasswordAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

    private final String username;
    private final String password;

    public PasswordAuthenticationToken(
            AuthorizationGrantType authorizationGrantType,
            Authentication clientPrincipal,
            Map<String, Object> additionalParameters,
            String username,
            String password
    ) {
        super(authorizationGrantType, clientPrincipal, additionalParameters);
        this.username = username;
        this.password = password;
    }

}
