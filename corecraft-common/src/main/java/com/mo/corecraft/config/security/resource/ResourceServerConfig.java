package com.mo.corecraft.config.security.resource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
// 启用方法级权限控制
@EnableMethodSecurity
public class ResourceServerConfig {
    @Bean
    @Order(1) // 必须高于默认的 WebSecurityConfig（通常 Order=100）
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http, UserDetailsService userDetailsService) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // `/files/**` 免鉴权**不是疏忽**：`<img src>` 由浏览器直接发起，
                        // 不会带 Authorization 头（JWT 存在 localStorage，只有 axios 手动加）。
                        // 加上鉴权，所有图片都是裂图。安全性靠文件名是 UUID、不可猜。
                        .requestMatchers("/login", "/files/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter(userDetailsService))
                        )
                );
        return http.build();
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter(UserDetailsService userDetailsService) {
        return jwt -> {
            SecurityUser user = (SecurityUser) userDetailsService.loadUserByUsername(jwt.getSubject());
            return UsernamePasswordAuthenticationToken.authenticated(user, jwt, user.getAuthorities());
        };
    }
}
