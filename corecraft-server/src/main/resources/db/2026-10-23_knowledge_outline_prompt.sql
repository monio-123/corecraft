-- 目录树导入：AI 生成目录树的规范 + 配置树两个节点
-- 2026-10-23
--
-- 背景：出题范围收敛成"按目录"之后，**目录本身成了骨架**——
-- 用户手上有一套体系大纲（比如「计算机网络（后端&云原生精简版）」），
-- 想要的是"一次把目录树灌进来"，而不是一个一个手建。
--
-- 这个脚本加两样东西：
--   ① `sys_config` 里的规范文本（`ai.prompt.knowledge.outline`）
--   ② 配置树上对应的两个节点（容器 + 叶子）
--
-- ⚠️ **规范里的输出格式必须和"粘贴导入"的那棵一模一样**：
--    前端只认一种格式（`src/utils/outlineTree.js` 的 `parseOutlineTree`），
--    AI 出的树也得能被同一个解析器读懂 ⇒ **一个解析器、两条路共用**。
--    改了格式，解析器和提示词要一起改。
--
-- ⚠️ **键不能放 `ai.prompt.question.*`**：那个前缀下的叶子会被 `questions()`
--    拿去当抽屉里的引导问题按钮 ⇒ 抽屉里会多出一个没意义的按钮。
--
-- 执行时机：随时（配置即时生效，不用发版）。


-- ========== 规范文本 ==========
INSERT INTO corecraft.sys_config (user_id, config_key, config_value, remark)
VALUES (
  0,
  'ai.prompt.knowledge.outline',
  '把一个主题拆成一套目录树。**只输出目录树，不要任何解释、不要开场白。**

材料里给了两样东西：**主题**，和**现有目录清单**（缩进表示层级）。
- **优先复用已有目录**：清单里有的就用它，不要另起一个同义的新目录
- 要挂在某个已有目录下面时，**从那个目录开始逐层写出来**（"语言" → "Java" → "多线程"），只写这一支
- 清单里完全没有的（新领域）就新起一个顶层目录

输出的格式（**必须一模一样**，导入端只认这一种）：
计算机网络（后端&云原生精简版）
├── 绪论（核心概念）
│   ├── 网络基本概念、LAN/WAN
│   └── OSI七层 / TCP/IP四层模型，封装解封装
└── 物理层（极简）
    ├── 传输介质：双绞线、光纤、无线
    └── 设备：Hub、中继器

规则：
- **一层 4 个字符**：`├── ` / `└── `，中间的层用 `│   ` 占位，同级的最后一行用 4 个空格占位
- **最深 4 层**，不要超过
- 目录名是**领域或主题**（"传输层"、"容器网络"、"索引优化"），不是流程、场景、状态（"日常"、"上线流程"、"待办"）—— 目录是这条知识**主要在学谁**
- 目录名只写领域/主题本身，**不带序号**（挂在已有体系下，序号会乱）
- 同一层不要出现意思重复的目录；不同父下同名是允许的
- **叶子是知识点**（"传输介质：双绞线、光纤"这种内容），不是目录：照常列出来，但不用再往下拆
- 一个主题给 10 到 30 个目录就够，别把知识点全铺成目录
- 目录名用中文，简短，别写一整句话',
  '把一个主题拆成目录树时整份发给模型。输出格式必须和粘贴导入的那棵一模一样（前端只认一种格式）；材料里带主题和现有目录清单'
);


-- ========== 配置树：容器 + 叶子 ==========
INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT id, '知识体系', 'knowledge', 'ai.prompt.knowledge', NULL, 4, '按主题生成目录树用的规范'
FROM corecraft.sys_config_node WHERE config_key = 'ai.prompt';

INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT id, '目录生成规范', 'outline', 'ai.prompt.knowledge.outline', 'text', 1,
       '把一个主题拆成目录树时整份发给模型。输出格式必须和粘贴导入的那棵一模一样（前端只认一种格式）'
FROM corecraft.sys_config_node WHERE config_key = 'ai.prompt.knowledge';


-- ========== 回滚 ==========
-- DELETE FROM corecraft.sys_config WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';
-- DELETE FROM corecraft.sys_config_node WHERE config_key LIKE 'ai.prompt.knowledge%';
