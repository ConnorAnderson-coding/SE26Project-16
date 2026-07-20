import http from './http'

function normalizeCheckIn(record) {
  if (!record) return null
  return {
    ...record,
    id: String(record.id),
    activityId: String(record.activityId)
  }
}

export async function createQrSession(activityId) {
  return http.post('/checkins/qrcode/session', { activityId: Number(activityId) })
}

export async function checkInByQr({ activityId, token }) {
  return normalizeCheckIn(await http.post('/checkins/qrcode', {
    activityId: Number(activityId),
    token
  }))
}

export async function createPasswordSession(activityId) {
  return http.post('/checkins/password/session', { activityId: Number(activityId) })
}

export async function checkInByPassword({ activityId, code }) {
  return normalizeCheckIn(await http.post('/checkins/password', {
    activityId: Number(activityId),
    code
  }))
}

export async function checkInByLocation({ activityId, latitude, longitude }) {
  return normalizeCheckIn(await http.post('/checkins/location', {
    activityId: Number(activityId),
    latitude,
    longitude
  }))
}

export async function getMine() {
  const data = await http.get('/checkins/mine')
  return data.map(normalizeCheckIn)
}

export async function listByActivity(activityId) {
  const data = await http.get('/checkins', { params: { activityId: Number(activityId) } })
  return data.map(normalizeCheckIn)
}

export async function getStats(activityId) {
  const data = await http.get('/checkins/stats', { params: { activityId: Number(activityId) } })
  return {
    ...data,
    activityId: String(data.activityId),
    records: (data.records || []).map(normalizeCheckIn)
  }
}
