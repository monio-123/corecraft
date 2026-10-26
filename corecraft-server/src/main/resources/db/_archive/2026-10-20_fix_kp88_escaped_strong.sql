-- 一次性数据修复：把 kp_topic id=88 正文里「被转义成字面星号的粗体标记」还原成真正的粗体。
--
-- 背景
-- ----
-- 那条正文是从网页粘贴进来的，`<strong>List：</strong>` 这种 html 会被 ProseMirror
-- 直接解析成**真粗体标记**；但序列化回 markdown 时，remark 不知道自己的 strong
-- 后面还跟着什么，写出了 `**List：**List代表…` —— 闭合的 `**` 后面紧跟字母，
-- CommonMark 闭不上（要闭上的 `**` 同时也算"左向"）。于是：
--
--   1. 刷新时重新解析 → 粗体没了，`**` 变成**两个字面星号**
--   2. 再存一次 → 字面星号被转义 → `\*` `\*`（每个星号前面一个反斜杠）
--
-- 库里现在就是第 2 步的结果。显示出来是 `**List：**`（星号是字面的，不是粗体），
-- 而同一篇里真正的粗体 `**Collection派生出了三个子接口：**` 没被转义。
--
-- 修法（两条规则，按位置区分开/闭）
-- ----
--   1. 紧跟 `[` 的那一对 → `**[`   —— **后面不能加空格**
--      （加了空格，strong 就不包含那个链接了，渲染出来是"字面 **" + 普通链接）
--   2. 紧跟 `)` 的那一对 → `)** `   —— **后面必须加空格**
--      （不补的话又回到上面那个闭不上的坑，只是换了个方向）
--
-- 结果：
--   * \*\*[List：](/app/learn?category=58)\*\*List代表…
--   ↓
--   * **[List：](/app/learn?category=58)** List代表…
--
-- 范围
-- ----
-- **只动 id=88 这一行。** 同一张表里 id=40 / id=77 也含 `\*`，但那些是
-- **密码里的星号被 markdown 正常转义**（账号密码类内容），还原成 `**` 会把密码改掉。
-- 所以这里既限定 id，又限定 user_id。
--
-- 可重复执行：第二次跑时已经找不到 `\*` `\*` 了，REPLACE 命中 0 次，不会有副作用。
--
-- 回滚：执行前把 kp_topic id=88 的 content 备份一份
-- （本次备份在 corecraft-web/_backup_kp88_before_fix.json）。
--
-- ⚠️ 两条替换**有先后**：先 `\*\*[` → `**[`（开标记），再 `)\*\*` → `)** `（闭标记）。
-- 反过来做的话，开标记那一对会先被 `)\*\*` 规则误伤不到（开标记前面是 `* ` 不是 `)`），
-- 但顺序按"先开、后闭"写更好读，也避免以后改动时互相干扰。

UPDATE kp_topic
SET content = REPLACE(REPLACE(content, '\\*\\*\\[', '**['), ')\\*\\*', ')** ')
WHERE id = 88
  AND user_id = 6
  AND is_delete = 0;

-- 自查 1：这条正文里不该再有「反斜杠+星号」
SELECT id, CHAR_LENGTH(content) - CHAR_LENGTH(REPLACE(content, CONCAT(CHAR(92), '*'), '')) AS 剩余转义星号数
FROM kp_topic
WHERE id = 88 AND is_delete = 0;

-- 自查 2：另外两行**必须**还带着它们的转义星号（密码里的星号，不能动）
SELECT id, title, CHAR_LENGTH(content) - CHAR_LENGTH(REPLACE(content, CONCAT(CHAR(92), '*'), '')) AS 转义星号数
FROM kp_topic
WHERE id IN (40, 77) AND is_delete = 0;
