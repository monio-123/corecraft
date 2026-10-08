package com.mo.corecraft.service;

import com.mo.corecraft.model.query.SysDictItemQuery;
import com.mo.corecraft.model.req.SysDictItemCreateReq;
import com.mo.corecraft.model.req.SysDictItemUpdateReq;
import com.mo.corecraft.model.resp.SysDictItemResp;

import java.util.List;

public interface SysDictItemService {

    SysDictItemResp selectSysDictItem(SysDictItemQuery query);

    List<SysDictItemResp> selectSysDictItemList(SysDictItemQuery query);

    void createSysDictItem(SysDictItemCreateReq req);

    void updateSysDictItem(SysDictItemUpdateReq req);

    void deleteSysDictItem(Long id);
}
