import http from './http'

export function normalizeFeedback(item) {
  return {
    id: String(item.id),
    activityId: String(item.activityId),
    userId: item.userId,
    userName: item.userName,
    rating: item.rating,
    content: item.content,
    createdAt: item.createdAt,
    activityTitle: item.activityTitle
  }
}

export async function submitFeedback(payload) {
  const data = await http.post('/feedbacks', {
    ...payload,
    activityId: Number(payload.activityId)
  })
  return normalizeFeedback(data)
}

export async function getMyFeedbacks() {
  const data = await http.get('/feedbacks/mine')
  return data.map(normalizeFeedback)
}

export async function listFeedbacksByActivity(activityId) {
  const data = await http.get('/feedbacks', { params: { activityId: Number(activityId) } })
  return data.map(normalizeFeedback)
}
