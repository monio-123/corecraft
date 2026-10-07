-- ============================================================
-- sys_dict_item.remark: varchar(255) → varchar(1000)（2026-10-07）
--
-- ## 为什么扩
--
-- 255 太紧，**逼着人开始删判据**。上一轮 organize 那条只剩 21 字符余量，
-- 「粒度宁细勿泛，说清是 SQL排序 还是对象排序 还是 list排序」这句只能砍掉——
-- 而它恰恰是用户明确要求的（「这个标签可以稍微详细一点 比如这个第二个中的排序标签
-- 最好是sql排序」）。
--
-- 三个数字放一起看就很清楚：
--   pick_category  160 / 255   只管一件事，剩 95
--   pick_tags      163 / 255   只管一件事，剩 92
--   organize       234 / 255   管三件事，只剩 21
--
-- 「一次问全」在长度约束下必然要把三个判据挤在一起互相挤掉。扩列是最简单的解法，
-- 不需要把判据拆到别的表、不需要后端加分支、不需要用户改操作习惯。
--
-- ## 安全性核对过两件事（不是"应该没事"，是查了）
--
-- 1. **DTO 上没有长度校验**。`SysDictItemCreateReq` / `SysDictItemUpdateReq` 的
--    `remark` 都是裸 `private String remark`，**没有任何 @Size / @Length**。
--    所以管理后台的「备注」textarea 存不进 1000 字符这件事不存在——
--    没有校验挡着，扩了列就真的能存。
-- 2. **行大小没超**。utf8mb4 varchar(1000) = 4000 字节，加上
--    label(400) + value(400) + css_class(400) ≈ 5.2 KB，
--    远低于 InnoDB 65535 字节的行上限。不会有 "Row size too large"。
--
-- ## 改完之后（判据终于写得开）
--
--   pick_category:categories   160 → 365
--   pick_tags:tags             163 → 270
--   organize:categories,tags   234 → 454
--
-- 三条都补回了被砍掉的东西：
--   - 「粒度宁细勿泛：「排序」太笼统，说清是 SQL排序/对象排序/list排序」
--   - 每一档的正反例（泛型·MySQL·序列化 / SQL排序·SQL分页·数据库连接 /
--     max_connections·SHOW STATUS）
--   - 「只有「数据库」这种宽泛领域名时就提一个更具体的出来」
--     —— 这条修的是 #87 那个真 bug：原来写的是「清单里没有就提议」，
--     而「数据库」一直在清单里，提议那一支根本走不到，所以 3 条里只有 1 条
--     提议了 MySQL。
-- ============================================================

ALTER TABLE `sys_dict_item`
  MODIFY COLUMN `remark` varchar(1000) DEFAULT NULL COMMENT '备注';


-- ---------- 改完后的三条判据（ALTER 之后才能存进去） ----------

UPDATE `sys_dict_item` SET `remark` = '这条知识该归到哪个目录。

判据是「这个名字本身有没有成套的知识体系」：
- 能围绕它列出一串关联知识点的，才是目录。例：泛型（协变/逆变/类型擦除/通配符）、MySQL（连接/索引/事务/复制）、序列化（Jackson/Gson/protobuf）
- 只是沾边、没有成套体系的，不该当目录。例：SQL排序、SQL分页、数据库连接
- 只是个参数或命令的，更不是。例：max_connections、SHOW STATUS

怎么挑：清单里选最贴近的那一层。有更具体的就别选大类——清单里同时有「MySQL」和「数据库」而这条讲 MySQL，就选 MySQL。
清单里只有「数据库」「持久化」这种宽泛领域名、而这条明显属于某个具体技术栈的，就把它当新目录名提出来。
提之前先看清单里有没有近义的，有就归过去，别造一个几乎一样的。'
WHERE `dict_type_id` = (
  SELECT id FROM `sys_dict_type` WHERE `code` = 'ai_topic_question' AND `is_delete` = 0
) AND `value` = 'pick_category:categories' AND `is_delete` = 0;

UPDATE `sys_dict_item` SET `remark` = '这条知识「涉及」哪些概念。

标签是「沾得上、但没有成套知识体系」的那类：
- 合适的例子：SQL排序、SQL分页、数据库连接
- 有成套体系的是目录的活（泛型、MySQL、序列化），别抢
- 只是个参数或命令的不算标签，那是正文：max_connections、SHOW STATUS

粒度宁细勿泛：「排序」太笼统，说清是「SQL排序」「对象排序」还是「list排序」。

数量 0 到 4 个，宁缺毋滥。凑出来的标签是垃圾，比没有还糟。
下面的清单只为统一叫法（同一条别一会儿 Java 一会儿 JAVA），符合就用，不符合不要硬套。'
WHERE `dict_type_id` = (
  SELECT id FROM `sys_dict_type` WHERE `code` = 'ai_topic_question' AND `is_delete` = 0
) AND `value` = 'pick_tags:tags' AND `is_delete` = 0;

UPDATE `sys_dict_item` SET `remark` = '这次一次给全，按下面三段输出，不要写别的解释：

名字：<12 字以内说清它讲什么，不要「详解」「介绍」「学习笔记」这类空词>

目录：<这条知识该归到哪个目录。判据是「这个名字本身有没有成套的知识体系」：
能围绕它列出一串关联知识点的才是目录（泛型、MySQL、序列化）；
沾边但没有成套体系的（SQL排序、SQL分页、数据库连接）不当目录；
单个参数或命令（max_connections、SHOW STATUS）更不是。
清单里选最贴近的那一层，有更具体的就别选大类；
只有「数据库」这种宽泛领域名时就提一个更具体的出来。提之前先看有没有近义的。>

标签：
- <这条知识「涉及」哪些概念。标签是沾得上、但没有成套知识体系的那类（SQL排序、SQL分页、数据库连接）；
有成套体系的是目录的活。单个参数或命令（max_connections、SHOW STATUS）不算标签，那是正文。
粒度宁细勿泛：「排序」太笼统，说清是「SQL排序」「对象排序」还是「list排序」。0 到 4 个，宁缺毋滥。>'
WHERE `dict_type_id` = (
  SELECT id FROM `sys_dict_type` WHERE `code` = 'ai_topic_question' AND `is_delete` = 0
) AND `value` = 'organize:categories,tags' AND `is_delete` = 0;
