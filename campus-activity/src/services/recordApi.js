import http from './http'

export async function publishRecord(activityId, payload) {
  return http.post(`/activities/${activityId}/record`, payload)
}

export async function getRecord(activityId) {
  return http.get(`/activities/${activityId}/record`)
}
