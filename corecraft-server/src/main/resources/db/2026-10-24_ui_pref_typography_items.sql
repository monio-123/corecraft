-- 界面偏好：新增 5 项「正文排版」配置（挂在详情组 ui.detail 下）
-- 2026-10-24
--
-- 背景：用户指出"标题和正文的字体大小 颜色 间隔区别等等等等 我们系统中并不明确"，
--   参照语雀的层次感 ⇒ 补一套明确的正文排版规范，并且**做成可配置**。
--
-- 为什么加在 `ui.detail`（详情组）而不是新开一组：
--   · 详情组自己的说明就写着"知识点详情页的**间距与字号**"；
--   · 用户要的是"跟字体（正文字号）放在一起" ⇒ 同一个组，面板里就是挨着的几行。
--
-- ⚠️ **配置树 = 偏好面板和系统配置的同一个数据源**（utils/uiPrefs.js 的 buildGroups
--   读的就是它）⇒ 加了这几行，**业务界面顶部的偏好面板**和**管理端
--   「系统管理 → 系统配置」**两个地方**自动都有**：
--   管理员改的是系统默认（user_id=0），每个人改的是自己的覆盖，
--   生效值 = 我的覆盖 > 系统默认 > 代码内置默认。
--
-- 只加树节点、**不加 sys_config 的值行**（跟现有 ui.* 一致：
--   现在 sys_config 里一条 ui.* 都没有 ⇒ 全走代码里的内置默认）。
--
-- 五项（"标题多大" + "块与块之间留多少"两件事，不多拆）：
--   标题大小   标题相对正文的放大倍数（1 = 语雀默认比例）
--   标题留白   标题上下留白的基准（上下不对称、随层级递减的比例写死在样式里）
--   行高       正文行与行之间的距离（比值）
--   段间距     段落之间留多少（列表 / 表格 / 引用 / 代码块也用它）
--   段首缩进   首行缩进几个字宽（默认 0 = 不缩进）
--
-- 执行时机：随时（配置即时生效，不用发版）。


-- ========== ① 清理（幂等：同一批 config_key 先删掉再插）============
DELETE FROM corecraft.sys_config_node
WHERE config_key IN (
  'ui.detail.headingScale',
  'ui.detail.headingGap',
  'ui.detail.lineHeight',
  'ui.detail.paraGap',
  'ui.detail.paraIndent'
);


-- ========== ② 加节点（父级按 config_key 找，不写死 id）============
INSERT INTO corecraft.sys_config_node
  (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id, '标题大小', 'headingScale', 'ui.detail.headingScale', 'number', 5,
  '标题相对正文的放大倍数。1 = 语雀默认比例。**标题不给独立字号**（层级比例是排版规范，这里只是整体调大调小）'
FROM corecraft.sys_config_node p WHERE p.config_key = 'ui.detail';

INSERT INTO corecraft.sys_config_node
  (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id, '标题留白', 'headingGap', 'ui.detail.headingGap', 'number', 6,
  '标题上下留白的基准。**上下不对称**（上方更松）且随层级递减，比例写死在样式里，这里拖的是那个基准值'
FROM corecraft.sys_config_node p WHERE p.config_key = 'ui.detail';

INSERT INTO corecraft.sys_config_node
  (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id, '行高', 'lineHeight', 'ui.detail.lineHeight', 'number', 7,
  '正文行与行之间的距离（比值，1.8 = 语雀那档）'
FROM corecraft.sys_config_node p WHERE p.config_key = 'ui.detail';

INSERT INTO corecraft.sys_config_node
  (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id, '段间距', 'paraGap', 'ui.detail.paraGap', 'number', 8,
  '段落之间留多少。**列表、表格、引用、代码块也用这一个值**（跟段落是同一件事）'
FROM corecraft.sys_config_node p WHERE p.config_key = 'ui.detail';

INSERT INTO corecraft.sys_config_node
  (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id, '段首缩进', 'paraIndent', 'ui.detail.paraIndent', 'number', 9,
  '首行缩进几个字宽（中文排版习惯）。默认 0 = 不缩进。单位是字宽不是像素 ⇒ 跟着字号缩放'
FROM corecraft.sys_config_node p WHERE p.config_key = 'ui.detail';


-- ========== ③ 跑后校验（详情组应有 9 个节点：4 个旧的 + 5 个新的）============
SELECT code, name, config_key, sort FROM corecraft.sys_config_node
 WHERE parent_id = (SELECT id FROM corecraft.sys_config_node WHERE config_key = 'ui.detail')
 ORDER BY sort;


-- ========== 回滚 ==========
-- DELETE FROM corecraft.sys_config_node
--  WHERE config_key IN (
--    'ui.detail.headingScale',
--    'ui.detail.headingGap',
--    'ui.detail.lineHeight',
--    'ui.detail.paraGap',
--    'ui.detail.paraIndent'
--  );
-- （样式和 PREF_SPECS 那边要一起回退，否则面板上的行会指向不存在的变量。）
