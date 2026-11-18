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
