package com.mo.corecraft.controller.knowledge;

import com.mo.corecraft.model.req.KpTagRenameReq;
import com.mo.corecraft.model.resp.KpTagResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.KpTagService;
import com.mo.corecraft.utils.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kp/tag")
@RequiredArgsConstructor
public class KpTagController {

    private final KpTagService kpTagService;

    @GetMapping("/list")
    public ResultResp<List<KpTagResp>> list() {
        Long userId = SecurityUtil.getUser().getSysUserDTO().getId();
        return ResultResp.data(kpTagService.listTags(userId));
    }

    @PutMapping("/rename")
    public ResultResp<Void> rename(@Valid @RequestBody KpTagRenameReq req) {
        Long userId = SecurityUtil.getUser().getSysUserDTO().getId();
        kpTagService.renameTag(req, userId);
        return ResultResp.success();
    }

    @DeleteMapping("/{id}")
    public ResultResp<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUser().getSysUserDTO().getId();
        kpTagService.deleteTag(id, userId);
        return ResultResp.success();
    }
}
