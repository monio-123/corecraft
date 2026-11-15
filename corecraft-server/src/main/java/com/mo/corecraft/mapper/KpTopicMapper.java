package com.mo.corecraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.resp.KpTopicIndexResp;
import com.mo.corecraft.model.resp.KpTopicResp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface KpTopicMapper extends BaseMapper<KpTopic> {

    /**
     * 知识点的**结构索引**（轻量投影，**不含正文**）。
     *
     * <p>SQL 在 `mappers/KpTopicMapper.xml` 里 —— 为什么单独一条、为什么不查实体，
     * 那边的注释说全了。
     *
     * <p>⚠️ **关键词不传"哪一档"，传"搜哪几块"**（titleQ / contentQ）：
     * 档位到「搜哪几块」的推导**只在 {@code KpTopicRepository#scopeKeywords} 一处**
     * ⇒ 索引这条 SQL 和列表那条 wrapper 不可能漂。
     *
     * @param userId   归属（只查自己的）
     * @param titleQ   标题要 like 的词；null = 不搜标题
     * @param contentQ 正文要 like 的词；null = 不搜正文
     */
    List<KpTopicIndexResp> selectIndex(@Param("userId") Long userId,
                                        @Param("titleQ") String titleQ,
                                        @Param("contentQ") String contentQ);

    /**
     * 列表页的**一页**：轻量投影，**正文只取开头一段当摘要**。
     *
     * <p>为什么不用 {@code selectPage}（MP 实体查询）：那条会把整行取回来，
     * **包括 content 那一整列**（实测 20 条 = 29KB，其中正文占绝大多数）——
     * 而卡片上只显示**前 90 个字的摘要**，剩下的正文在这一屏里**一个像素都用不上**。
     * 点进详情页另有详情接口取全文 ⇒ 列表这次传输是**白传输**。
     *
     * <p>⚠️ **摘要长度取 200 字符（不是 90）**：前端的摘要还要做一遍清洗
     * （去 markdown 标记、压空白），90 字符截完清洗完常常不足 90 字
     * （实测：截 90 时 40 条里 39 条摘要不足；截 150 起全部够）⇒ 取 200 留余量。
     * 截断点落在哪不影响正确性：前端拿到的仍是正文**开头**的一段。
     */
    List<KpTopicResp> selectPageProjection(@Param("userId") Long userId,
                                           @Param("categoryIds") List<Long> categoryIds,
                                           @Param("uncategorized") Boolean uncategorized,
                                           @Param("typeId") Long typeId,
                                           @Param("tag") String tag,
                                           @Param("keyword") String keyword,
                                           @Param("titleQ") String titleQ,
                                           @Param("contentQ") String contentQ,
                                           @Param("createdFrom") LocalDateTime createdFrom,
                                           @Param("createdTo") LocalDateTime createdTo,
                                           @Param("offset") long offset,
                                           @Param("size") long size);

    /**
     * 列表页的**总条数**（与 {@link #selectPageProjection} 同一套过滤条件）。
     *
     * <p>⚠️ **必须与取数那条共用过滤条件** —— 两边的条数不一致时，
     * 分页器显示的页数和实际能翻到的条数对不上，而界面上什么异常都看不出来。
     */
    long countPageProjection(@Param("userId") Long userId,
                             @Param("categoryIds") List<Long> categoryIds,
                             @Param("uncategorized") Boolean uncategorized,
                             @Param("typeId") Long typeId,
                             @Param("tag") String tag,
                             @Param("keyword") String keyword,
                             @Param("titleQ") String titleQ,
                             @Param("contentQ") String contentQ,
                             @Param("createdFrom") LocalDateTime createdFrom,
                             @Param("createdTo") LocalDateTime createdTo);
}
