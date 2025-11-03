package com.mo.corecraft.config.security.resource;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mo.corecraft.DO.SysUser;
import com.mo.corecraft.DTO.SysUserDTO;
import com.mo.corecraft.config.cache.CacheKeys;
import com.mo.corecraft.config.cache.CacheManagerFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUserDTO sysUser = CacheManagerFacade.getIfAbsent(
                CacheKeys.SYS_USER,
                () -> baseMapper.selectByUsername(username));
        return Optional.ofNullable(sysUser).orElseThrow(() -> new OAuth2AuthenticationException(new
                OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, String.format("user %s not found", username), "")));
    }
}
