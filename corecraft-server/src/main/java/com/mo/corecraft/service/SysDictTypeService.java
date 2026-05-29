package com.mo.corecraft.service;

import com.mo.corecraft.model.query.SysDictTypeQuery;
import com.mo.corecraft.model.req.SysDictTypeCreateReq;
import com.mo.corecraft.model.req.SysDictTypeUpdateReq;
import com.mo.corecraft.model.resp.SysDictTypeResp;

import java.util.List;

public interface SysDictTypeService {

    SysDictTypeResp selectSysDictType(SysDictTypeQuery query);

    List<SysDictTypeResp> selectSysDictTypeList(SysDictTypeQuery query);

    void createSysDictType(SysDictTypeCreateReq req);

    void updateSysDictType(SysDictTypeUpdateReq req);

    void deleteSysDictType(Long id);
}
