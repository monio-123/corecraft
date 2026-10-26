package com.mo.corecraft.service.impl;

import com.mo.corecraft.model.entity.SysConfig;
import com.mo.corecraft.model.req.SysConfigItemReq;
import com.mo.corecraft.model.req.SysConfigSaveReq;
import com.mo.corecraft.model.resp.MyConfigResp;
import com.mo.corecraft.model.resp.SysConfigResp;
import com.mo.corecraft.repository.SysConfigRepository;
import com.mo.corecraft.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统配置（KV）：系统默认 + 每个人的覆盖值。
 *
 * **用户端和管理端是同一段落库代码**：区别只是写到谁的 user_id 下，
 * 所以 save 只按 userId 分流，不按"是不是管理员"写两套。
 */
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigRepository sysConfigRepository;

    @Override
    public MyConfigResp myConfig(Long userId, String prefix) {
        Map<String, String> defaults = toValueMap(sysConfigRepository.listByUserId(SYSTEM_USER_ID, prefix));
        // 生效值 = 我的覆盖 > 系统默认：拿默认值打底，再用我的覆盖盖上去
        Map<String, String> values = new LinkedHashMap<>(defaults);
        values.putAll(toValueMap(sysConfigRepository.listByUserId(userId, prefix)));

        MyConfigResp resp = new MyConfigResp();
        resp.setValues(values);
        resp.setDefaults(defaults);
        return resp;
    }

    @Override
    public void save(Long userId, SysConfigSaveReq req) {
        List<SysConfigItemReq> items = req.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        // 一次把"这些键现在存不存在"问清楚，后面的每一条就不用再单独查一次
        Set<String> keys = items.stream().map(SysConfigItemReq::getKey).collect(Collectors.toSet());
        Map<String, SysConfig> existing = sysConfigRepository.listByUserIdAndKeys(userId, keys).stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, Function.identity()));

        for (SysConfigItemReq item : items) {
            SysConfig old = existing.get(item.getKey());
            SysConfig entity = new SysConfig();
            entity.setConfigValue(item.getValue());
            // 说明留空 = 沿用原值：改个值不该顺手把管理员写的说明抹掉
            if (StringUtils.isNotBlank(item.getRemark())) {
                entity.setRemark(item.getRemark());
            }

            if (old == null) {
                entity.setUserId(userId);
                entity.setConfigKey(item.getKey());
                sysConfigRepository.insert(entity);
            } else {
                // 只带 id + 改动列去更新，别把查出来的旧值整行写回去（详见 repository 的注释）
                entity.setId(old.getId());
                sysConfigRepository.update(entity);
            }
            // 同一批里出现重复键时，后一条接着改刚写进去的那条，不去撞唯一键
            existing.put(item.getKey(), entity);
        }
    }

    @Override
    public void deleteMine(Long userId, String prefix) {
        sysConfigRepository.deleteByUserIdAndKeyPrefix(userId, prefix);
    }

    @Override
    public List<SysConfigResp> listSystem(String prefix) {
        return sysConfigRepository.listByUserId(SYSTEM_USER_ID, prefix).stream().map(this::toResp).toList();
    }

    @Override
    public List<SysConfigResp> listUser(Long userId, String prefix) {
        return sysConfigRepository.listUserScope(userId, prefix).stream().map(this::toResp).toList();
    }

    @Override
    public void deleteOne(String key, Long userId) {
        // 不传 userId = 删系统默认那行：默认行本来就没有主人
        sysConfigRepository.deleteByUserIdAndKey(userId == null ? SYSTEM_USER_ID : userId, key);
    }

    @Override
    public String getSystemValue(String key) {
        return sysConfigRepository.listByUserIdAndKeys(SYSTEM_USER_ID, List.of(key)).stream()
                .findFirst()
                .map(SysConfig::getConfigValue)
                .orElse(null);
    }

    // ==================== 内部 ====================

    private SysConfigResp toResp(SysConfig c) {
        var resp = new SysConfigResp();
        resp.setId(c.getId());
        resp.setUserId(c.getUserId());
        resp.setKey(c.getConfigKey());
        resp.setValue(c.getConfigValue());
        resp.setRemark(c.getRemark());
        resp.setUpdateTime(c.getUpdateTime());
        return resp;
    }

    /** 一批配置行 → 键值对 */
    private Map<String, String> toValueMap(List<SysConfig> list) {
        Map<String, String> map = new LinkedHashMap<>();
        list.forEach(c -> map.put(c.getConfigKey(), c.getConfigValue()));
        return map;
    }
}
