package com.mo.corecraft.service;

import com.mo.corecraft.model.req.AiModelsRefreshReq;
import com.mo.corecraft.model.resp.AiVendorResp;

import java.util.List;

/**
 * AI 厂商（和它们下面的模型清单）。
 *
 * <p>**厂商和模型都在字典里**，这个服务只做两件事：读出来给人选、按厂商接口拉最新的回来。
 */
public interface AiVendorService {

    /**
     * 厂商清单（连各自的模型）。
     *
     * <p>只给启用着的：停用的厂商不出现在设置页里。
     */
    List<AiVendorResp> list();

    /**
     * 从厂商接口拉最新模型清单，**写回字典**（该厂商名下的字典项）。
     *
     * <p>拉不到就报错，**已有清单不清空** —— 有些厂商压根没有 /models 这个接口，
     * 清空了用户就只能去官网一个个抄。
     */
    AiVendorResp refresh(Long userId, AiModelsRefreshReq req);
}
