# 已归档的 SQL 脚本

以下脚本已被 `knowledge_platform.sql` 完全重构覆盖，**不要在生产或本地再跑**。
保留仅供查阅历史决策。

## 为什么归档

业务表 SQL 维护成本太高：5 张表分散在 4 个脚本（DDL + 清空 + 2 个增量），每次想完全重置都要想"先跑哪个、再跑哪个"。

现在统一为 `knowledge_platform.sql` 单一入口（DROP + CREATE 幂等可重跑）。

## 归档文件

| 文件 | 原作用 | 失效原因 |
|------|--------|----------|
| `kp_clear_data.sql` | 业务表 truncate + corecraft-web 菜单清理 | 业务表清空已并入 `knowledge_platform.sql` 顶部；菜单清理用 `clear_all_menus.sql` |
| `kp_add_user_id.sql` | 给 kp_topic 加 user_id 字段（增量迁移） | 新版 `knowledge_platform.sql` 直接建表已含 user_id，不需要增量 |
| `kp_add_tree_id_and_topic_detail_menu.sql` | 给 kp_topic 加 tree_id 字段 + 挂载详情页菜单 | tree_id 已并入新 DDL；详情页菜单挂载逻辑已并入 `corecraft-web_menu.sql` |

## 一次性迁移（已执行完；**回滚靠同目录里的原件**）

这几份是**跑过一次就完事**的迁移脚本（不是"被 knowledge_platform.sql 覆盖"那种）：
它们的判据写死在当时的数据上，重跑没有意义（甚至会撞唯一键）。
留在活跃目录里的坏处是：部署清单要逐个判断"这个要不要跑"，而判断错一次就是数据事故。
⇒ 归档到这里，**内容一个字没改**（回滚还要靠它们）。

| 文件 | 原作用 | 为什么归档 |
|------|--------|------------|
| `2026-10-29_delete_empty_category_trees.sql` | 删 71 棵"整棵子树都没有可见知识点"的空目录 | 跑过一次就完了；回滚用同目录 `2026-10-29_empty_category_trees.sql` |
| `2026-10-29_delete_java_basics_bak.sql` | 删「Java基础bak」整棵子树（87 个目录 + 240 条知识点软删） | 同上；回滚用同目录 `2026-10-29_java_basics_bak_categories.sql` |
| `2026-11-02_remove_learn_menu_item.sql` | 软删「知识点」菜单项（`sys_permission.code='corecraft-web:learn'`） | 同上；`menu_reset.sql` 已同步不再重建它 |
| `2026-11-02_add_xiaomi_vendor.sql.merged-into-ai_vendor_catalog` | 加「小米 MiMo」厂商 + 型号 | 内容已并进 `../2026-10-26_ai_vendor_catalog.sql`（那份现在带幂等守卫）⇒ 这份留作历史 |

## 历史背景

- 2025-06：初版 `knowledge_platform.sql`（4 张表：kp_topic / kp_topic_tag / kp_topic_quiz / kp_topic_relation + 树/节点相关 3 张）
- 2025-07：加 user_id 字段（数据按用户隔离）→ `kp_add_user_id.sql`
- 2026-07：表砍到 4 张（树改为 topic 派生视图）
- 2026-07：详情页路由 + 加 tree_id 字段 + 挂菜单 → `kp_add_tree_id_and_topic_detail_menu.sql`
- 2026-08：tag 模型重构（独立 kp_tag 主表）+ 5 张表统一到 `knowledge_platform.sql`，原 3 个迁移/清理脚本归档
