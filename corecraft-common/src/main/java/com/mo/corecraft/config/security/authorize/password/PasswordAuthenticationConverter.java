package com.mo.corecraft.config.security.authorize.password;

import com.mo.corecraft.config.security.authorize.CustomAuthorizationGrantType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.HashMap;
import java.util.Map;

public class PasswordAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!CustomAuthorizationGrantType.PASSWORD.getValue().equals(grantType)) return null;
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> additionalParams = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> additionalParams.put(k, v[0]));
        return new PasswordAuthenticationToken(
                CustomAuthorizationGrantType.PASSWORD,
                clientPrincipal,
                additionalParams,
                username,
                password
        );

    }
}
