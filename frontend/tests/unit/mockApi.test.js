import { describe, expect, it, vi } from 'vitest'
import {
  checkIn,
  getAnalyticsData,
  getCheckIns,
  getCommunityClusters,
  getUsers
} from '../../src/services/mock/mockApi'

describe('legacy mock API', () => {
  it('returns check-ins and prevents duplicate attendance', async () => {
    const before = await getCheckIns()
    const activityId = `activity-${Date.now()}`
    const userId = `user-${Date.now()}`
    const first = await checkIn(activityId, userId, 'qrcode')
    expect(first).toMatchObject({ success: true })
    expect((await getCheckIns()).length).toBe(before.length + 1)
    await expect(checkIn(activityId, userId, 'password')).resolves.toMatchObject({
      success: false
    })
  })

  it('returns users without passwords and community clusters', async () => {
    const users = await getUsers()
    expect(users.length).toBeGreaterThan(0)
    expect(users[0]).not.toHaveProperty('password')
    await expect(getCommunityClusters()).resolves.toBeDefined()
  })

  it('builds analytics data with supplied and default arrays', async () => {
    await expect(getAnalyticsData({ activities: [1], signups: [2], feedbacks: [3] }))
      .resolves.toMatchObject({ activities: [1], signups: [2], feedbacks: [3] })
    await expect(getAnalyticsData({})).resolves.toMatchObject({
      activities: [],
      signups: [],
      feedbacks: []
    })
  })
})
