package com.mo.corecraft.config.security.resource;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;

@Getter
@Setter
public class SecurityUserRecord {

    private Long id;
    private String username;
    private String password;

    private Collection<SimpleGrantedAuthority> authorities;
    private Collection<String> permissions;
}
