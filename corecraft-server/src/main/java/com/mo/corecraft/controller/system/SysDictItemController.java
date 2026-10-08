package com.mo.corecraft.controller.system;

import com.mo.corecraft.model.query.SysDictItemQuery;
import com.mo.corecraft.model.req.SysDictItemCreateReq;
import com.mo.corecraft.model.req.SysDictItemUpdateReq;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.model.resp.SysDictItemResp;
import com.mo.corecraft.service.SysDictItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dict/item")
@RequiredArgsConstructor
public class SysDictItemController {

    private final SysDictItemService sysDictItemService;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResultResp<SysDictItemResp> sysDictItem(SysDictItemQuery query) {
        return ResultResp.data(sysDictItemService.selectSysDictItem(query));
    }

    @RequestMapping(value = "list", method = RequestMethod.GET)
    public ResultResp<List<SysDictItemResp>> sysDictItemList(SysDictItemQuery query) {
        return ResultResp.data(sysDictItemService.selectSysDictItemList(query));
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    public ResultResp<Void> createSysDictItem(@Validated @RequestBody SysDictItemCreateReq req) {
        sysDictItemService.createSysDictItem(req);
        return ResultResp.success();
    }

    @RequestMapping(value = "", method = RequestMethod.PUT)
    public ResultResp<Void> updateSysDictItem(@Validated @RequestBody SysDictItemUpdateReq req) {
        sysDictItemService.updateSysDictItem(req);
        return ResultResp.success();
    }

    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    public ResultResp<Void> deleteSysDictItem(@PathVariable("id") Long id) {
        sysDictItemService.deleteSysDictItem(id);
        return ResultResp.success();
    }
}
