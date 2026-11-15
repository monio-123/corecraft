package com.mo.corecraft.service.impl;

import com.mo.corecraft.model.entity.SysDictItem;
import com.mo.corecraft.model.query.SysDictItemQuery;
import com.mo.corecraft.model.req.SysDictItemCreateReq;
import com.mo.corecraft.model.req.SysDictItemUpdateReq;
import com.mo.corecraft.model.resp.SysDictItemResp;
import com.mo.corecraft.repository.SysDictItemRepository;
import com.mo.corecraft.service.SysDictItemService;
import com.mo.corecraft.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SysDictItemServiceImpl implements SysDictItemService {

    private final SysDictItemRepository sysDictItemRepository;

    @Override
    public SysDictItemResp selectSysDictItem(SysDictItemQuery query) {
        return sysDictItemRepository.get(query, BeanUtils.converter(SysDictItemResp.class));
    }

    @Override
    public List<SysDictItemResp> selectSysDictItemList(SysDictItemQuery query) {
        return sysDictItemRepository.list(query, BeanUtils.converter(SysDictItemResp.class));
    }

    @Override
    public void createSysDictItem(SysDictItemCreateReq req) {
        SysDictItem entity = new SysDictItem();
        entity.setDictTypeId(req.getDictTypeId());
        entity.setLabel(req.getLabel());
        entity.setValue(req.getValue());
        entity.setSort(Optional.ofNullable(req.getSort()).orElse(0));
        entity.setCssClass(req.getCssClass());
        entity.setRemark(req.getRemark());
        entity.setEnabled(Optional.ofNullable(req.getEnabled()).orElse(true));
        // 类型默认是否参与掌握度（null = 不配置，走安全兜底；true/false 才落值）
        entity.setMasteryEnabled(req.getMasteryEnabled());
        sysDictItemRepository.insert(entity);
    }

    @Override
    public void updateSysDictItem(SysDictItemUpdateReq req) {
        sysDictItemRepository.update(req.getId(), req, (r, e) -> {
            if (r.getDictTypeId() != null) {
                e.setDictTypeId(r.getDictTypeId());
            }
            if (StringUtils.isNotBlank(r.getLabel())) {
                e.setLabel(r.getLabel());
            }
            if (StringUtils.isNotBlank(r.getValue())) {
                e.setValue(r.getValue());
            }
            if (r.getSort() != null) {
                e.setSort(r.getSort());
            }
            if (r.getCssClass() != null) {
                e.setCssClass(r.getCssClass());
            }
            if (r.getRemark() != null) {
                e.setRemark(r.getRemark());
            }
            // ⚠️ **null = 不修改**（不是"改成继承"）：这一列的 null 是"没配这条规则"，
            // 当成要写就会静默抹掉管理员的设置。SysDictItem.masteryEnabled 用的是
            // MP 默认的 NOT_NULL 策略 ⇒ null 天然不进 UPDATE 语句。
            if (r.getMasteryEnabled() != null) {
                e.setMasteryEnabled(r.getMasteryEnabled());
            }
            if (r.getEnabled() != null) {
                e.setEnabled(r.getEnabled());
            }
        });
    }

    @Override
    public void deleteSysDictItem(Long id) {
        sysDictItemRepository.delete(id);
    }
}
