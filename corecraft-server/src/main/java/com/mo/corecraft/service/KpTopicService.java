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
}
