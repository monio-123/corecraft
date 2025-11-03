package com.mo.corecraft.controller;

import com.mo.corecraft.DTO.SysUserDTO;
import com.mo.corecraft.utils.SecurityUtil;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class testController {

    @RequestMapping("/hello")
    public String hello() {
        SysUserDTO user = SecurityUtil.getUser();
        Authentication authentication = SecurityUtil.getAuthentication();
        System.out.println(user);
        System.out.println(authentication);
        return "Hello World";
    }
}
