package com.mo.corecraft.service;

import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.req.KpTopicCreateReq;
import com.mo.corecraft.model.req.KpTopicUpdateReq;
import com.mo.corecraft.model.resp.KpTopicResp;

import java.util.List;

public interface KpTopicService {

    List<KpTopicResp> selectTopicList(KpTopicQuery query);

    KpTopicResp selectTopicById(Long id);

    void createTopic(KpTopicCreateReq req, Long userId);

    void updateTopic(KpTopicUpdateReq req, Long userId);

    void deleteTopic(Long id, Long userId);

    void addTopicRelation(Long topicId, Long relatedTopicId);

    void removeTopicRelation(Long topicId, Long relatedTopicId);

    /**
     * 按 tag 重新给该 user 的所有 topic 回填 treeId（一个 tag 集合 = 一棵树）。
     * tag 改名 / 删除后调用，保证 topic 树归属不漂。
     */
    void rebuildAutoTrees(Long userId);
}
