-- ============================================================================
-- access / refresh token 有效期：1 小时 → 3 天（259200 秒）
--
-- 2026-10-05
--
-- 背景：真机使用中 1 小时到期一次太频繁（后端在 IntelliJ 重启还会额外清掉内存 JWT），
--       改成 3 天。
--
-- 改的是**库里 oauth2_registered_client.token_settings** 这一列 JSON，
-- 不是 Java 代码：RegisteredClientRepository 是 `JdbcRegisteredClientRepository`，
-- 有效期按 client 存在库里，`AuthorizationServerConfig` 里没有 accessTokenTimeToLive。
--
-- 只改字符串里那两个片段，不重写整列：另外几个设置（reuse-refresh-tokens /
-- access-token-format / authorization-code 等）的序列化格式必须原样保留，
-- 整列重写容易打错键名或类型标签，Spring 反序列化时直接炸。
--
-- ⚠️ 只对**之后签发**的 token 生效。已经发出去的 token 其 exp 写在 JWT 里，
--    改库不会让它们变长——要让当前登录态拿到 3 天的 token 需要重新登录一次。
--
-- ⚠️ 3 天的安全代价：token 一旦泄露，3 天内都能用；而且本前端目前**不存
--    refresh_token**（`utils/auth.js` 的 saveAuth 只存 access_token / token_type /
--    expires_in），所以没有"用 refresh 换新 access"这条路缩短会话——
--    想让某个会话提前失效，只能重新登录或改库。
-- ============================================================================

UPDATE oauth2_registered_client
SET token_settings = REPLACE(
        REPLACE(
            token_settings,
            '"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000]',
            '"settings.token.access-token-time-to-live":["java.time.Duration",259200.000000000]'
        ),
        '"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000]',
        '"settings.token.refresh-token-time-to-live":["java.time.Duration",259200.000000000]'
    )
WHERE id = '1';

-- 回查：应该看到 access / refresh 两项都是 259200.000000000，其余片段一字未动
SELECT id, client_id, token_settings FROM oauth2_registered_client WHERE id = '1';
