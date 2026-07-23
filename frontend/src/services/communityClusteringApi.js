import http from './http'

export const getLatestClustering = () => http.get('/community-clustering/latest')

export const getMyCommunity = () => http.get('/community-clustering/me')

export const getClusteringRuns = (page = 0, size = 20) =>
  http.get('/admin/community-clustering/runs', { params: { page, size } })

export const getClusteringRun = runId =>
  http.get(`/admin/community-clustering/runs/${encodeURIComponent(runId)}`)

export const submitClusteringRun = (clusterCount = 2) =>
  http.post('/admin/community-clustering/runs', { clusterCount })

export const getCommunityMembers = (communityId, page = 0, size = 20) =>
  http.get(`/admin/community-clustering/communities/${encodeURIComponent(communityId)}/members`, {
    params: { page, size }
  })
