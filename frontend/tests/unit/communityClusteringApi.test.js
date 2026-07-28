import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../src/services/http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

import http from '../../src/services/http'
import {
  getClusteringRun,
  getClusteringRuns,
  getCommunityMembers,
  getLatestClustering,
  getMyCommunity,
  submitClusteringRun
} from '../../src/services/communityClusteringApi'

describe('community clustering API', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads the public latest result and current membership', async () => {
    await getLatestClustering()
    await getMyCommunity()
    expect(http.get).toHaveBeenNthCalledWith(1, '/community-clustering/latest')
    expect(http.get).toHaveBeenNthCalledWith(2, '/community-clustering/me')
  })

  it('uses fixed server-side pagination endpoints', async () => {
    await getClusteringRuns(2, 10)
    await getCommunityMembers('community/a', 1, 20)
    expect(http.get).toHaveBeenNthCalledWith(1, '/admin/community-clustering/runs', {
      params: { page: 2, size: 10 }
    })
    expect(http.get).toHaveBeenNthCalledWith(
      2,
      '/admin/community-clustering/communities/community%2Fa/members',
      { params: { page: 1, size: 20 } }
    )
  })

  it('submits only the cluster count and safely encodes run ids', async () => {
    await submitClusteringRun(4)
    await getClusteringRun('run/a')
    expect(http.post).toHaveBeenCalledWith('/admin/community-clustering/runs', { clusterCount: 4 })
    expect(http.get).toHaveBeenCalledWith('/admin/community-clustering/runs/run%2Fa')
  })
})
