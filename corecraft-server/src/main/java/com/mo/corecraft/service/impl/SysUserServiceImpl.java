package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.config.cache.CacheKeys;
import com.mo.corecraft.config.cache.CacheManagerFacade;
import com.mo.corecraft.config.storage.LocalStorageProperties;
import com.mo.corecraft.config.security.resource.SecurityUser;
import com.mo.corecraft.model.entity.SysUser;
import com.mo.corecraft.model.query.SysUserQuery;
import com.mo.corecraft.model.req.SysUserCreateReq;
import com.mo.corecraft.model.req.SysUserPasswordUpdateReq;
import com.mo.corecraft.model.req.SysUserProfileUpdateReq;
import com.mo.corecraft.model.req.SysUserUpdateReq;
import com.mo.corecraft.model.resp.SysUserProfileResp;
import com.mo.corecraft.model.resp.SysUserResp;
import com.mo.corecraft.repository.SysRoleRepository;
import com.mo.corecraft.repository.SysUserRepository;
import com.mo.corecraft.repository.SysUserRoleRepository;
import com.mo.corecraft.service.SysUserService;
import com.mo.corecraft.utils.BeanUtils;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService{

    private final SysUserRepository sysUserRepository;

    private final SysUserRoleRepository sysUserRoleRepository;

    private final SysRoleRepository sysRoleRepository;

    private final PasswordEncoder passwordEncoder;

    private final LocalStorageProperties localStorageProperties;

    @Override
    public SysUserResp selectSysUser(SysUserQuery query) {
        return sysUserRepository.selectOne(query);
    }

    @Override
    public List<SysUserResp> selectSysUserList(SysUserQuery query) {
        return sysUserRepository.selectList(query);
    }

    @Override
    public IPage<SysUserResp> selectSysUserPage(Page<SysUser> page, SysUserQuery query) {
        return sysUserRepository.selectPage(page, query);
    }

    @Override
    public void createSysUser(SysUserCreateReq req) {
        req.validatePasswordEqual();
        req.setPassword(passwordEncoder.encode(req.getPassword()));
        sysUserRepository.insert(BeanUtils.createFrom(req, SysUser.class));
    }

    @Override
    public void updateSysUser(SysUserUpdateReq req) {
        sysUserRepository.update(req.getId(), req, BeanUtils::copyTo);
    }

    @Override
    public void assignUserRole(Long userId, Long roleId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        SysUser user = sysUserRepository.entityById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (roleId != null && sysRoleRepository.entityById(roleId) == null) {
            throw new RuntimeException("角色不存在");
        }
        sysUserRoleRepository.saveUserRole(userId, roleId);
        CacheManagerFacade.delete(CacheKeys.SECURITY_USER_RECORD, user.getUsername());
    }

    @Override
    public SysUserProfileResp currentUserProfile() {
        SecurityUser user = requireCurrentUser();
        SysUser entity = sysUserRepository.entityById(user.getSysUserDTO().getId());
        SysUserProfileResp resp = BeanUtils.createFrom(entity, SysUserProfileResp.class);
        resp.setRoles(SecurityUtil.getRoles().stream().toList());
        resp.setPermissions(SecurityUtil.getPermissions());
        return resp;
    }

    @Override
    public void updateCurrentUserProfile(SysUserProfileUpdateReq req) {
        SecurityUser user = requireCurrentUser();
        sysUserRepository.update(user.getSysUserDTO().getId(), req, (r, e) -> {
            if (r.getNickname() != null) {
                e.setNickname(r.getNickname());
            }
            if (r.getEmail() != null) {
                e.setEmail(r.getEmail());
            }
            if (r.getMobile() != null) {
                e.setMobile(r.getMobile());
            }
            if (r.getAvatar() != null) {
                e.setAvatar(r.getAvatar());
            }
        });
    }

    @Override
    public void updateCurrentUserPassword(SysUserPasswordUpdateReq req) {
        String oldPassword = req.getOldPassword();
        String newPassword = req.getNewPassword();
        String confirmPassword = req.getConfirmPassword();
        if (!StringUtils.equals(newPassword, confirmPassword)) {
            throw new RuntimeException("新密码和确认密码不一致");
        }
        SecurityUser user = requireCurrentUser();
        SysUser entity = sysUserRepository.entityById(user.getSysUserDTO().getId());
        if (entity == null || !passwordEncoder.matches(oldPassword, entity.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }
        entity.setPassword(passwordEncoder.encode(newPassword));
        sysUserRepository.update(entity);
    }

    @Override
    public String uploadCurrentUserAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("头像文件不能为空");
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String suffix = ".png";
            if (StringUtils.isNotBlank(originalFilename) && originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf('.'));
            }
            String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;
            Path dir = Path.of(localStorageProperties.getAvatarDir());
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            String avatarPath = "/api/files/avatars/" + fileName;
            SysUserProfileUpdateReq req = new SysUserProfileUpdateReq();
            req.setAvatar(avatarPath);
            updateCurrentUserProfile(req);
            return avatarPath;
        } catch (Exception e) {
            throw new RuntimeException("头像上传失败", e);
        }
    }

    @Override
    public void deleteSysUser(Long id) {
        SysUser user = sysUserRepository.entityById(id);
        sysUserRoleRepository.deleteByUserId(id);
        sysUserRepository.delete(id);
        if (user != null) {
            CacheManagerFacade.delete(CacheKeys.SECURITY_USER_RECORD, user.getUsername());
        }
    }

    @Override
    public void deleteBatchSysUser(List<Long> ids) {
        if (ids != null) {
            ids.forEach(sysUserRoleRepository::deleteByUserId);
        }
        sysUserRepository.deleteBatch(ids);
        CacheManagerFacade.clear(CacheKeys.SECURITY_USER_RECORD);
    }

    private SecurityUser requireCurrentUser() {
        SecurityUser user = SecurityUtil.getUser();
        if (!user.isAuthenticated()) {
            throw new RuntimeException("未获取到当前登录用户");
        }
        return user;
    }

}
