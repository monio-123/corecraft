import request from '../utils/request'

/**
 * 文件上传。**返回值是相对路径**（形如 `contents/xxx.png`），不是完整 URL。
 *
 * <p>为什么不直接返回完整 URL：域名和后端地址本来就配在前端，
 * 存相对路径换存储时（本地盘 → MinIO）正文里的链接一个字都不用改。
 */
export function uploadFile(file, scope) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('scope', scope)
  return request.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    // 上传单独放宽。默认上限 20MB 的文件，在慢一点的链路上要传十几秒；
    // 用全局值会在**文件还没传完**时就报超时，而那一刻其实只是在传，
    // 用户看到的是「网络错误」配一个根本没传完的文件。
    timeout: 120000
  })
}

/** 相对路径 → 可访问地址。正文里存的就是这个 */
export function fileUrl(path) {
  return path ? `/api/files/${path}` : ''
}
