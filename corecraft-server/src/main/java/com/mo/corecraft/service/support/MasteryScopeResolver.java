package com.mo.corecraft.service.support;

import com.mo.corecraft.model.query.SysDictItemQuery;
import com.mo.corecraft.model.query.SysDictTypeQuery;
import com.mo.corecraft.model.resp.SysDictItemResp;
import com.mo.corecraft.service.SysDictItemService;
import com.mo.corecraft.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「参与掌握度」判据的**读取端**（2026-11-12）。
 *
 * <p>把「类型默认值」从字典表里读出来，交给 {@link MasteryScope} 判。
 * <b>判据本身只有 {@link MasteryScope} 一处</b>；这里只负责取数。
 *
 * <p>为什么要单独一个组件：三处消费方（列表卡片 / 目录聚合 / 索引）都要这个判据，
 * 各写一遍"去字典表查默认值"就是三份会漂的取数代码。
 */
@Component
@RequiredArgsConstructor
public class MasteryScopeResolver {

    /** 知识点类型所在的字典编码：{@link KpDictCodes#TOPIC_TYPE}（后端唯一一份） */

    private final SysDictTypeService sysDictTypeService;
    private final SysDictItemService sysDictItemService;

    /**
     * 类型默认值表：字典项 id → 该类型默认是否参与掌握度。
     *
     * <p>只取**启用中**的字典项（停用的类型界面上不出现，不该继续参与统计）。
     * 查不到字典类型 ⇒ 空表 ⇒ 全部走安全兜底（不参与）。
     */
    public Map<Long, Boolean> typeDefaults() {
        SysDictTypeQuery typeQuery = new SysDictTypeQuery();
        typeQuery.setCode(KpDictCodes.TOPIC_TYPE);
        var dictType = sysDictTypeService.selectSysDictTypeList(typeQuery).stream().findFirst().orElse(null);
        if (dictType == null) {
            return Map.of();
        }
        SysDictItemQuery itemQuery = new SysDictItemQuery();
        itemQuery.setDictTypeId(dictType.getId());
        itemQuery.setEnabled(true);
        List<SysDictItemResp> items = sysDictItemService.selectSysDictItemList(itemQuery);
        Map<Long, Boolean> raw = new HashMap<>();
        for (SysDictItemResp item : items) {
            raw.put(item.getId(), item.getMasteryEnabled());
        }
        return MasteryScope.typeDefaults(raw);
    }
}
