package com.mo.corecraft.controller;

import com.mo.corecraft.DTO.SysUserDTO;
import com.mo.corecraft.utils.SecurityUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class testController {

    @PreAuthorize("@auth.hasRoleOrPermission('ROLE_ADMIN', 'user:add')")
    @RequestMapping("/hello")
    public String hello() {
        SysUserDTO user = SecurityUtil.getUser();
        Authentication authentication = SecurityUtil.getAuthentication();
        System.out.println(user);
        System.out.println(authentication);
        return "Hello World";
    }
}
