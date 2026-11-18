-- ============================================================
-- 2026-11-17 AI 问答抽屉引导问题清理与焕新
-- 1. 清理已下线功能的残留节点/配置（pick_tags、organize）
-- 2. 新增「精简润色」(polish:none)、「补充实例」(examples:none)，对接【正文开始】/【正文结束】一键写入正文
-- 3. 「命名知识点」更名为「提炼标题」，重排 sort (1~5)
--
-- 执行时机：本次迁移在 dev/线上库执行一次；幂等守卫支持安全重跑。
-- ============================================================

-- ---------- 1. 跑前自检 ----------
SELECT id, parent_id, name, code, config_key, sort
  FROM corecraft.sys_config_node
 WHERE config_key LIKE 'ai.prompt.question%'
 ORDER BY sort, id;

-- ---------- 2. 幂等变更 ----------

-- ① 彻底删除已退役的「标签推荐」与「补全标题与目录」
DELETE FROM corecraft.sys_config WHERE config_key IN (
  'ai.prompt.question.pick_tags:tags',
  'ai.prompt.question.organize:categories,tags'
);
DELETE FROM corecraft.sys_config_node WHERE config_key IN (
  'ai.prompt.question.pick_tags:tags',
  'ai.prompt.question.organize:categories,tags'
);

-- ② 更新存量引导问题的名称与排序
UPDATE corecraft.sys_config_node
SET name = '梳理重点', sort = 1
WHERE config_key = 'ai.prompt.question.comb:none';

UPDATE corecraft.sys_config_node
SET name = '关联目录', sort = 4
WHERE config_key = 'ai.prompt.question.pick_category:categories';

UPDATE corecraft.sys_config_node
SET name = '提炼标题', sort = 5, remark = '根据正文提炼 12 字以内标题'
WHERE config_key = 'ai.prompt.question.rename:none';

-- ③ 新增「精简润色」（sort = 2）与「补充实例」（sort = 3）节点
INSERT INTO corecraft.sys_config_node
  (parent_id, name, code, config_key, value_type, sort, remark)
SELECT
  p.id, '精简润色', 'polish:none', 'ai.prompt.question.polish:none', 'text', 2,
  '去重理顺当前正文结构并输出完整改写稿（支持一键写入正文）'
FROM corecraft.sys_config_node p
WHERE p.config_key = 'ai.prompt.question'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM corecraft.sys_config_node WHERE config_key = 'ai.prompt.question.polish:none') x
  );

INSERT INTO corecraft.sys_config_node
  (parent_id, name, code, config_key, value_type, sort, remark)
SELECT
  p.id, '补充实例', 'examples:none', 'ai.prompt.question.examples:none', 'text', 3,
  '为正文中的核心概念补充代码示例或正反例（支持一键写入正文）'
FROM corecraft.sys_config_node p
WHERE p.config_key = 'ai.prompt.question'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM corecraft.sys_config_node WHERE config_key = 'ai.prompt.question.examples:none') x
  );

-- ④ 写入「精简润色」与「补充实例」的系统默认提示词（复用 ai.prompt.edit.body 的改写规范与【正文开始】/【正文结束】契约）
INSERT INTO corecraft.sys_config (user_id, config_key, config_value, remark)
SELECT
  0,
  'ai.prompt.question.polish:none',
  '【判据】对当前知识点正文做精简与结构润色：删去重复啰嗦的铺垫、理顺标题层级与列表结构、把模糊表述改为准确结论，保留全部核心事实、命令、代码与图表。\n【引用】ai.prompt.edit.body\n【格式】只输出润色后的完整正文，第一行写 【正文开始】，最后一行写 【正文结束】，标记外不要写任何解释。',
  '精简润色正文，输出可一键写入正文'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT id FROM corecraft.sys_config WHERE user_id = 0 AND config_key = 'ai.prompt.question.polish:none') x
);

INSERT INTO corecraft.sys_config (user_id, config_key, config_value, remark)
SELECT
  0,
  'ai.prompt.question.examples:none',
  '【判据】阅读当前知识点正文，找出其中最关键但缺少具体演示的概念或规则，在对应小节下补充简短、可直接验证的代码示例或正反对比案例（已有例子的段落原样保留）。\n【引用】ai.prompt.edit.body\n【格式】只输出补充实例后的完整正文，第一行写 【正文开始】，最后一行写 【正文结束】，标记外不要写任何解释。',
  '补充实例后输出完整正文，可一键写入正文或存为新知识点'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT id FROM corecraft.sys_config WHERE user_id = 0 AND config_key = 'ai.prompt.question.examples:none') x
);

-- ---------- 3. 跑后校验 ----------
SELECT name, code, config_key, sort
  FROM corecraft.sys_config_node
 WHERE config_key LIKE 'ai.prompt.question.%'
 ORDER BY sort;

SELECT config_key
  FROM corecraft.sys_config
 WHERE config_key LIKE 'ai.prompt.question.%'
 ORDER BY config_key;

-- ---------- 4. 回滚段（按需手动执行） ----------
-- DELETE FROM corecraft.sys_config WHERE config_key IN ('ai.prompt.question.polish:none', 'ai.prompt.question.examples:none');
-- DELETE FROM corecraft.sys_config_node WHERE config_key IN ('ai.prompt.question.polish:none', 'ai.prompt.question.examples:none');
-- （被删/改的历史节点与配置见 _backup/2026-11-17_ai_question_backup.json）
