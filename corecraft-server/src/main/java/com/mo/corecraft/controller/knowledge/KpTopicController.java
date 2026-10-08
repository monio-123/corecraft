package com.mo.corecraft.controller.knowledge;

import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.req.KpTopicCreateReq;
import com.mo.corecraft.model.req.KpTopicUpdateReq;
import com.mo.corecraft.model.resp.KpTopicResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.KpTopicService;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kp/topic")
@RequiredArgsConstructor
public class KpTopicController {

    private final KpTopicService kpTopicService;

    @RequestMapping(value = "list", method = RequestMethod.GET)
    public ResultResp<List<KpTopicResp>> list(KpTopicQuery query) {
        Long userId = SecurityUtil.getUser().getSysUserDTO().getId();
        query.setUserId(userId);
        return ResultResp.data(kpTopicService.selectTopicList(query));
    }

    @RequestMapping(value = "{id}", method = RequestMethod.GET)
    public ResultResp<KpTopicResp> detail(@PathVariable("id") Long id) {
        return ResultResp.data(kpTopicService.selectTopicById(id));
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    public ResultResp<Void> create(@Validated @RequestBody KpTopicCreateReq req) {
        Long userId = SecurityUtil.getUser().getSysUserDTO().getId();
        kpTopicService.createTopic(req, userId);
        return ResultResp.success();
    }

    @RequestMapping(value = "", method = RequestMethod.PUT)
    public ResultResp<Void> update(@Validated @RequestBody KpTopicUpdateReq req) {
        Long userId = SecurityUtil.getUser().getSysUserDTO().getId();
        kpTopicService.updateTopic(req, userId);
        return ResultResp.success();
    }

    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    public ResultResp<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUser().getSysUserDTO().getId();
        kpTopicService.deleteTopic(id, userId);
        return ResultResp.success();
    }

    @RequestMapping(value = "{id}/relation/{relatedId}", method = RequestMethod.POST)
    public ResultResp<Void> addRelation(@PathVariable("id") Long id,
                                        @PathVariable("relatedId") Long relatedId) {
        kpTopicService.addTopicRelation(id, relatedId);
        return ResultResp.success();
    }

    @RequestMapping(value = "{id}/relation/{relatedId}", method = RequestMethod.DELETE)
    public ResultResp<Void> removeRelation(@PathVariable("id") Long id,
                                           @PathVariable("relatedId") Long relatedId) {
        kpTopicService.removeTopicRelation(id, relatedId);
        return ResultResp.success();
    }

}
