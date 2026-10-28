-- 随堂测验（详情页 AI 问答抽屉里考当前这条知识）+ 判断题 + 出题范围拆分
-- 2026-10-27
--
-- 背景：用户 2026-10-27 提了四件事，这一轮一次做完：
--   ① 去掉「只能回答当前知识点内的内容」这条限制 —— 落在**全局人设** `ai.prompt.system`
--      （用户拍板：改在那里，聊天也一起放开）
--   ② 题型和分数进配置：最多 3 单选 / 3 多选 / 3 判断 / 3 概念 / 3 面试
--   ③ 结构可解析，跟之前的考试一样（复用同一套题型表与解析器）
--   ④ 目录级考试的提示词改一下：目录节点的考试要针对**内部所有内容的混合**，
--      好题目是**知识点相结合**的（但不要死板，按题型和题数分布看着来，不要求全结合）
--
-- 另外两条是配套的：
--   · **题量不是强制**：内容太少可以只出一道题，实在出不了就提示无法生成（宁缺毋滥）
--   · **判断题**是这一轮新增的题型：题干 +「答案：正确/错误」，界面两个按钮
--
-- 配套的代码改动（这一份脚本只动**配置**）：
--   · 前端 `examParse.js`：`EXAM_TYPES` 加 `judge`（全站唯一一份题型表）
--   · 前端 `exam.js`：配置多三个槽位（`scope` / `quiz.scope` / `quiz.types`）
--   · 前端 `ExamRunner.vue`（新）：答题 + 交卷 + 判分 + 讲解，考试页与随堂测验共用
--   · 前端 `AppTopicDetailView.vue`：抽屉加「随堂测验」入口与内嵌答题
--   · 后端：**零改动**（随堂测验传 `topicIds:[当前这条]`，走的是已有的 `digestOf`）
--
-- ## 提示词怎么分工（一条规则只存一份）
--
--   `ai.prompt.exam.paper`      共用：**怎么写题**（题型写法、考点清单流程、选项构造规则）
--   `ai.prompt.exam.grade`      共用：判分规范（加判断题那一行）
--   `ai.prompt.exam.scope`      目录级：**考什么**（全部内容 + 优先知识点相结合）  ← 新增
--   `ai.prompt.exam.quiz.scope` 随堂：**考什么**（只考当前这条）                ← 新增
--   `ai.prompt.exam.quiz.types` 随堂：题量结构（题型|道数|分值）                ← 新增
--
--   范围之所以要拆开：目录考试考的是「一堆内容的混合」，随堂考的是「一条」，
--   同一段话没法同时伺候两边 ⇒ 拆成两个配置项，各自可改。
--   拼 prompt 的顺序 = 题量结构 → 范围 → 写法规范 → 难度 → 考生要求（见 `exam.js`）
--
-- ⚠️ 一处耦合要知道：规范里写死了「【学习】」「【笔记】」这两个类型名，
--    而类型名来自字典 `kp_topic_type` 的 label。
--    **改了字典里的类型名，`ai.prompt.exam.paper` 要跟着改**（和题型名一个道理：
--    规范里出现的东西都是"模型要照着写"的契约名）。
--
-- ## 改法：全部用 REPLACE 改**要改的那几句**
--
-- 不是整段覆盖 —— 整段覆盖的话，规范里任何一句没抄到就悄悄丢了，
-- 而且丢的那句在库里看不出来（只有模型出的题变差）。
-- REPLACE 改完可以逐句核对：旧句一个都不剩、新句一个都不少。
--
-- 执行时机：随时（出题时读的就是这几条，不用发版）。只动 corecraft 库。


-- ========== ① 跑前自检 ==========
SELECT config_key, CHAR_LENGTH(config_value) AS 长度
  FROM corecraft.sys_config
 WHERE user_id = 0
   AND config_key IN ('ai.prompt.system', 'ai.prompt.exam.paper', 'ai.prompt.exam.grade');


-- ========== ② 全局人设：放开「只依据这次给你的内容」 ==========
-- 改的是**系统级**提示词（user_id = 0）⇒ 所有人生效。
-- 保留「不要拿你记忆里的版本替换它」：那是**版本准确性**，不是范围限制。
-- 删掉的是「你的回答只依据这次给你的内容」和「内容里没写到的地方明说没写、不要自己补」——
--   后者逼着模型对当前知识点的边界之外一律拒答（用户 2026-10-27 明确要去掉）。
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '你的回答只依据这次给你的内容，不要拿你记忆里的版本替换它；内容里没写到的地方，明说「这份内容里没写」，不要自己补。',
         '这份内容写明的版本、说法、结论就是当前这样，不要拿你记忆里的版本替换它。内容里没写到的地方，你可以按自己的理解补。'
       ),
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.system';


-- ========== ③ 出题规范：加判断题的写法 ==========
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '- 填空：题干一行，然后写「答案：」，后面只写那个词或短语，不要写整句话',
         '- 判断：题干一行，然后写「答案：」，后面只写「正确」或「错误」两个词之一，不要写选项行\n- 填空：题干一行，然后写「答案：」，后面只写那个词或短语，不要写整句话'
       ),
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.exam.paper';


-- ========== ④ 出题规范：宁缺毋滥写死（题量是上限，不是必须出满） ==========
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '- 清单里的考点个数最多和题量结构的总题数一样：材料撑不住就少出题，每种题型的道数也不要超过题量结构给它配的数量',
         '- 题量结构给的是上限，不是必须出满：材料撑不住就少出题，每种题型的道数也不要超过题量结构给它配的数量\n- 宁缺毋滥：内容实在太少时只出一道题也是对的；一个考点都立不住的时候不要硬凑，只写一行「出不了题」'
       ),
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.exam.paper';


-- ========== ⑤ 出题规范：判断题也要有材料依据 ==========
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '- 出单选、多选、填空时，正确答案必须能在材料里找到明确依据',
         '- 出单选、多选、填空、判断时，正确答案必须能在材料里找到明确依据'
       ),
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.exam.paper';


-- ========== ⑥ 出题规范：范围那句改成两种出题都成立的写法 ==========
-- 原来写的是「只考和上面这个主题直接相关的」—— "这个主题"对目录考试是整个目录、
-- 对随堂是那一条，同一句没法两边通用。改成「这次给的材料」：
-- 随堂的材料就是当前那一条（范围自然收窄到它），目录的材料就是那棵子树。
-- 「不要扯到相邻话题」保留，两边都适用。
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '- 只考和上面这个主题直接相关的，不要扯到相邻话题',
         '- 只考这次给的材料里的内容，不要扯到相邻话题'
       ),
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.exam.paper';


-- ========== ⑦ 判分规范：补判断题那一行 ==========
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '- 填空：意思对就是对，表达方式不同但意思一样也算对；意思不对就是错',
         '- 判断：与标准答案一致（正确 / 错误）就是判对，一致以外都是错\n- 填空：意思对就是对，表达方式不同但意思一样也算对；意思不对就是错'
       ),
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.exam.grade';


-- ========== ⑧ 新增三份配置值 ==========
-- 目录级考试的出题范围
INSERT INTO corecraft.sys_config (user_id, config_key, config_value, remark)
VALUES (
  0,
  'ai.prompt.exam.scope',
  '范围是这次给的全部内容：出题时在整份材料里取点，不要只盯着其中一两条。

优先出「知识点相结合」的题：
- 一道题同时用上两条以上相关知识（比如问「这两者怎么配合」「A 和 B 的区别和联系」「它们放在一起会怎样」）
- 这类题只占一部分，不是每一道都要结合：按题型和题数的分布看着来，别为了结合而硬凑
- 内容之间本来就没有关联、也没有可问的关系时，就老老实实单独考某一条',
  '目录级考试考什么：范围是这次给的全部内容，优先出知识点相结合的题。**与随堂测验的「出题范围」分开**'
);

-- 随堂测验的出题范围
INSERT INTO corecraft.sys_config (user_id, config_key, config_value, remark)
VALUES (
  0,
  'ai.prompt.exam.quiz.scope',
  '范围是当前这条知识。

考点从这条知识自己立得住的地方取：定义、原理、用法、边界、常见误解。',
  '随堂测验考什么：只考当前这条知识。**与目录级考试的「出题范围」分开**'
);

-- 随堂测验的题量结构
INSERT INTO corecraft.sys_config (user_id, config_key, config_value, remark)
VALUES (
  0,
  'ai.prompt.exam.quiz.types',
  '单选|3|1
多选|3|2
判断|3|1
概念|3|5
面试|3|10',
  '随堂测验的题量结构：每行「题型|道数|分值」，分值可省（省了按 1 分/题）。**题型名要用解析器认的那几个**（examParse.js 的 EXAM_TYPES），写别的会被忽略'
);


-- ========== ⑨ 配置树：把上面三项挂上去 ==========
-- 「出题范围」挨着「出题规范」，所以后面三项往后挪一位
UPDATE corecraft.sys_config_node
   SET sort = sort + 1
 WHERE config_key IN ('ai.prompt.exam.types', 'ai.prompt.exam.level', 'ai.prompt.exam.extra');

-- 目录级考试的「出题范围」
INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT id, '出题范围', 'scope', 'ai.prompt.exam.scope', 'text', 3,
       '目录级考试考什么。**与随堂测验的「出题范围」分开**：考一个目录时范围是内部全部内容，优先出知识点相结合的题'
  FROM corecraft.sys_config_node
 WHERE config_key = 'ai.prompt.exam'
 LIMIT 1;

-- 「随堂测验」这一组（容器）
INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT id, '随堂测验', 'quiz', 'ai.prompt.exam.quiz', NULL, 7,
       '详情页 AI 问答抽屉里的随堂测验：考当前这条知识。**写法规范 / 判分规范 / 难度档位沿用上面那几项**，只有范围和题量是它自己的'
  FROM corecraft.sys_config_node
 WHERE config_key = 'ai.prompt.exam'
 LIMIT 1;

INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT id, '出题范围', 'scope', 'ai.prompt.exam.quiz.scope', 'text', 1,
       '随堂测验考什么：只考当前这条知识'
  FROM corecraft.sys_config_node
 WHERE config_key = 'ai.prompt.exam.quiz'
 LIMIT 1;

INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT id, '题型与道数', 'types', 'ai.prompt.exam.quiz.types', 'text', 2,
       '随堂测验的题量结构：每行「题型|道数|分值」。**题型名要用解析器认的那几个**（examParse.js 的 EXAM_TYPES），写别的会被忽略'
  FROM corecraft.sys_config_node
 WHERE config_key = 'ai.prompt.exam.quiz'
 LIMIT 1;


-- ========== ⑩ 跑后校验 ==========
-- 10.1 三条新配置都在、值不是空
SELECT config_key, CHAR_LENGTH(config_value) AS 长度
  FROM corecraft.sys_config
 WHERE user_id = 0
   AND config_key IN ('ai.prompt.exam.scope', 'ai.prompt.exam.quiz.scope', 'ai.prompt.exam.quiz.types');
-- 期望：3 行，长度都 > 0

-- 10.2 人设里已经没有「只依据这次给你的内容」，新句子在
SELECT CHAR_LENGTH(config_value) AS 长度,
       (config_value LIKE '%只依据这次给你的内容%') AS 还有旧限制,
       (config_value LIKE '%你可以按自己的理解补%') AS 新句子在
  FROM corecraft.sys_config
 WHERE user_id = 0 AND config_key = 'ai.prompt.system';
-- 期望：还有旧限制 = 0，新句子在 = 1

-- 10.3 出题规范：旧句一个不剩、新句一个不少
SELECT CHAR_LENGTH(config_value) AS 长度,
       (config_value LIKE '%- 判断：题干一行%') AS 有判断写法,
       (config_value LIKE '%出不了题%') AS 有出不了题,
       (config_value LIKE '%题量结构给的是上限%') AS 有上限那条,
       (config_value LIKE '%填空、判断时，正确答案%') AS 判断也有依据,
       (config_value LIKE '%只考这次给的材料里的内容%') AS 范围句已改,
       (config_value LIKE '%只考和上面这个主题直接相关的%') AS 旧范围句还在,
       (config_value LIKE '%清单里的考点个数最多%') AS 旧考点句还在
  FROM corecraft.sys_config
 WHERE user_id = 0 AND config_key = 'ai.prompt.exam.paper';
-- 期望：有判断写法=1、有出不了题=1、有上限那条=1、判断也有依据=1、范围句已改=1
--       旧范围句还在=0、旧考点句还在=0

-- 10.4 判分规范里有判断题那一行
SELECT (config_value LIKE '%- 判断：与标准答案一致%') AS 有判断判定
  FROM corecraft.sys_config
 WHERE user_id = 0 AND config_key = 'ai.prompt.exam.grade';
-- 期望：1

-- 10.5 配置树：四个新节点都在，父链对
SELECT id, parent_id, name, code, config_key, value_type, sort
  FROM corecraft.sys_config_node
 WHERE config_key LIKE 'ai.prompt.exam%'
 ORDER BY config_key;
-- 期望：多出 ai.prompt.exam.scope / ai.prompt.exam.quiz / ai.prompt.exam.quiz.scope / ai.prompt.exam.quiz.types


-- ========== 回滚 ==========
-- 前提：先删掉新增的四个配置树节点与三条配置值，再把下面几段的值改回去。
--
-- DELETE FROM corecraft.sys_config_node WHERE config_key IN
--   ('ai.prompt.exam.scope', 'ai.prompt.exam.quiz', 'ai.prompt.exam.quiz.scope', 'ai.prompt.exam.quiz.types');
-- DELETE FROM corecraft.sys_config WHERE user_id = 0 AND config_key IN
--   ('ai.prompt.exam.scope', 'ai.prompt.exam.quiz.scope', 'ai.prompt.exam.quiz.types');
--
-- UPDATE corecraft.sys_config_node SET sort = sort - 1
--  WHERE config_key IN ('ai.prompt.exam.types', 'ai.prompt.exam.level', 'ai.prompt.exam.extra');
--
-- UPDATE corecraft.sys_config
--    SET config_value = REPLACE(
--          config_value,
--          '这份内容写明的版本、说法、结论就是当前这样，不要拿你记忆里的版本替换它。内容里没写到的地方，你可以按自己的理解补。',
--          '你的回答只依据这次给你的内容，不要拿你记忆里的版本替换它；内容里没写到的地方，明说「这份内容里没写」，不要自己补。'
--        ),
--        update_time = NOW()
--  WHERE user_id = 0 AND config_key = 'ai.prompt.system';
--
-- UPDATE corecraft.sys_config
--    SET config_value = REPLACE(
--          REPLACE(
--            REPLACE(
--              REPLACE(
--                config_value,
--                '- 判断：题干一行，然后写「答案：」，后面只写「正确」或「错误」两个词之一，不要写选项行\n',
--                ''
--              ),
--              '- 题量结构给的是上限，不是必须出满：材料撑不住就少出题，每种题型的道数也不要超过题量结构给它配的数量\n- 宁缺毋滥：内容实在太少时只出一道题也是对的；一个考点都立不住的时候不要硬凑，只写一行「出不了题」\n',
--              '- 清单里的考点个数最多和题量结构的总题数一样：材料撑不住就少出题，每种题型的道数也不要超过题量结构给它配的数量\n'
--            ),
--            '- 出单选、多选、填空、判断时，正确答案必须能在材料里找到明确依据',
--            '- 出单选、多选、填空时，正确答案必须能在材料里找到明确依据'
--          ),
--          '- 只考这次给的材料里的内容，不要扯到相邻话题',
--          '- 只考和上面这个主题直接相关的，不要扯到相邻话题'
--        ),
--        update_time = NOW()
--  WHERE user_id = 0 AND config_key = 'ai.prompt.exam.paper';
--
-- UPDATE corecraft.sys_config
--    SET config_value = REPLACE(
--          config_value,
--          '- 判断：与标准答案一致（正确 / 错误）就是判对，一致以外都是错\n',
--          ''
--        ),
--        update_time = NOW()
--  WHERE user_id = 0 AND config_key = 'ai.prompt.exam.grade';
