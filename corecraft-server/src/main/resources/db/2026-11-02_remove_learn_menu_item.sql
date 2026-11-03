-- ============================================================================
-- 2026-11-02 撤掉「知识点」菜单项（sys_permission.code = 'corecraft-web:learn'）
--
-- 【为什么撤】
--   侧边栏原来的 nav 块里，「知识点」点进去是**全体知识点平铺**（317 条）——
--   没有方向、没有范围，是个"所有东西"的容器。而这个产品真正的用法是：
--   从**目录**进去看、或从**首页**看当天新增 / 待归类。
--   2026-11-02 的界面重构把侧边栏的 nav 块整个撤掉了（首页入口进了品牌区），
--   这个菜单项在界面上**已经没有任何位置** ⇒ 库里这一行也该跟着撤，
--   否则它是一条"配了但没人看得见"的僵尸配置（下次重置菜单还会冒出来）。
--
--   ⚠️ **撤的只是菜单项，不是这一页**：
--   `/app/learn`（知识点列表）还在 —— 从侧边栏目录、首页的「待归类」「今日新增」卡、
--   正文里的目录链接都能进。路由**不校验**这个菜单项
--   （router/index.js 的 protectedMenuPaths 里没有 /app/learn），
--   所以撤掉它不影响任何人访问这一页。
--
-- 【为什么只软删这一行、别的都不动】
--   · **id 15「首页」（code='corecraft-web:home'，path='/app'）必须保留**：
--     前端 `hasAppMenus()`（utils/session.js）判的是"菜单路径里有没有 /app 开头"，
--     超管从管理后台回业务界面靠它 ⇒ 删了首页就回不去了。
--   · **id 14「知识点详情」（path='/app/topic/:id'）保留**：它本来就不进导航
--     （meta.path 含 ':'，前端 isVisibleToUser 直接过滤掉），留着无碍。
--   · **sys_role_permission 的两行分配（id 158 / 163）保留**：权限一行删了之后，
--     角色的分配就成了悬空引用。留着 ⇒ 回滚只要一条 UPDATE，不用重建分配。
--
-- 【定位方式】
--   按 `code='corecraft-web:learn'` 定位，**不硬编码 id**（跨环境稳定，
--   与 _archive 里那几个菜单脚本同一口径）。
--
-- 【软删，不是物理删】
--   `sys_permission` 继承 BaseEntity ⇒ 带 `@TableLogic`（is_delete）。
--   MyBatis-Plus 的每一次查询都会自动带上 `is_delete = 0`，
--   所以这一行立刻从**所有**菜单查询里消失（资源管理页、菜单树、用户权限装载）。
--   数据本身留在库里 ⇒ 回滚是一条 UPDATE。
--
-- 【回滚】
--   见文件末尾。回滚后**要刷新页面（F5）**：菜单树在模块级缓存 + localStorage
--   两处都留着旧的一份，不刷新看不到。
--
-- 收口：只动 corecraft 库（106.53.205.69:3306）。
-- 执行：跑前先看「跑前自检」，跑完看「跑后校验」。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 跑前自检
-- ---------------------------------------------------------------------------

-- ① 待撤的那一行（应为 1 行：id=12 / name=知识点 / code=corecraft-web:learn / path=/app/learn）
SELECT id, name, code, sort, type, parent_id, is_enabled, is_delete, meta
FROM corecraft.sys_permission
WHERE code = 'corecraft-web:learn';

-- ② 它被哪些角色分配了（**留档，不动**）
SELECT srp.id, srp.role_id, sr.name AS role_name, srp.permission_id, srp.is_delete
FROM corecraft.sys_role_permission srp
LEFT JOIN corecraft.sys_role sr ON sr.id = srp.role_id
WHERE srp.permission_id IN (
  SELECT id FROM corecraft.sys_permission WHERE code = 'corecraft-web:learn'
);

-- ③ **首页那一行必须在**（hasAppMenus 靠它）
SELECT id, name, code, is_delete FROM corecraft.sys_permission
WHERE code = 'corecraft-web:home';

-- ④ 业务壳（shell=app）现有菜单：撤之前应该看到「首页」+「知识点」+「知识点详情」
SELECT id, name, code, meta FROM corecraft.sys_permission
WHERE meta LIKE '%"shell":"app"%' AND is_delete = 0
ORDER BY id;

-- ---------------------------------------------------------------------------
-- 变更
-- ---------------------------------------------------------------------------

-- **软删**这一行（is_delete 0 → 1）。
--   ⚠️ 不动 is_enabled：is_enabled=0 是"这行还在、只是先不显示"（能被重新打开），
--      is_delete=1 才是"这条权限没了"。这里要的是后者。
UPDATE corecraft.sys_permission
SET is_delete = 1,
    update_time = NOW()
WHERE code = 'corecraft-web:learn'
  AND is_delete = 0;

-- ---------------------------------------------------------------------------
-- 跑后校验
-- ---------------------------------------------------------------------------

-- ① 这一行应是软删状态（is_delete=1，**数据还在**）
SELECT id, name, code, is_delete, update_time
FROM corecraft.sys_permission
WHERE code = 'corecraft-web:learn';

-- ② 首页那一行**必须还是活的**（is_delete=0）
SELECT id, name, code, is_delete FROM corecraft.sys_permission
WHERE code = 'corecraft-web:home';

-- ③ 业务壳剩下的菜单：**不再有「知识点」**
SELECT id, name, code, meta FROM corecraft.sys_permission
WHERE meta LIKE '%"shell":"app"%' AND is_delete = 0
ORDER BY id;

-- ④ 前端 hasAppMenus() 的判据：可见菜单里还得有 /app 开头的路径
--    （等价于 ③ 里有 path='/app' 那一行 ⇒ 超管还能回业务界面）
SELECT COUNT(*) AS app_menu_paths
FROM corecraft.sys_permission
WHERE is_delete = 0
  AND JSON_UNQUOTE(JSON_EXTRACT(meta, '$.path')) LIKE '/app%';

-- ⑤ 角色分配原样保留（回滚路径完好）
SELECT COUNT(*) AS role_grants
FROM corecraft.sys_role_permission
WHERE permission_id IN (
  SELECT id FROM corecraft.sys_permission WHERE code = 'corecraft-web:learn'
)
  AND is_delete = 0;

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------

-- 一条 UPDATE 就够了（软删是**逻辑删**，数据没动）：
-- UPDATE corecraft.sys_permission
-- SET is_delete = 0,
--     update_time = NOW()
-- WHERE code = 'corecraft-web:learn';
--
-- ⚠️ 回滚后**要刷新页面（F5）**：菜单树在模块级缓存 + localStorage 两处
--    都留着旧的一份，不刷新看不到。
-- ⚠️ 前端那一侧也得回：侧边栏的 nav 块（2026-11-02 撤的）要一起恢复，
--    否则菜单项回来了、界面还是无处显示。
