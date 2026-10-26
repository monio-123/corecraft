package com.mo.corecraft.controller.system;

import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.req.SysConfigSaveReq;
import com.mo.corecraft.model.resp.MyConfigResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.model.resp.SysConfigResp;
import com.mo.corecraft.service.SysConfigService;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置（KV）：系统默认 + 每个人的覆盖值。
 *
 * **路径分两段**：/mine 是用户自己的（登录即可，userId 一律从登录态取，不接受前端传参——
 * 传了就能改别人的设置）；不带 /mine 的是管理端，要 config:api 权限码。
 *
 * **两端共用同一个 DTO、同一段落库代码**：用户端存自己的覆盖值，管理端存系统默认，
 * 区别只是"写到谁的 user_id 下"。
 */
@RestController
@RequestMapping("/sys/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;

    /** 我的配置：生效值 + 系统默认原值 */
    @RequestMapping(value = "mine", method = RequestMethod.GET)
    public ResultResp<MyConfigResp> myConfig(@RequestParam(required = false) String prefix) {
        return ResultResp.data(sysConfigService.myConfig(SecurityUtil.getUserId(), prefix));
    }

    /** 存我的覆盖值（前端拖完滑块一次性提交多个键） */
    @RequestMapping(value = "mine", method = RequestMethod.PUT)
    public ResultResp<Void> saveMine(@Validated @RequestBody SysConfigSaveReq req) {
        sysConfigService.save(SecurityUtil.getUserId(), req);
        return ResultResp.success();
    }

    /** 清掉我的覆盖值 = 恢复系统默认 */
    @RequestMapping(value = "mine", method = RequestMethod.DELETE)
    public ResultResp<Void> deleteMine(@RequestParam("prefix") String prefix) {
        if (StringUtils.isBlank(prefix)) {
            throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "要清空的键前缀不能为空!");
        }
        sysConfigService.deleteMine(SecurityUtil.getUserId(), prefix);
        return ResultResp.success();
    }

    /** 管理端：查配置行。scope=system 看系统默认，scope=user 看各用户的覆盖 */
    @RequestMapping(value = "list", method = RequestMethod.GET)
    @PreAuthorize("@auth.hasPermission('config:api')")
    public ResultResp<List<SysConfigResp>> list(@RequestParam(value = "scope", defaultValue = "system") String scope,
                                                @RequestParam(required = false) Long userId,
                                                @RequestParam(required = false) String prefix) {
        if ("system".equals(scope)) {
            return ResultResp.data(sysConfigService.listSystem(prefix));
        }
        if ("user".equals(scope)) {
            return ResultResp.data(sysConfigService.listUser(userId, prefix));
        }
        throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "查询范围只能是 system 或 user!");
    }

    /** 管理端：改系统默认（user_id=0） */
    @RequestMapping(value = "", method = RequestMethod.PUT)
    @PreAuthorize("@auth.hasPermission('config:api')")
    public ResultResp<Void> saveSystem(@Validated @RequestBody SysConfigSaveReq req) {
        sysConfigService.save(SysConfigService.SYSTEM_USER_ID, req);
        return ResultResp.success();
    }

    /** 管理端：删一行。不传 userId = 删系统默认那行 */
    @RequestMapping(value = "", method = RequestMethod.DELETE)
    @PreAuthorize("@auth.hasPermission('config:api')")
    public ResultResp<Void> deleteOne(@RequestParam("key") String key,
                                       @RequestParam(required = false) Long userId) {
        if (StringUtils.isBlank(key)) {
            throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "要删的配置键不能为空!");
        }
        sysConfigService.deleteOne(key, userId);
        return ResultResp.success();
    }

}
