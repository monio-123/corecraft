package com.mo.corecraft.config.security.resource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
// 启用方法级权限控制
@EnableMethodSecurity
public class ResourceServerConfig {
    @Bean
    @Order(1) // 必须高于默认的 WebSecurityConfig（通常 Order=100）
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()) // 启用 JWT 验证
                )
                .formLogin(Customizer.withDefaults());
        return http.build();
    }
}