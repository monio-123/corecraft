package com.mo.corecraft.security.authorize;

import com.mo.corecraft.security.authorize.password.PasswordAuthenticationConverter;
import com.mo.corecraft.security.authorize.password.PasswordAuthenticationProvider;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
public class AuthorizationServerConfig {

    @Bean
    public AuthenticationManager authenticationManager(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            UserDetailsService userDetailsService) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder);
         return builder.build();
    }

    @Bean
    public OAuth2TokenGenerator<?> jwtGenerator(JWKSource<SecurityContext> jwkSource) {
        return new JwtGenerator(new NimbusJwtEncoder(jwkSource));
    }


    /**
     * Security过滤器链,用于协议端点
     * 保护 OAuth2 授权服务器的协议端点（如 /oauth2/authorize、/oauth2/token 等）
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE) //确保 OAuth2 端点安全策略优先生效。
    public SecurityFilterChain authorizationServerSecurityFilterChain (HttpSecurity http, OAuth2TokenGenerator<?> tokenGenerator,
                                                                       UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity (http);
        http
                // 用于授权码模式，当客户端是浏览器发起 HTML 请求时（Accept: text/html），返回 302 重定向去 /login 页面。否则默认返回 401
                .exceptionHandling ((exceptions) -> exceptions
                        .defaultAuthenticationEntryPointFor (
                                new LoginUrlAuthenticationEntryPoint ("/login"),
                                new MediaTypeRequestMatcher (MediaType.TEXT_HTML)
                        )
                );
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                                .accessTokenRequestConverters(
                                        authenticationConverters -> authenticationConverters.add(new PasswordAuthenticationConverter())
                                )
                                .authenticationProviders(
                                        authenticationProviders ->
                                                authenticationProviders.add(
                                                        new PasswordAuthenticationProvider(userDetailsService, tokenGenerator, passwordEncoder))
                                ));
        http.cors(Customizer.withDefaults());
        return http.build ();
    }

    /**
     * 保护授权服务器之外的普通应用端点（如登录页、静态资源等）
     */
    @Bean
    @Order(2) // 比授权服务器晚，早于默认100
    public SecurityFilterChain formLoginSecurityFilterChain(HttpSecurity http, UserDetailsService userDetailsService) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/css/**", "/js/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults())
                .userDetailsService(userDetailsService)
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

//    /**
//     * spring security 默认登录用户密码
//     */
//    @Bean
//    @Primary
//    public UserDetailsService userDetailsService() {
//        UserDetails user = User.builder()
//                .username("user")
//                .password(passwordEncoder().encode("password"))
//                .roles("USER")
//                .build();
//        return new InMemoryUserDetailsManager(user);
//    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }
}
