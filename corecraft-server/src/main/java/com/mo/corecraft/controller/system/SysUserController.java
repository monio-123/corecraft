package com.mo.corecraft.controller.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.query.SysUserQuery;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.model.resp.SysUserResp;
import com.mo.corecraft.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResultResp<SysUserResp> sysUser(SysUserQuery query) {
        return ResultResp.data(sysUserService.selectSysUser(query));
    }

    @RequestMapping(value = "list", method = RequestMethod.GET)
    public ResultResp<List<SysUserResp>> sysUserList(SysUserQuery query) {
        return ResultResp.data(sysUserService.selectSysUserList(query));
    }

    @RequestMapping(value = "page", method = RequestMethod.GET)
    public ResultResp<IPage<SysUserResp>> sysUserPage(SysUserQuery query,
                                                   @RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return ResultResp.data(sysUserService.selectSysUserPage(Page.of(page, pageSize), query));
    }

}
