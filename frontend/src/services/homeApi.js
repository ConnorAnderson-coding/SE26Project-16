import http from './http'

export async function getHomeStats() {
  return http.get('/home/stats')
}
