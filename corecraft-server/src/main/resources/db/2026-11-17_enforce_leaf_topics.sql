-- ============================================================
-- 2026-11-17 彻底执行「知识点只挂叶子目录」：
-- 1. 备份并将全库挂在非叶子目录下的存量知识点（59 条）统一退回待归类（category_id = NULL）
-- 2. 移除 ai.prompt.knowledge.plan / expand / point 中「在非叶子层生成总览/导读知识点」的规则
-- 3. 更新 ai.prompt.question.pick_category:categories 提示词：明确只选最底层叶子目录
-- （说明：原版第 ⑥ 段曾删除 ai.prompt.knowledge.tags:tags，已按 skill §2.2 保留标签提示词的约定移除，避免重放误删）
--
-- 执行时机：本次迁移在 dev/线上库执行一次；幂等守卫支持安全重跑。
-- ============================================================

-- ---------- 1. 跑前自检 ----------
SELECT COUNT(*) AS non_leaf_topic_cnt
  FROM corecraft.kp_topic t
 WHERE t.is_delete = 0
   AND t.category_id IS NOT NULL
   AND EXISTS (SELECT 1 FROM corecraft.kp_category ch WHERE ch.parent_id = t.category_id);

-- ---------- 2. 幂等变更 ----------

-- ① 备份非叶子直属知识点的原目录映射（幂等建表）
CREATE TABLE IF NOT EXISTS corecraft._bak_20261117_nonleaf_topics (
  topic_id BIGINT PRIMARY KEY,
  old_category_id BIGINT NOT NULL,
  title VARCHAR(255),
  backed_up_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO corecraft._bak_20261117_nonleaf_topics (topic_id, old_category_id, title)
SELECT t.id, t.category_id, t.title
FROM corecraft.kp_topic t
WHERE t.is_delete = 0
  AND t.category_id IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM corecraft.kp_category ch
    WHERE ch.parent_id = t.category_id
  );

-- 将所有挂在非叶子目录上的知识点移入「待归类」（category_id = NULL）
UPDATE corecraft.kp_topic t
JOIN (
  SELECT DISTINCT parent_id AS non_leaf_id
  FROM corecraft.kp_category
  WHERE parent_id IS NOT NULL AND parent_id > 0
) nl ON t.category_id = nl.non_leaf_id
SET t.category_id = NULL
WHERE t.is_delete = 0;

-- ② 更新 ai.prompt.knowledge.plan：去掉非叶子层的「总览知识点」，严格「目录=有下级、知识点=最深叶子」
UPDATE corecraft.sys_config
SET config_value = '把一个主题拆成一套能照着学的目录树，每个最底层章节下列上该掌握的知识点。**只输出目录树，不要任何解释、不要开场白。**\n\n**每个知识点（叶子）的行尾，用「 ｜ 」接一句定位**：不超过 25 字，说清「这条讲什么、讲到什么程度」；不写「很重要」「必须掌握」这类空话。\n\n材料里给了**主题**，可能还有**补充说明**和**已有的一级目录名单**。\n- **补充说明**（用户自己写的一句话，如「面向开发」「面向考试」）是这一批的额外要求：按它取舍内容重点——面向开发就偏工程实现与排障，面向考试就偏考点与易错点。\n- 名单**只用来避开重复建同义的书**（名单里已有「计算机网络」，就别再建一本「计算机网络」）\n- 输出的树**自成一体**，全部是新的，**不要挂到名单里任何一本书下面**\n\n**按这个主题公认的经典教材 / 专著来排布**：章节顺序、每个章节覆盖哪些问题，照通行教材的编排来；不同流派说法有分歧时，取更通用、更接近多数教材的那一种。**只借鉴结构，不要写书名、版本、页码、原文引文**。\n\n层级（**照经典教材的样子：篇 → 章 → 节，不许跳；严禁在同一目录下混排子目录与知识点**）：\n- 第 1 层：**主题目录**（书名，如「操作系统」）\n- 第 2 层：**模块目录**（书里的"篇"，如「2. 进程管理」）\n- 第 3 层：**章节目录**（书里的"章"，如「2.1 进程基础」）\n- 第 4 层及以下：**子章节**（书里"章"下面再分的问题域，如「2.1.1 同步原语」）\n- **最底层叶子**：**知识点标题**（挂在最深的目录下面；**凡是有下级子目录的节点，下面绝对不要直接挂知识点或总览**）\n- **几层**：**至少 3 层**（主题 → 模块 → 章节），再往下**按内容自然展开** —— 一节内容多就分小节，一条撑不起一段就停。\n\n每一层放什么：\n- 模块 = 一个**大领域**（进程管理 / 内存管理 / 文件系统 / I/O 与设备 / 安全与虚拟化）\n- 章节 = 这个领域里的**一个问题域**（进程基础 / 线程 / 调度 / 同步与互斥 / 死锁与通信）\n- 子章节 = 章节里**更细的一个问题**（线程 → 线程池 / 线程优先级）\n- 知识点 = 该问题域里的**一个具体概念或机制**（PCB / 进程上下文切换 / 时间片轮转），**只出现在最底层的叶子目录下面**\n- **知识点总数不超过 200 条**\n\n**数量按内容重要性分配，不要平均分配**：重点问题域多给几个概念，次要的地方少给甚至一个就够；宁可一层只有一个，也不要为凑数硬凑。\n\n命名（**都要是名词短语：不要句子、不要问题、不要口水话**）：\n- **模块带序号「2.」、章节带序号「2.1」、子章节带「2.1.1」**；主题目录不带序号（它是书名）\n- **章节名是领域名**：「进程基础」「调度算法」「死锁的四个必要条件」；**不要**「进程是什么」「进程的定义」「进程概述」「进程简介」\n- **知识点名是标准术语或名词短语**：「进程上下文切换」「PCB（进程控制块）」「进程终止」「内核态与用户态」「银行家算法」；**不要**陈述句\n- **不要「XX的定义」「XX的含义」「XX是什么」「XX概述」「XX总览」**：直接写术语\n- 名字要能当**检索词**、能当**卡片标题**；结论、原理、对比写进正文\n- 目录名不超过 50 个字\n\n结构例子（**照这个层次来，只有最底层目录下面挂知识点**）：\n计算机网络\n├── 1. 物理层\n│   ├── 1.1 传输介质\n│   │   ├── 双绞线 ｜ 结构、分类与抗干扰特性\n│   │   ├── 光纤 ｜ 多模与单模的差别及适用距离\n│   │   ├── 同轴电缆 ｜ 基带与宽带传输特性\n│   │   └── 无线信道 ｜ 频段划分与主要干扰来源\n│   └── 1.2 信道容量\n│       ├── 奈奎斯特准则 ｜ 无噪声理想低通信道极限速率\n│       └── 香农定理 ｜ 信噪比与高斯白噪声信道极限容量'
WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.plan';

-- ③ 更新 ai.prompt.knowledge.plan.expand：去掉「模块导读」与「XX总览」，严禁同层混排子目录与知识点
UPDATE corecraft.sys_config
SET config_value = '你在为材料里的主题补全目录。**全书模块已经定稿**（见材料），这次只展开指定的那一个模块。\n\n输出层级（从这个模块开始往下 2~3 层，用树形符号 ├── └──）：\n- 第 1 行：这个模块（照材料原样抄，含序号与定位）\n- 第 2 层：章 —— **编号跟着模块序号走**：模块 3 的章就是 3.1 / 3.2，不要固定写 2.x\n- 第 3 层：知识点（叶子），行尾用“ ｜ ”接不超过 25 字的定位\n- 章里内容多可以再分一层小节（模块 3 的第 1 节就是 3.1.1），再把知识点挂在小节下面\n\n规则：\n- **只输出这一个模块**，不要写材料里列出的其它模块\n- **严禁在同一层级混排子目录与知识点**：如果某章（如 3.2）下面拆了小节（3.2.1、3.2.2），那 3.2 下面只能有小节目录，所有知识点都必须挂进对应小节里；不要在有子目录的节点下面直接挂「XX总览」或散装知识点\n- 每个叶子（知识点）行尾都要有定位\n- 名字都是名词短语：不要“XX的定义”“XX概述”“XX总览”“XX是什么”，不要陈述句\n- 知识点 10~40 条，按内容重要性分配，不要平均分\n- 只输出目录树，不要解释'
WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.plan.expand';

-- ④ 更新 ai.prompt.knowledge.point：去掉「总览知识点」段落
UPDATE corecraft.sys_config
SET config_value = REPLACE(
  REPLACE(
    REPLACE(
      config_value,
      '，以及**这个目录下面的章节和它们各自的知识点**（总览知识点要用；章节后面带地址的，把章节名写成链接）',
      ''
    ),
    '- **总览知识点**（材料里给了下级章节和它们的知识点时）：正文写成这个主题/模块的**索引** —— 它讲什么、分成哪几个章节、每个章节讲什么、重点和难点在哪，并把下级章节和知识点的名字列出来\n',
    ''
  ),
  '- **总览知识点里提到下级章节时，章节名写成 markdown 链接**：材料里给了地址就照抄（`[章节名](地址)`）；没给地址就写普通文字，**不要自己编地址**\n',
  ''
)
WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.point';

-- ⑤ 更新 ai.prompt.question.pick_category:categories：去掉已下线的标签描述，明确「只选叶子目录」
UPDATE corecraft.sys_config
SET config_value = '【判据】这条知识该归到哪个叶子目录。\n判断标准：这条知识主要在讲什么，就归到最贴近的那个最底层目录（叶子目录）。\n- **只能选下面没有子目录的叶子目录**，不要选下面还有子目录的书名或章节目录\n- 清单里有合适的叶子目录就选最贴近的那一个\n- 要新建子目录就用斜杠写出完整路径（如「数据库/MySQL」），但不要在已有知识点的叶子目录下面再建子目录\n【格式】只输出目录完整路径本身。不要写理由、不要加括号或引号。'
WHERE user_id = 0 AND config_key = 'ai.prompt.question.pick_category:categories';

-- ---------- 3. 跑后校验 ----------
SELECT COUNT(*) AS non_leaf_remaining
  FROM corecraft.kp_topic t
  JOIN corecraft.kp_category ch ON ch.parent_id = t.category_id
 WHERE t.is_delete = 0;

SELECT COUNT(*) AS backup_cnt FROM corecraft._bak_20261117_nonleaf_topics;

SELECT config_key
  FROM corecraft.sys_config
 WHERE config_key LIKE 'ai.prompt.knowledge.%'
   AND config_value LIKE '%总览知识点%';

-- ---------- 4. 回滚段（按需手动执行） ----------
-- UPDATE corecraft.kp_topic t
-- JOIN corecraft._bak_20261117_nonleaf_topics b ON b.topic_id = t.id
-- SET t.category_id = b.old_category_id
-- WHERE t.is_delete = 0 AND t.category_id IS NULL;
