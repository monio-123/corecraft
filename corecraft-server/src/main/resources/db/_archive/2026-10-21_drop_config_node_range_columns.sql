-- 配置树表删掉五列：min_value / max_value / step_value / unit / default_value
--
-- 【为什么】
-- 用户 2026-10-21："还有耦合问题 这些范围的提示放在描述/备注 等等之类的里面就行了
-- 都说了这个是通用的系统级别的配置 其实最主要的应该只有 k v 中文描述 等少数几个必要的"
--
-- 滑块的上下限 / 步长 / 单位 / 内置默认是**界面偏好这一件专属的**东西，
-- 塞进通用的系统级 KV 结构里就是耦合：新建一条普通 KV 时，界面上凭空多出
-- 五个填不上的输入框。
--
-- 拆开之后各归各位：
--   · 通用 KV（sys_config_node + sys_config）：中文描述 / 编码 / 值 / 说明；
--   · 界面偏好的范围（前端 uiPrefs.js 的 PREF_SPECS）：min / max / step / unit / def；
--   · **范围用一句人话写在「说明」里**（例："下限 200 是…"）—— 人能读懂那句话。
--
-- 【不影响什么】
-- · 实体 SysConfigNode 已经不映射这几列了（#235 清理时已删），前端也不读（#231 已删）；
-- · 面板的滑块范围来自前端的 PREF_SPECS（18 项，一个没少）；
-- · 库里这几列存的值是 2026-10-21 灌树时写进去的初始范围，删掉不丢信息
--   （同样的数在 PREF_SPECS 里，也写在各项的「说明」里）。
--
-- 【执行】
-- 已执行（2026-10-21）。一次性迁移，归档在此；
-- 新装库跑 2026-10-21_sys_config_node_tree.sql 就行（那边的建表语句已经是删过之后的形状）。

ALTER TABLE `sys_config_node`
  DROP COLUMN `min_value`,
  DROP COLUMN `max_value`,
  DROP COLUMN `step_value`,
  DROP COLUMN `unit`,
  DROP COLUMN `default_value`;
