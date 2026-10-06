-- ============================================================
-- AI 引导问题字典（2026-10-07 新增）
--
-- ## 为什么放 sys_dict 而不是新建表
--
-- 这几个问题是**全局模板**，对所有人一样，谁也不需要"我自己的那套引导问题"。
-- 字典表没有 user_id，放进去正好就是全局一份；要按人隔离的（AI 接口配置）才拆了 ai_endpoint。
--
-- ## 三列怎么分工
--
-- sys_dict_item 的 `value` 只有 varchar(100)，装不下
-- `{"prompt":"...","context":["categories"]}` 这种 JSON（remark 也只有 255）。所以拆开：
--
--   label   (100) = 问题文本，显示在引导按钮上
--   value   (100) = **key:附料标记**，见下
--   remark  (255) = 给 AI 的描述（这条问题该怎么答的规则）
--
-- ## ⚠️ value 为什么是「key:contexts」这种复合值
--
-- **踩过的坑**：`sys_dict_item` 上有 `UNIQUE KEY uk_sys_dict_item_type_value (dict_type_id, value)`，
-- 也就是**同一个字典类型里 value 必须唯一**。最初想直接拿 value 当"附料标记"
-- （`none` / `categories` / `tags`），结果两条都不附料的问题撞成同一个 `none`，
-- 插入直接报 `Duplicate entry '2-none'`。
--
-- 所以 value 必须**同时**承担"项的机器标识"和"附什么料"两件事，写成 `key:contexts`：
--
--   rename:none               = 机器标识 rename，附料 none（只给正文）
--   pick_category:categories  = 机器标识 pick_category，附料 categories（附目录清单）
--   pick_tags:tags            = 机器标识 pick_tags，附料 tags（附标签清单）
--
-- 附料用**逗号分隔**而不是固定枚举：后端 split 之后逐个附，将来加「附关联知识点」
-- 就是 `pick_related:categories,tags,related`，**后端一行都不用改**。
--
-- 一共三种料（正文恒在），**这是一张三行的表，不是流程**，所以不需要工作流引擎。
-- ============================================================

INSERT INTO `sys_dict_type` (`code`, `name`, `remark`) VALUES
('ai_topic_question', 'AI-知识点默认问题',
 '知识点详情页「AI 问答」抽屉空态的引导问题。label=问题文本，value=key:附料标记(逗号分隔 none/categories/tags)，remark=给 AI 的描述');

-- type_id 用子查询取，不写死数字
INSERT INTO `sys_dict_item` (`dict_type_id`, `label`, `value`, `sort`, `remark`)
SELECT id, '给这个知识点命名', 'rename:none', 1,
 '这条知识还没起名字。读一遍正文，给它起一个：12 个字以内，直接说清它讲的是什么，不要用「详解」「介绍」「学习笔记」这类空词。只输出名字本身，不要任何解释、引号或标点。'
FROM `sys_dict_type` WHERE `code` = 'ai_topic_question' AND `is_delete` = 0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `label`, `value`, `sort`, `remark`)
SELECT id, '要关联哪个目录', 'pick_category:categories', 2,
 '这条知识该归到哪个目录？从下面这份目录清单里挑最贴切的一个，原样输出它的名字。如果都不合适，回答「都不合适，建议新建一个」。只能从清单里选，不要自己造名字。'
FROM `sys_dict_type` WHERE `code` = 'ai_topic_question' AND `is_delete` = 0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `label`, `value`, `sort`, `remark`)
SELECT id, '应该关联哪些标签', 'pick_tags:tags', 3,
 '给这条知识配 3 到 5 个标签，覆盖它涉及的核心概念（方法名、协议名、概念名），每个一行、前面加 - 。优先从下面已有的标签清单里挑，挑不到合适的可以提新的。'
FROM `sys_dict_type` WHERE `code` = 'ai_topic_question' AND `is_delete` = 0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `label`, `value`, `sort`, `remark`)
SELECT id, '生成几道测验题', 'make_quiz:none', 4,
 '基于这条知识出 3 道单选题考读者，每道题 4 个选项、其中只有 1 个正确答案。'
FROM `sys_dict_type` WHERE `code` = 'ai_topic_question' AND `is_delete` = 0;