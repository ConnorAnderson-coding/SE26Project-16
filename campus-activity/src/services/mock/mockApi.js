import {
  initialCheckIns,
  initialCommunityClusters,
  initialUsers
} from '../../data/mockData'

let checkIns = [...initialCheckIns]

export async function getCheckIns() {
  return checkIns
}

export async function checkIn(activityId, userId, method) {
  const existing = checkIns.find(
    c => c.activityId === activityId && c.userId === userId
  )
  if (existing) {
    return { success: false, message: '您已签到' }
  }
  const record = {
    id: `c${Date.now()}`,
    activityId,
    userId,
    method,
    time: new Date().toISOString()
  }
  checkIns = [...checkIns, record]
  return { success: true, message: '签到成功', data: record }
}

export async function getUsers() {
  return initialUsers.map(({ password, ...user }) => user)
}

export async function getCommunityClusters() {
  return initialCommunityClusters
}

export async function getAnalyticsData({ activities, signups, feedbacks }) {
  return {
    activities: activities || [],
    signups: signups || [],
    checkIns,
    feedbacks: feedbacks || []
  }
}
