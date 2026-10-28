-- 随堂测验的题量结构：3 单选 + 3 多选 + 1 概念 + 1 面试（2026-10-28 晚）
--
-- 背景：用户 2026-10-28 晚问「随堂测验的题目分布和条数配置是在配置中么 帮我改成 3+3+1+1 就行了
-- 3 单选 3 多选 1 概念 1 面试」。
--
-- 改的是**配置项**（`ai.prompt.exam.quiz.types`）⇒ **前端零改动**：
-- 题量结构本来就是数据（`exam.js` 的 `parseTypes` 解析「题型|道数|分值」这一行格式），
-- 解析器认的题型名是 `examParse.js` 的 `EXAM_TYPES` 那六个（单选 / 多选 / 判断 / 填空 / 概念 / 面试）。
--
-- 改前：单选|3|1 / 多选|3|2 / 判断|3|1 / 概念|3|5 / 面试|3|10
-- 改后：单选|3|1 / 多选|3|2 / 概念|1|5 / 面试|1|10
--   · **判断题整行去掉**（用户给的四类里没有它）⇒ 随堂测验不再出判断题。
--     ⚠️ **判断题这个题型本身还在**（`EXAM_TYPES.judge`、界面的两个按钮、目录级考试照用），
--     只是随堂测验不考它了 —— 别把这一行当成"删掉判断题这个能力"。
--   · 概念 / 面试 各 3 → 1；**分值不动**（每题的分值：单选 1 / 多选 2 / 概念 5 / 面试 10）
--     ⇒ 计划满分从 57 变成 24，共 8 道。
--
-- ⚠️ **题量是上限，不是必须出满** —— 这条写在**共用的**出题规范 `ai.prompt.exam.paper` 里：
--     「题量结构给的是上限，不是必须出满：材料撑不住就少出题……」
--     「宁缺毋滥：内容实在太少时只出一道题也是对的；一个考点都立不住的时候不要硬凑，
--       只写一行「出不了题」」
--   ⇒ **随堂测验也吃得到**（两种出题共用这一份规范）⇒ 这条知识内容少的时候出的题会少于 8 道，
--     **这是对的，不是出卷失败**。
--
-- 执行时机：随时（出题时读的就是这一项，不用发版、不用重启后端）。只动 corecraft 库。
-- ⚠️ 前端把这份配置**读一次就存着**（`AppTopicDetailView` 的 `quizSpec`）⇒ 改完**刷新页面**才生效。


-- ========== ① 跑前自检 ==========
-- 改前应该是这五行（判断 3、概念 3、面试 3）；对不上就先别往下跑
SELECT config_key, user_id, CHAR_LENGTH(config_value) AS 长度, config_value
  FROM corecraft.sys_config
 WHERE config_key = 'ai.prompt.exam.quiz.types';
-- 期望：1 行（user_id = 0），值是「单选|3|1 / 多选|3|2 / 判断|3|1 / 概念|3|5 / 面试|3|10」

-- 有没有**个人覆盖**（个人覆盖优先级高于系统默认）⇒ 有的话这条改不动，得先看覆盖的人是谁
SELECT id, user_id, config_value
  FROM corecraft.sys_config
 WHERE config_key = 'ai.prompt.exam.quiz.types'
   AND user_id <> 0;
-- 期望：0 行


-- ========== ② 改题量结构 ==========
-- 整段替换（这一项的值本身就是「几行题型|道数|分值」，不是长提示词 ⇒ 整段换是对的；
-- 长提示词才用 REPLACE 改那一句）。
UPDATE corecraft.sys_config
   SET config_value = '单选|3|1
多选|3|2
概念|1|5
面试|1|10',
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.exam.quiz.types';


-- ========== ③ 跑后校验 ==========
-- 3.1 值就是新那四行
SELECT config_value,
       (config_value LIKE '单选|3|1%') AS 第一行是单选3,
       (config_value LIKE '%概念|1|5%') AS 概念是1,
       (config_value LIKE '%面试|1|10%') AS 面试是1,
       (config_value LIKE '%判断%') AS 还有判断,
       (CHAR_LENGTH(config_value) - CHAR_LENGTH(REPLACE(config_value, CHAR(10), ''))) + 1 AS 行数
  FROM corecraft.sys_config
 WHERE user_id = 0 AND config_key = 'ai.prompt.exam.quiz.types';
-- 期望：第一行是单选3=1、概念是1=1、面试是1=1、还有判断=0、行数=4

-- 3.2 「宁缺毋滥 / 题量是上限」那两条**还在共用的出题规范里**（这一轮没动它）
SELECT (config_value LIKE '%题量结构给的是上限%') AS 有上限那条,
       (config_value LIKE '%宁缺毋滥%') AS 有宁缺毋滥,
       (config_value LIKE '%出不了题%') AS 有出不了题
  FROM corecraft.sys_config
 WHERE user_id = 0 AND config_key = 'ai.prompt.exam.paper';
-- 期望：三条都是 1（随堂测验共用这一份规范 ⇒ 一样吃得到）

-- 3.3 目录级考试那份**没被动**（这次只改随堂的）
SELECT config_value
  FROM corecraft.sys_config
 WHERE user_id = 0 AND config_key = 'ai.prompt.exam.types';
-- 期望：仍是「单选|3|1 / 多选|3|2 / 填空|3|2 / 概念|3|5 / 面试|3|10」


-- ========== 回滚 ==========
UPDATE corecraft.sys_config
   SET config_value = '单选|3|1
多选|3|2
判断|3|1
概念|3|5
面试|3|10',
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.exam.quiz.types';
