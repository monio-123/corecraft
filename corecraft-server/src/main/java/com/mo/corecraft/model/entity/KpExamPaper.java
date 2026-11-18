package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 一份试卷：**随堂测验 / 目录考试 / 复习卷共用一张表**（{@link #kind} 区分）。
 *
 * <p>标准答案属于用户私有数据，不提供含答案的公开分享（设计 §10.2）。
 * 试卷生成后先持久化，再进入答题（设计 §11.2）。
 */
@Getter
@Setter
@TableName("kp_exam_paper")
public class KpExamPaper extends BaseEntity {

    private Long id;

    /** 试卷是用户私有数据，查询一律带 userId 校验 */
    private Long userId;

    /** {@code quiz} 随堂测验 / {@code exam} 目录考试 / {@code review} 复习卷 */
    private String kind;

    /** 目录考试有值；复习卷跨目录允许 NULL，范围以 scopeSnapshot 为准 */
    private Long categoryId;

    /** 卷名（人看得懂） */
    private String title;

    /** {@code generating} 出卷中 / {@code ready} 可作答 / {@code failed} 出卷失败（生成完才进答题） */
    private String status;

    /** JSON：当时范围——知识点 id、标题、内容 hash/epoch、目录路径（快照，不回查现状） */
    private String scopeSnapshot;

    /** JSON：题型、道数、分值、难度、附加要求、模型等出卷参数 */
    private String settingsSnapshot;
}
