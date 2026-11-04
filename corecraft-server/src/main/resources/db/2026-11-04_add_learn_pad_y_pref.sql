-- ============================================================================
-- 2026-11-04 新增界面偏好项：知识点页「顶部间距」（ui.learn.learnPadY）
--
-- 【为什么加这一项】
--   面包屑（一级目录 › 二级目录）离顶栏太远（用户 2026-11-04：「这个一级目录 -> 二级目录
--   往上一点 现在离上面的间隙比较大显得不好看」）。那一屏的内容整体往下压了一截。
--
--   ⚠️ **它和已有的「上下」不是一回事**（用户原话：「目前的上下改的是每个框的上下间隔
--   不是整体往上的间隔」）：
--     · 「上下」= ui.learn.learnGap  = 区块**之间**的间距（面包屑 / 筛选条 / 卡片区彼此隔多远）
--     · 「顶部间距」= 本项            = **整体往上的偏移**（内容区离顶栏多远）
--   ⇒ 两个都要，别拿「上下」顶替。
--
-- 【定位方式】
--   父节点按 `config_key='ui.learn'` 找，**不硬编码 id**（跨环境稳定）。
--   叶子按 `config_key='ui.learn.learnPadY'` 判重 ⇒ **可重复执行**。
--
-- 【为什么脚本在库里、代码里也有一份】
--   偏好项的**名字和分组**来自配置树（sys_config_node，管理员在「系统管理 → 系统配置」里
--   能改名 / 挪组）；**范围、步长、CSS 变量映射**在代码里（uiPrefs.js 的 PREF_SPECS）——
--   配置树是通用的 KV，塞不进"最小 / 最大 / 步长"这种界面偏好专属的东西。
--   ⇒ 少一行 ⇒ 面板上就不显示这一项（面板只列 PREF_SPECS 里有的键）。
--
-- 【默认值】
--   代码里的内置默认 = 16px（原来跟通用 padding 一样是 32px ⇒ 面包屑离顶栏太远）。
--   **不往 sys_config 插默认值行**：那一行是"系统默认"（管理员设的），
--   没有它就用内置默认 —— 和「上下 / 左右 / 卡片间距」几项的现状一致。
--
-- 【回滚】
--   见文件末尾（删掉这一行节点即可；代码侧的 PREF_SPECS 与 CSS 回滚见 FEATURES-history）。
--
-- 收口：只动 corecraft 库（106.53.205.69:3306）。
-- 执行：跑前先看「跑前自检」，跑完看「跑后校验」。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 跑前自检
-- ---------------------------------------------------------------------------
-- 1) 父节点在不在（「知识点」组）
SELECT id, name, config_key FROM sys_config_node WHERE config_key = 'ui.learn';
-- 2) 这一项是不是已经在了（在了 ⇒ 脚本跳过，不重复插）
SELECT id, name, config_key FROM sys_config_node WHERE config_key = 'ui.learn.learnPadY';
-- 3) 同一组下现有的项（看一眼排序，别把别人的位置挤掉）
SELECT id, name, config_key, sort FROM sys_config_node
 WHERE parent_id = (SELECT id FROM sys_config_node WHERE config_key = 'ui.learn')
 ORDER BY sort;

-- ---------------------------------------------------------------------------
-- 执行
-- ---------------------------------------------------------------------------
INSERT INTO sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id,
       '顶部间距',
       'learnPadY',
       'ui.learn.learnPadY',
       'number',
       5,
       '内容整体离顶栏的距离。跟「上下」不是一回事：那是区块之间的间距，这是整体往上的偏移'
  FROM sys_config_node p
 WHERE p.config_key = 'ui.learn'
   AND NOT EXISTS (
         SELECT 1 FROM sys_config_node WHERE config_key = 'ui.learn.learnPadY'
       );

-- ---------------------------------------------------------------------------
-- 跑后校验
-- ---------------------------------------------------------------------------
-- 1) 只有一行，且挂在「知识点」组下
SELECT n.id, n.parent_id, g.name AS group_name, n.name, n.code, n.config_key, n.value_type, n.sort
  FROM sys_config_node n
  LEFT JOIN sys_config_node g ON g.id = n.parent_id
 WHERE n.config_key = 'ui.learn.learnPadY';
-- 2) 这一组现在长这样（滑块顺序）
SELECT name, code, sort FROM sys_config_node
 WHERE parent_id = (SELECT id FROM sys_config_node WHERE config_key = 'ui.learn')
 ORDER BY sort;
-- 3) 没有重复的 config_key
SELECT config_key, COUNT(*) AS cnt FROM sys_config_node
 WHERE config_key IN ('ui.learn.learnGap','ui.learn.learnPadX','ui.learn.learnGridGap',
                      'ui.learn.learnPillPy','ui.learn.learnPadY')
 GROUP BY config_key HAVING COUNT(*) > 1;
--    期望：**空集**（有行 ⇒ 重复插了）

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------
-- DELETE FROM sys_config_node WHERE config_key = 'ui.learn.learnPadY';
-- （代码侧还要把 uiPrefs.js 的 PREF_SPECS 里那一项删掉、AppLayout 的 padding-top 改回去，
--   否则面板会少一行但 CSS 还在读那个变量。）
