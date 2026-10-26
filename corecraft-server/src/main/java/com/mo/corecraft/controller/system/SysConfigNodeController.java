package com.mo.corecraft.controller.system;

import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.req.SysConfigNodeSaveReq;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.model.resp.SysConfigNodeResp;
import com.mo.corecraft.service.SysConfigNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 配置树（KV 的分组与说明书）。
 *
 * **读 = 登录即可**：树是元数据（有哪些项、叫什么、范围多大），不含值。
 * 业务侧的偏好面板也要读它（面板上那几行叫什么、能拖到多少，是它决定的）。
 *
 * **写 = 管理端**：要 config:api 权限码，和「系统配置」页改值用同一个码。
 */
@RestController
@RequestMapping("/sys/config/node")
@RequiredArgsConstructor
public class SysConfigNodeController {

    private final SysConfigNodeService sysConfigNodeService;

    /** 整棵配置树（扁平的，前端自己按 parentId 挂） */
    @RequestMapping(value = "list", method = RequestMethod.GET)
    public ResultResp<List<SysConfigNodeResp>> list() {
        return ResultResp.data(sysConfigNodeService.listAll());
    }

    /** 新建一个节点 */
    @RequestMapping(value = "", method = RequestMethod.POST)
    @PreAuthorize("@auth.hasPermission('config:api')")
    public ResultResp<Long> create(@Validated @RequestBody SysConfigNodeSaveReq req) {
        return ResultResp.data(sysConfigNodeService.create(req));
    }

    /** 改一个节点（短名 / 父节点 / 值类型不可改） */
    @RequestMapping(value = "", method = RequestMethod.PUT)
    @PreAuthorize("@auth.hasPermission('config:api')")
    public ResultResp<Void> update(@Validated @RequestBody SysConfigNodeSaveReq req) {
        if (req.getId() == null) {
            throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "要改哪一个节点？id 不能为空!");
        }
        sysConfigNodeService.update(req.getId(), req);
        return ResultResp.success();
    }

    /** 删一个节点（连它的子树和它们各自的键值对一起删） */
    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    @PreAuthorize("@auth.hasPermission('config:api')")
    public ResultResp<Void> delete(@PathVariable("id") Long id) {
        sysConfigNodeService.delete(id);
        return ResultResp.success();
    }
}
