import { ApiError, request } from './http'
import { csrfPost } from './csrf'

const RUN_PATH_PREFIX = '/api/v1/admin/community-clustering/runs/'
const RUN_PATH_PATTERN = /^\/api\/v1\/admin\/community-clustering\/runs\/[^/?#]+$/

export function getLatestClustering({ signal } = {}) {
  return request('/api/v1/community-clustering/latest', { signal })
    .then(result => result.data)
}

export function getMyClustering({ signal } = {}) {
  return request('/api/v1/community-clustering/me', { signal })
    .then(result => result.data)
}

export function resolveRunLocation(location, runId) {
  const fallback = `${RUN_PATH_PREFIX}${encodeURIComponent(runId)}`
  if (!location) return { path: fallback, protocolWarning: true }

  try {
    const url = new URL(location, window.location.origin)
    const isSameOrigin = url.origin === window.location.origin
    const isExpectedPath = url.pathname === fallback
    const hasNoExtraParts = !url.search && !url.hash
    if (isSameOrigin && isExpectedPath && hasNoExtraParts) {
      return { path: url.pathname, protocolWarning: false }
    }
  } catch {
    // Fall through to the known Spring route.
  }

  return { path: fallback, protocolWarning: true }
}

export async function triggerClustering(clusterCount, { signal } = {}) {
  const result = await csrfPost('/api/v1/admin/community-clustering/runs', {
    json: { clusterCount },
    signal
  })
  if (result.status !== 202 || result.data?.status !== 'PENDING' || !result.data?.runId) {
    throw new ApiError({
      status: result.status,
      code: 'INVALID_RESPONSE',
      message: '服务器未按约定接受聚类任务'
    })
  }
  const resolved = resolveRunLocation(result.location, result.data.runId)
  return {
    run: result.data,
    runPath: resolved.path,
    protocolWarning: resolved.protocolWarning
  }
}

export function getClusteringRun(runPath, { signal } = {}) {
  if (typeof runPath !== 'string' || !RUN_PATH_PATTERN.test(runPath)) {
    return Promise.reject(new ApiError({
      status: 0,
      code: 'INVALID_RUN_LOCATION',
      message: '聚类任务地址无效'
    }))
  }
  return request(runPath, { signal }).then(result => result.data)
}
