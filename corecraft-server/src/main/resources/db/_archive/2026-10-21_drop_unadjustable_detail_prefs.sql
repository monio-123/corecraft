-- 四个界面偏好项不再可配置：值写死在代码里（或整个删掉），配置树上也删掉它们
--
-- 【为什么】
-- 用户 2026-10-21：
--   "顶栏下 正文上方 正文左右 这几个都删除 写死的值就按现在的默认值就行"
--   "还有一个阅读宽度 我试了调整好像一点用都没有 …… 是不是也可以删除掉"
--
-- 前三项是**框的固定留白**，写死在 CSS 的 token 里：
--   顶栏下   = .bar 的 margin-bottom      → var(--sp-11) = 14px
--   正文上方 = 卡片的 padding-top        → var(--sp-12) = 16px
--   正文左右 = 卡片的 padding-left/right → var(--sp-12) = 16px
--
-- 第四项「阅读宽度」是**详情页整体的 max-width 上限**（默认 2400 ≈ 不封顶）：
-- 它是 #144 取消 920 硬封顶时留下的"可选封顶"，但默认 2400 在比它窄的窗口上
-- 根本不生效 ⇒ **调它像调了个摆设**（用户原话："我试了调整好像一点用都没有"）。
-- 现在详情页在所有窗口下都是 100% 铺满内容区，**不再有任何宽度封顶**。
--
-- 留在偏好面板里只会让人以为"这几个该调"，而前两项和「正文白边」在效果上重叠：
-- **文字离框边的距离 = 卡片内距 + 白边**，两个旋钮调的是同一件事。
-- （「正文白边」本身**仍然可调** —— 它管文字离框边那条缝，和上面几项不是一回事。）
--
-- 写死之后**默认观感一模一样**：前三项原来的默认值就是那三个数；
-- 「正文白边」的默认是"没拖过 ⇒ 走 CSS 的自动值"，自动值在默认字号（14px）下
-- 正好是 2em + 缝 = 34px。
--
-- 【执行】
-- 已执行（2026-10-21）。一次性迁移，归档在此；
-- 新装库跑 2026-10-21_sys_config_node_tree.sql 就行（那边的树已经是删过之后的形状）。
--
-- 【顺带】
-- 把这四个键在 sys_config 里的行也删掉：万一有人拖过滑块，那行就是没人认领的垃圾。
-- （本库执行时这几行本来就不存在 —— 没有人的覆盖值。）

DELETE FROM `sys_config_node`
WHERE `config_key` IN (
  'ui.detail.detailBarGap',
  'ui.detail.detailContentPadY',
  'ui.detail.detailContentPadX',
  'ui.detail.detailReadW'
);

DELETE FROM `sys_config`
WHERE `config_key` IN (
  'ui.detail.detailBarGap',
  'ui.detail.detailContentPadY',
  'ui.detail.detailContentPadX',
  'ui.detail.detailReadW'
);
