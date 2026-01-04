package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mo.corecraft.config.security.resource.SecurityUserRecord;
import com.mo.corecraft.model.dto.SysUserDTO;
import com.mo.corecraft.model.entity.SysUser;
import com.mo.corecraft.config.security.resource.SecurityUser;
import com.mo.corecraft.config.cache.CacheKeys;
import com.mo.corecraft.config.cache.CacheManagerFacade;
import com.mo.corecraft.mapper.SysUserMapper;
import com.mo.corecraft.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
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
        SecurityUserRecord securityUserRecord = CacheManagerFacade.getIfAbsent(
                CacheKeys.SECURITY_USER_RECORD,
                () -> baseMapper.selectByUsername(username));
        Optional.ofNullable(securityUserRecord)
                .orElseThrow(() -> new OAuth2AuthenticationException(new
                        OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, String.format("user %s not found", username), "")));
        return Optional.of(securityUserRecord).map(v -> {
                    SysUserDTO sysUserDTO = BeanUtils.createFrom(securityUserRecord, SysUserDTO.class);
                    return SecurityUser.builder()
                            .sysUserDTO(sysUserDTO)
                            .password(securityUserRecord.getPassword())
                            .authorities(securityUserRecord.getAuthorities())
                            .permissions(securityUserRecord.getPermissions())
                            .build();
                })
                .orElseThrow(() -> new OAuth2AuthenticationException(new
                OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, String.format("user %s not found", username), "")));
    }
}
