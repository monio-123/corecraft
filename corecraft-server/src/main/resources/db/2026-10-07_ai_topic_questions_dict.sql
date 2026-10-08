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


-- ============================================================
-- 追加：organize —— 一次把标题/目录/标签问全（2026-10-07）
--
-- ## 为什么要单独一条，而不是复用 rename + pick_category + pick_tags 三条
--
-- 列表页「AI 归类」要对待归类的知识**逐条**跑一遍。三条分开问 = 每条 3 次调用，
-- 18 条就是 54 次；而且三个结果互相不知道对方，名字和标签可能各说各话。
-- 一次问全 = 每条 1 次，而且模型能同时权衡「叫什么 / 放哪 / 打什么标」。
--
-- 代价是多一个解析器（前端 aiAdopt.js 的 parseOrganize）——这是**代码**，
-- 字典免不掉；字典免掉的是"改措辞"和"加一种附料"（`contexts` 逗号分隔那套照旧能用）。
--
-- ## 输出格式为什么用「名字：/目录：/标签：」三段，而不是靠位置猜
--
-- 三样东西一次产出，模型很容易先写一段解释。**带前缀的键值行可以各取各的**：
-- 名字那行没写就只补目录、目录没匹配上就只补名字和标签，各字段独立成败。
-- 靠"第一行是什么、第二行是什么"去猜的话，少一行后面全错位。
--
-- 长度：189 字符 / varchar(255)。写得下，但**再加内容要重新量**。
-- ============================================================

INSERT INTO `sys_dict_item` (`dict_type_id`, `label`, `value`, `sort`, `remark`)
SELECT id, '补全这条知识（标题 / 目录 / 标签）', 'organize:categories,tags', 6,
 '这条知识还没起名字、没归类、没打标签。一次给全，严格按下面三段输出，除此之外不要写任何解释：
名字：<12 个字以内，说清它讲的是什么，不要用「详解」「介绍」这类空词>
目录：<从下面这份目录清单里挑最贴切的一个，原样输出清单里的名字；都不合适就写「都不合适，建议新建一个」>
标签：
- <3 到 5 个，覆盖核心概念（方法名、协议名、概念名），优先从下面已有的标签清单里挑>'
FROM `sys_dict_type` WHERE `code` = 'ai_topic_question' AND `is_delete` = 0;


-- ============================================================
-- ⚠️ 上面这几条 remark 已经被后续迁移改掉了，**这里保留的是历史原样**
--
-- 判据反复调了好几轮（目录/标签/正文三分、「粒度宁细勿泛」、提议新目录的条件…），
-- 每次都只 UPDATE 同一批行，从没回头改这一份：
--   2026-10-07_fix_tag_granularity_prompt.sql
--   2026-10-07_allow_category_proposal.sql
--   2026-10-07_category_depth_and_tag_finer.sql
--   2026-10-07_three_way_boundary.sql
--   2026-10-07_widen_dict_item_remark.sql  ← 最后一条，同时把 remark 列扩到 varchar(1000)
--
-- **想知道"判据现在到底怎么写的"，看 widen_dict_item_remark.sql 里那三段。**
--
-- 为什么不直接改这一份（改了就变成迁移脚本在说谎）：
-- 这个文件是**首次初始化**用的，回填一份就等于把 2026-10-07 那天的状态盖回去。
-- 迁移历史要保真，变化记在后面的文件里。
-- ============================================================