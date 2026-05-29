package com.mo.corecraft.controller.system;

import com.mo.corecraft.model.query.SysDictTypeQuery;
import com.mo.corecraft.model.req.SysDictTypeCreateReq;
import com.mo.corecraft.model.req.SysDictTypeUpdateReq;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.model.resp.SysDictTypeResp;
import com.mo.corecraft.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dict/type")
@RequiredArgsConstructor
public class SysDictTypeController {

    private final SysDictTypeService sysDictTypeService;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResultResp<SysDictTypeResp> sysDictType(SysDictTypeQuery query) {
        return ResultResp.data(sysDictTypeService.selectSysDictType(query));
    }

    @RequestMapping(value = "list", method = RequestMethod.GET)
    public ResultResp<List<SysDictTypeResp>> sysDictTypeList(SysDictTypeQuery query) {
        return ResultResp.data(sysDictTypeService.selectSysDictTypeList(query));
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    public ResultResp<Void> createSysDictType(@Validated @RequestBody SysDictTypeCreateReq req) {
        sysDictTypeService.createSysDictType(req);
        return ResultResp.success();
    }

    @RequestMapping(value = "", method = RequestMethod.PUT)
    public ResultResp<Void> updateSysDictType(@Validated @RequestBody SysDictTypeUpdateReq req) {
        sysDictTypeService.updateSysDictType(req);
        return ResultResp.success();
    }

    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    public ResultResp<Void> deleteSysDictType(@PathVariable("id") Long id) {
        sysDictTypeService.deleteSysDictType(id);
        return ResultResp.success();
    }
}
