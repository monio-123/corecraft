package com.mo.corecraft.controller.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.query.SysRoleQuery;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.model.resp.SysRoleResp;
import com.mo.corecraft.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResultResp<SysRoleResp> sysRole(SysRoleQuery query) {
        return ResultResp.data(sysRoleService.selectSysRole(query));
    }

    @RequestMapping(value = "list", method = RequestMethod.GET)
    public ResultResp<List<SysRoleResp>> sysRoleList(SysRoleQuery query) {
        return ResultResp.data(sysRoleService.selectSysRoleList(query));
    }

    @RequestMapping(value = "page", method = RequestMethod.GET)
    public ResultResp<IPage<SysRoleResp>> sysRolePage(SysRoleQuery query,
                                                   @RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return ResultResp.data(sysRoleService.selectSysRolePage(Page.of(page, pageSize), query));
    }

}
