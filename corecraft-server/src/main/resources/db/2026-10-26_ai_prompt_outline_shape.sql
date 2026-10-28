-- 提示词去下限：骨架的每层数量区间 → 只留总量上限 + 一条分配原则
-- 2026-10-26
--
-- 背景：outline 提示词写着「模块 5 到 8 个、章节 3 到 5 个、知识点 3 到 5 个、总数 ≤120」。
--   那段话的本意是「**按经典教材的样子排布**」，但落成数字区间之后，
--   模型把它读成「每层铺匀」⇒ 实测（库里 5 棵 MySQL 树）**28 章有 25 章恰好 4 条**
--   （4 正是 3-5 的中点）。真实教材不均摊：概述章 2 条、死锁章 6 条才是常态。
--
-- 为什么下界有害（两条，都实测过）：
--   ① **下界本来就没约束住模型**：flash 那次实测出的骨架里，某模块只有 2 章（低于下界 3）；
--      库里还有一整棵 8×3×3 的树 ⇒ 花 40 个字表达的规则，模型随时可以不听。
--   ② **总量预算会把模型逼去「砍层」**：8×5×5=200 装不进 120 ⇒ 模型自己去把某一层的
--      章数砍掉，**丢内容的位置不受控**；而只有总量上限时，模型削薄的地方、不会砍一整层。
--      （数量是硬的，内容重要性是软的 ⇒ 冲突时模型保数量。）
--
-- 改法：**上限留、下界去**。
--   - 每层的数量区间**整条删掉**（层与层之间怎么分，完全由内容决定）；
--   - 只留「知识点总数不超过 200 条」当**成本闸**（120 条 × 每条 2 次调用 ≈ 40 分钟；
--     200 是"能一次列完"的上限，再多会被上游输出上限截断 ⇒ 表现为半截树、看不出是截断）；
--   - 加一句「**按内容重要性分配，不要平均分配**」；
--   - **结构例子改成不均的**（1.1 四条 / 1.2 两条）—— 例子是模型模仿的对象，
--     原来 2/2 的例子本身也在暗示「每章两三条」。
--
-- 正文提示词同一类锚点一起改：points 模式（没给标题清单）原来要求
--   「自己定 3 到 5 个知识点标题」⇒ 同样去掉下限。
--   （正文里的「至少 200 字」**保留** —— 那是内容质量下限、也是落库门槛，与数量区间不是一回事。）
--
-- **这是配置不是表结构**：`sys_config.config_value` 一改即时生效，不用发版。
-- 改的是**系统级**提示词（user_id = 0）⇒ 所有人生效。
--
-- **天然幂等**：全部用 REPLACE 定点替换，old 串不存在时 0 行影响 ⇒ 重复跑安全。
--
-- 跑前建议（内容级还原）：
--   mysqldump -uroot -p<pwd> corecraft sys_config \
--     --where="user_id=0 AND config_key IN ('ai.prompt.knowledge.outline','ai.prompt.knowledge.point')" \
--     > <工作区外>/corecraft_pre_shape_20261026.sql
--
-- 执行时机：随时（只动 corecraft 库）。


-- ========== ① 跑前自检（三条都要命中 1）============
SELECT config_key, CHAR_LENGTH(config_value) AS 长度,
       (config_value LIKE '%**5 到 8 个**%')          AS outline_待改处1,
       (config_value LIKE '%**知识点总数不超过 120 条**%') AS outline_待改处2,
       (config_value LIKE '%3 到 5 个知识点标题%')     AS point_待改处
  FROM corecraft.sys_config
 WHERE user_id = 0
   AND config_key IN ('ai.prompt.knowledge.outline', 'ai.prompt.knowledge.point');


-- ========== ② 改 ==========

-- ②.1 骨架：每层数量区间整条去掉 + 总量上限 120→200 + 加分配原则
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '每一层放什么（**按书的惯例**）：\n- 模块 = 一个**大领域**（进程管理 / 内存管理 / 文件系统 / I/O 与设备 / 安全与虚拟化），**5 到 8 个**\n- 章节 = 这个领域里的**一个问题域**（进程基础 / 线程 / 调度 / 同步与互斥 / 死锁与通信），**3 到 5 个**\n- 知识点 = 该问题域里的**一个具体概念或机制**（PCB / 进程上下文切换 / 时间片轮转），**3 到 5 个**\n- **知识点总数不超过 120 条**',
         '每一层放什么（**按书的惯例**）：\n- 模块 = 一个**大领域**（进程管理 / 内存管理 / 文件系统 / I/O 与设备 / 安全与虚拟化）\n- 章节 = 这个领域里的**一个问题域**（进程基础 / 线程 / 调度 / 同步与互斥 / 死锁与通信）\n- 知识点 = 该问题域里的**一个具体概念或机制**（PCB / 进程上下文切换 / 时间片轮转）\n- **知识点总数不超过 200 条**\n\n**数量按内容重要性分配，不要平均分配**：重点问题域多给几个概念，次要的地方少给甚至一个就够；宁可一层只有一个，也不要为凑数硬凑。'
       ),
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.knowledge.outline';

-- ②.2 骨架：结构例子改成**不均**的（1.1 四条 / 1.2 两条）⇒ 破掉「每章两三条」的暗示
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '│   ├── 1.1 传输介质\n│   │   ├── 双绞线\n│   │   └── 光纤',
         '│   ├── 1.1 传输介质\n│   │   ├── 双绞线\n│   │   ├── 光纤\n│   │   ├── 同轴电缆\n│   │   └── 无线信道'
       ),
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.knowledge.outline';

-- ②.3 正文：points 模式（没给标题清单）自己定标题时，也不设下限
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '先自己定这个目录该有的 3 到 5 个知识点标题，再按同样的格式写正文。',
         '先自己定这个目录该有的知识点标题（几个由内容决定，宁少勿凑），再按同样的格式写正文。'
       ),
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.knowledge.point';


-- ========== ③ 跑后校验 ==========
--   outline：长度应 +30 字左右；三条旧锚点全为 0；三条新锚点全为 1
--   point  ：长度应 +8 字左右；旧锚点 0；新锚点 1
SELECT config_key, CHAR_LENGTH(config_value) AS 长度,
       (config_value LIKE '%**5 到 8 个**%')              AS 旧_模块区间,
       (config_value LIKE '%**知识点总数不超过 120 条**%') AS 旧_总数120,
       (config_value LIKE '%3 到 5 个知识点标题%')         AS 旧_正文条数,
       (config_value LIKE '%**知识点总数不超过 200 条**%') AS 新_总数200,
       (config_value LIKE '%不要平均分配%')                AS 新_分配原则,
       (config_value LIKE '%同轴电缆%')                    AS 新_不均例子,
       (config_value LIKE '%几个由内容决定%')              AS 新_正文条数
  FROM corecraft.sys_config
 WHERE user_id = 0
   AND config_key IN ('ai.prompt.knowledge.outline', 'ai.prompt.knowledge.point');


-- ========== 回滚 ==========
-- UPDATE corecraft.sys_config
--    SET config_value = REPLACE(
--          REPLACE(
--            REPLACE(
--              config_value,
--              '每一层放什么（**按书的惯例**）：\n- 模块 = 一个**大领域**（进程管理 / 内存管理 / 文件系统 / I/O 与设备 / 安全与虚拟化）\n- 章节 = 这个领域里的**一个问题域**（进程基础 / 线程 / 调度 / 同步与互斥 / 死锁与通信）\n- 知识点 = 该问题域里的**一个具体概念或机制**（PCB / 进程上下文切换 / 时间片轮转）\n- **知识点总数不超过 200 条**\n\n**数量按内容重要性分配，不要平均分配**：重点问题域多给几个概念，次要的地方少给甚至一个就够；宁可一层只有一个，也不要为凑数硬凑。',
--              '每一层放什么（**按书的惯例**）：\n- 模块 = 一个**大领域**（进程管理 / 内存管理 / 文件系统 / I/O 与设备 / 安全与虚拟化），**5 到 8 个**\n- 章节 = 这个领域里的**一个问题域**（进程基础 / 线程 / 调度 / 同步与互斥 / 死锁与通信），**3 到 5 个**\n- 知识点 = 该问题域里的**一个具体概念或机制**（PCB / 进程上下文切换 / 时间片轮转），**3 到 5 个**\n- **知识点总数不超过 120 条**'
--            ),
--            '│   ├── 1.1 传输介质\n│   │   ├── 双绞线\n│   │   ├── 光纤\n│   │   ├── 同轴电缆\n│   │   └── 无线信道',
--            '│   ├── 1.1 传输介质\n│   │   ├── 双绞线\n│   │   └── 光纤'
--          ),
--          '先自己定这个目录该有的知识点标题（几个由内容决定，宁少勿凑），再按同样的格式写正文。',
--          '先自己定这个目录该有的 3 到 5 个知识点标题，再按同样的格式写正文。'
--        ),
--        update_time = NOW()
--  WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';
--
-- UPDATE corecraft.sys_config
--    SET config_value = REPLACE(
--          config_value,
--          '先自己定这个目录该有的知识点标题（几个由内容决定，宁少勿凑），再按同样的格式写正文。',
--          '先自己定这个目录该有的 3 到 5 个知识点标题，再按同样的格式写正文。'
--        ),
--        update_time = NOW()
--  WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.point';
