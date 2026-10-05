import { bootstrapSession } from '../../utils/session'
import { ensureTopicsLoaded, ensureCategoriesLoaded } from './useCorecraftWebStore'

/**
 * 业务壳启动要的三份数据：会话（菜单 / 资料 / 权限）、知识点、知识目录。
 *
 * 原来 `AppLayout` 和 `AppHomeView` 的 onMounted 各写了一遍**一模一样**的
 * `Promise.all([bootstrapSession(), ensureTopicsLoaded(), ensureCategoriesLoaded()])`，
 * 同一个顺序，只有拿到结果之后做的事不同（一个存整份快照，一个只取 profile）。
 * 重复即收敛——而且这个重复正好卡在"失败之后没有重试入口"上：
 * 收敛到一处，加重试就只需要改一个地方。
 *
 * 返回的是会话快照（`{ menuTree, menuPaths, profile }`），调用方各取所需。
 * 不在这里存成共享 state：两个调用点要的东西本来就不一样（壳要整份、首页只要 profile），
 * 硬塞成一份反而多一层间接。
 *
 * 三个 ensure 各自幂等（`bootstrapSession` 有 snapshot 短路、store 三个有 xxxLoaded 标志），
 * 所以壳和页面都调它不会打两次接口。
 */
export async function loadShellData() {
  const [session] = await Promise.all([
    bootstrapSession(),
    ensureTopicsLoaded(),
    ensureCategoriesLoaded()
  ])
  return session
}
