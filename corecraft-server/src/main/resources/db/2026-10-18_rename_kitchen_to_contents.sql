-- =============================================================================
-- 2026-10-18 自测题存储：业务分区 kitchen → contents
--
-- 为什么改：kitchen 是随手挑的「厨房/灶台」比喻，**没有任何语义依据**。
-- 「内容」这条知识里有链接、这个文件归谁、管到哪，不该靠一个比喻词承载。
-- 改用 contents，对应 `kp_topic.content`（正文），和 `avatars` 一样是**领域名词**。
--
-- ⚠️ **只改业务这一层，不加「文件类别」层**（images / files / videos）。
--    类别应该由 ObjectFiles 从**扩展名推导**，而不是让前端传 ——
--    前端能传的参数就是能填错的参数，而「路径说 files/ 里放了个 .png」
--    这种两份真相正是这个项目反复踩的坑。
--    类别层随「文件上传」那个 feature 一起加，届时还会连同
--    「搬对象 + 改正文链接」的迁移一起做。
--
-- 执行时机：后端发新版（SCOPES 改成 contents）之后、之前都行；
--           但**必须在前端开始上传新图之前**执行，否则新老链接会并存。
--           桶里已上传的对象要同步搬（mc mv），脚本管不了对象存储。
-- =============================================================================

-- 正文里的图片链接。2026-10-18 实测命中 7 处：id=84 一处、id=85 六处
UPDATE `kp_topic`
SET `content` = REPLACE(`content`, '/api/files/kitchen/', '/api/files/contents/')
WHERE `content` LIKE '%/api/files/kitchen/%';

-- 题目里理论上也可能嵌图。**当前命中 0 行**，但保留这条：
-- 题目是模型生成的，下次出题随时可能往题干里塞图，
-- 到时候这条 SQL 就是现成的，而"等真出了问题再补"往往就忘了。
UPDATE `kp_topic_quiz`
SET `question` = REPLACE(`question`, '/api/files/kitchen/', '/api/files/contents/')
WHERE `question` LIKE '%/api/files/kitchen/%';
