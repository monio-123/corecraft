package com.mo.corecraft.controller.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.query.SysPermissionQuery;
import com.mo.corecraft.model.req.SysPermissionCreateReq;
import com.mo.corecraft.model.req.SysPermissionUpdateReq;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.model.resp.SysPermissionResp;
import com.mo.corecraft.service.SysPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/permission")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysPermissionService sysPermissionService;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResultResp<SysPermissionResp> sysPermission(SysPermissionQuery query) {
        return ResultResp.data(sysPermissionService.selectSysPermission(query));
    }

    @RequestMapping(value = "list", method = RequestMethod.GET)
    public ResultResp<List<SysPermissionResp>> sysPermissionList(SysPermissionQuery query) {
        return ResultResp.data(sysPermissionService.selectSysPermissionList(query));
    }

    @RequestMapping(value = "page", method = RequestMethod.GET)
    public ResultResp<IPage<SysPermissionResp>> sysPermissionPage(SysPermissionQuery query,
                                                            @RequestParam(defaultValue = "1") Integer page,
                                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ResultResp.data(sysPermissionService.selectSysPermissionPage(Page.of(page, pageSize), query));
    }

    @RequestMapping(value = "tree", method = RequestMethod.GET)
    public ResultResp<List<SysPermissionResp>> sysPermissionTree(SysPermissionQuery query) {
        return ResultResp.data(sysPermissionService.selectSysPermissionTree(query));
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    public ResultResp<Void> createSysPermission(@Validated @RequestBody SysPermissionCreateReq req) {
        sysPermissionService.createSysPermission(req);
        return ResultResp.success();
    }

    @RequestMapping(value = "", method = RequestMethod.PUT)
    public ResultResp<Void> updateSysPermission(@Validated @RequestBody SysPermissionUpdateReq req) {
        sysPermissionService.updateSysPermission(req);
        return ResultResp.success();
    }

    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    public ResultResp<Void> deleteSysPermission(@PathVariable("id") Long id) {
        sysPermissionService.deleteSysPermission(id);
        return ResultResp.success();
    }

    @RequestMapping(value = "batch", method = RequestMethod.DELETE)
    public ResultResp<Void> deleteBatchSysPermission(@RequestParam List<Long> ids) {
        sysPermissionService.deleteBatchSysPermission(ids);
        return ResultResp.success();
    }

}
