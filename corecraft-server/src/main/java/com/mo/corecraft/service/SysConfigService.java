package com.mo.corecraft.service;

import com.mo.corecraft.model.req.SysConfigSaveReq;
import com.mo.corecraft.model.resp.MyConfigResp;
import com.mo.corecraft.model.resp.SysConfigResp;

import java.util.List;

/**
 * 系统配置（KV）。
 *
 * **方法一律带 userId 参数**，由 controller 从登录态取。service 不碰 SecurityUtil——
 * 那是 web 层的事，它只管业务。
 */
public interface SysConfigService {

    /** 系统默认行所在的 user_id：0 = 平台级默认，不属于任何用户 */
    Long SYSTEM_USER_ID = 0L;

    /** 我的配置：生效值（我的覆盖 > 系统默认）+ 系统默认原值 */
    MyConfigResp myConfig(Long userId, String prefix);

    /**
     * 按 (user_id, config_key) upsert。
     * userId 传当前登录用户 = 存我的覆盖值；传 {@link #SYSTEM_USER_ID} = 改系统默认。
     */
    void save(Long userId, SysConfigSaveReq req);

    /** 删我的覆盖值 = 恢复系统默认 */
    void deleteMine(Long userId, String prefix);

    /** 管理端：系统默认那几行 */
    List<SysConfigResp> listSystem(String prefix);

    /** 管理端：各用户的覆盖行；userId 给了就再按它过滤 */
    List<SysConfigResp> listUser(Long userId, String prefix);

    /** 管理端：删一行（不传 userId = 删系统默认那行） */
    void deleteOne(String key, Long userId);

    /**
     * 系统默认那一个键的值（user_id=0）；没有这一行返回 null。
     *
     * **给"值本身是配置"的地方用**（比如 AI 提示词）：
     * 它们不是界面偏好，是系统级的一段配置文本，管理员在「系统配置」里改。
     */
    String getSystemValue(String key);
}
