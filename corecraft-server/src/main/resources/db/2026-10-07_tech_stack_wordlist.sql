-- ============================================================
-- 技术栈词表 + 跨字段约束（2026-10-07，第四次调 organize 判据）
--
-- ## 第四轮实机的结果
--
--   MySQL连接数查看与修改   目录：建议新建「MySQL」 ✓   标签：数据库连接 ✓
--   多字段排序补 res_pool_id 目录：Mybatis ✓            标签：SQL排序、排序稳定性、多字段排序 ✓
--   MySQL 公钥检索报错     目录：数据库 ✗               标签：数据库连接、JDBC、MySQL ✗
--
-- 前两条对了：**「排序」变成了「SQL排序」**（#88 补回的「粒度宁细勿泛」那句起效），
-- **第 1 条提议了 MySQL 之后标签栏的 MySQL 自动消失了**（跨字段联动本来是有的）。
--
-- 第 3 条不对：JDBC 和 MySQL 落到了标签栏。
--
-- ## 根因不是"例子不够"，是两件事
--
-- 1. **技术栈词表太短**。原来只列了 泛型/MySQL/序列化 三个，模型不知道 JDBC 也算。
--    补成：MySQL、JDBC、Redis、Nginx、Kafka、Spring、MyBatis——**并且写明
--    "不管清单里现在有没有，都不该拿它们当标签"**。
--    这一点很关键：JDBC 和 MySQL **都不在用户的目录清单里**，不特意说，模型会以为
--    "清单里没有 = 那它只能当标签"。
--
-- 2. **提示词分段写，段与段之间没有约束**。模型的联动其实存在（第 1 条证明了：
--    目录栏一选定 MySQL，标签栏就不敢再出 MySQL），但那是**因为它自己在目录栏选了
--    MySQL**。第 3 条它没提议 MySQL，于是 MySQL 留在标签里——**从它自己那套逻辑看
--    是自洽的，只是起点判断错了**。所以光补词表不够，得把这条依赖写成明文：
--    「标签是**目录栏没选过的**那些」。
--
-- ## 顺带记一句给以后调的人
--
-- 标签清单里现在还留着 `MySQL`、`JDBC` 这两条（它们是 0 使用的孤儿概念标签）。
-- 提示词末尾那句「下面的清单只为统一叫法，符合就用」是**中性表述**，有可能反过来
-- 把模型推向照抄清单里的词。所以显式的「这些词是目录的活、别当标签」必须留着——
-- **显式规则压过中性清单**，别因为觉得"清单是数据不是规则"就把这句删了。
--
-- 长度：464 / 309 / 585，都 < varchar(1000)（#88 扩的列）。
-- ============================================================

UPDATE `sys_dict_item` SET `remark` = '这条知识该归到哪个目录。

判据是「这个名字本身有没有成套的知识体系」：
- 能围绕它列出一串关联知识点的，才是目录。例：泛型（协变/逆变/类型擦除/通配符）、MySQL（连接/索引/事务/复制）、序列化（Jackson/Gson/protobuf）、JDBC（驱动/连接池/事务/预编译）
- 只是沾边、没有成套体系的，不该当目录。例：SQL排序、SQL分页、数据库连接
- 只是个参数或命令的，更不是。例：max_connections、SHOW STATUS

MySQL、JDBC、Redis、Nginx、Kafka、Spring、MyBatis 这类技术栈名一律是目录级的——不管清单里现在有没有，都不该拿它们当标签。

怎么挑：清单里选最贴近的那一层，有更具体的就别选大类（清单里同时有「MySQL」和「数据库」而这条讲 MySQL，就选 MySQL）。
只有「数据库」「持久化」这种宽泛领域名、而这条明显属于某个具体技术栈的，就把它当新目录名提出来。
提之前先看清单里有没有近义的，有就归过去，别造一个几乎一样的。'
WHERE `dict_type_id` = (
  SELECT id FROM `sys_dict_type` WHERE `code` = 'ai_topic_question' AND `is_delete` = 0
) AND `value` = 'pick_category:categories' AND `is_delete` = 0;

UPDATE `sys_dict_item` SET `remark` = '这条知识「涉及」哪些概念——但不要重复目录已经表达了的那个概念。

标签是「沾得上、但没有成套知识体系」的那类：
- 合适的例子：SQL排序、SQL分页、数据库连接
- MySQL、JDBC、泛型、MyBatis、Redis 这类有成套体系的一律是目录的活，别当标签
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
能围绕它列出一串关联知识点的才是目录（泛型、MySQL、序列化、JDBC、Nginx、Kafka…）；
沾边但没有成套体系的（SQL排序、SQL分页、数据库连接）不当目录；
单个参数或命令（max_connections、SHOW STATUS）更不是。
MySQL、JDBC、Redis、Nginx、Kafka、Spring、MyBatis 这类技术栈名一律是目录级的，不管清单里现在有没有。
清单里选最贴近的那一层，有更具体的就别选大类；
只有「数据库」这种宽泛领域名、而这条明显属于某个具体技术栈的，就提那个技术栈名出来。提之前先看有没有近义的。>

标签：
- <这条知识「涉及」哪些概念，但上面目录栏没选过的。标签是沾得上、但没有成套知识体系的那类
（SQL排序、SQL分页、数据库连接）；MySQL、JDBC 这类技术栈名是目录的活，别放这里。
单个参数或命令（max_connections、SHOW STATUS）不算标签，是正文。
粒度宁细勿泛：「排序」太笼统，说清是「SQL排序」「对象排序」还是「list排序」。0 到 4 个，宁缺毋滥。>'
WHERE `dict_type_id` = (
  SELECT id FROM `sys_dict_type` WHERE `code` = 'ai_topic_question' AND `is_delete` = 0
) AND `value` = 'organize:categories,tags' AND `is_delete` = 0;
