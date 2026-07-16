import http from './http'

export async function getActivityMetrics(activityId) {
  return http.get(`/analytics/activity/${activityId}/metrics`)
}

export async function getFullAnalysis(activityId) {
  return http.get(`/analytics/activity/${activityId}`)
}

/**
 * 触发分析。返回 { status, data } 包装，调用方根据 status 判断是否需要轮询：
 *   - 'pending'：后端已返回 202，分析在后台执行，需要轮询 getFullAnalysis
 *   - 'success'：旧路径直接拿到结果（向后兼容）
 * <p>
 * 后端现在的 trigger 是异步的：HTTP 202 + {analysisStatus: 'pending', metrics, ...}，
 * 数据最终落在 ActivityAnalysis，前端通过 pollAnalysisUntilReady 轮询读取。
 */
export async function triggerAnalysis(activityId) {
  const data = await http.post(`/analytics/trigger/${activityId}`)
  if (data && data.analysisStatus === 'pending') {
    return { status: 'pending', data }
  }
  return { status: 'success', data }
}

/**
 * 轮询活动分析状态，直到 analysisStatus 变成 'ready'/'failed' 或超时。
 * <p>
 * 最长等待 60 秒，每 2 秒一次。失败 / 超时不抛错，返回最后已知状态，
 * 由调用方决定如何展示。
 */
export async function pollAnalysisUntilReady(activityId, { intervalMs = 2000, timeoutMs = 60000 } = {}) {
  const deadline = Date.now() + timeoutMs
  let last = null
  while (Date.now() < deadline) {
    last = await getFullAnalysis(activityId)
    const status = last?.analysisStatus
    if (status === 'ready' || status === 'failed' || status === 'none') {
      return last
    }
    await new Promise(r => setTimeout(r, intervalMs))
  }
  return last
}
