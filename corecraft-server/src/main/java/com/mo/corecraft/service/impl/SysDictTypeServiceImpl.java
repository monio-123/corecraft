package com.mo.corecraft.service.impl;

import com.mo.corecraft.model.entity.SysDictType;
import com.mo.corecraft.model.query.SysDictTypeQuery;
import com.mo.corecraft.model.req.SysDictTypeCreateReq;
import com.mo.corecraft.model.req.SysDictTypeUpdateReq;
import com.mo.corecraft.model.resp.SysDictTypeResp;
import com.mo.corecraft.repository.SysDictItemRepository;
import com.mo.corecraft.repository.SysDictTypeRepository;
import com.mo.corecraft.service.SysDictTypeService;
import com.mo.corecraft.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SysDictTypeServiceImpl implements SysDictTypeService {

    private final SysDictTypeRepository sysDictTypeRepository;
    private final SysDictItemRepository sysDictItemRepository;

    @Override
    public SysDictTypeResp selectSysDictType(SysDictTypeQuery query) {
        return sysDictTypeRepository.get(query, BeanUtils.converter(SysDictTypeResp.class));
    }

    @Override
    public List<SysDictTypeResp> selectSysDictTypeList(SysDictTypeQuery query) {
        return sysDictTypeRepository.list(query, BeanUtils.converter(SysDictTypeResp.class));
    }

    @Override
    public void createSysDictType(SysDictTypeCreateReq req) {
        SysDictType entity = new SysDictType();
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setRemark(req.getRemark());
        entity.setEnabled(Optional.ofNullable(req.getEnabled()).orElse(true));
        sysDictTypeRepository.insert(entity);
    }

    @Override
    public void updateSysDictType(SysDictTypeUpdateReq req) {
        sysDictTypeRepository.update(req.getId(), req, (r, e) -> {
            if (StringUtils.isNotBlank(r.getCode())) {
                e.setCode(r.getCode());
            }
            if (StringUtils.isNotBlank(r.getName())) {
                e.setName(r.getName());
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
    public void deleteSysDictType(Long id) {
        sysDictItemRepository.deleteByDictTypeId(id);
        sysDictTypeRepository.delete(id);
    }
}
