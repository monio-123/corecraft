package com.mo.corecraft.controller.system;

import com.mo.corecraft.model.req.AiEndpointSaveReq;
import com.mo.corecraft.model.resp.AiEndpointResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.AiEndpointService;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 接口列表：每人多个，提问时选一个用。
 *
 * 和开关分成两个 controller：开关是"要不要用 AI"，接口是"用哪个"，
 * 路径上分开，前端也不用在一个请求里分清两种语义。
 *
 * **userId 一律从登录态取**：路径里的 id 是接口自己的 id，不是用户 id，
 * 真正的归属校验在 service 里按 userId 查，猜别人的 id 也取不到。
 */
@RestController
@RequestMapping("/ai/endpoint")
@RequiredArgsConstructor
public class AiEndpointController {

    private final AiEndpointService aiEndpointService;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResultResp<List<AiEndpointResp>> list() {
        return ResultResp.data(aiEndpointService.list(currentUserId()));
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    public ResultResp<AiEndpointResp> create(@Validated @RequestBody AiEndpointSaveReq req) {
        req.setId(null);
        return ResultResp.data(aiEndpointService.save(currentUserId(), req));
    }

    @RequestMapping(value = "", method = RequestMethod.PUT)
    public ResultResp<AiEndpointResp> update(@Validated @RequestBody AiEndpointSaveReq req) {
        if (req.getId() == null) {
            throw new com.mo.corecraft.exception.CoreCraftException("更新时必须带 API ID");
        }
        return ResultResp.data(aiEndpointService.save(currentUserId(), req));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public ResultResp<Void> delete(@PathVariable Long id) {
        aiEndpointService.delete(currentUserId(), id);
        return ResultResp.success();
    }

    private Long currentUserId() {
        return SecurityUtil.getUser().getSysUserDTO().getId();
    }
}