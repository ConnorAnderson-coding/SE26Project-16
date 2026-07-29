import { beforeEach, describe, expect, it, vi } from 'vitest'

const http = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn()
}))

vi.mock('../../src/services/http', async () => {
  const actual = await vi.importActual('../../src/services/http')
  return { ...actual, default: http }
})

import * as activityApi from '../../src/services/activityApi'
import * as analyticsApi from '../../src/services/analyticsApi'
import * as authApi from '../../src/services/authApi'
import * as checkInApi from '../../src/services/checkInApi'
import * as clusteringApi from '../../src/services/communityClusteringApi'
import * as favoriteApi from '../../src/services/favoriteApi'
import * as feedbackApi from '../../src/services/feedbackApi'
import * as homeApi from '../../src/services/homeApi'
import * as recordApi from '../../src/services/recordApi'
import * as registrationApi from '../../src/services/registrationApi'
import * as userApi from '../../src/services/userApi'

const activity = {
  id: 7,
  organizerId: 'teacher-1',
  title: 'Workshop',
  startTime: '2026-07-01T10:00:00',
  endTime: '2026-07-01T12:00:00'
}

const registration = {
  id: 8,
  activityId: 7,
  userId: 'student-1',
  status: 'pending',
  createdAt: '2026-06-01T10:00:00',
  activityTitle: 'Workshop',
  userName: 'Student',
  college: 'Software',
  location: 'Room 1',
  startTime: '2026-07-01T10:00:00'
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
})

describe('activity API', () => {
  it('normalizes nullable and numeric activities', () => {
    expect(activityApi.normalizeActivity(null)).toBeNull()
    expect(activityApi.normalizeActivity(activity)).toMatchObject({ id: '7', organizerId: 'teacher-1' })
  })

  it('lists activities and supplies defaults for a missing content array', async () => {
    http.get.mockResolvedValueOnce({ content: [activity], totalElements: 1 })
    await expect(activityApi.listActivities({ page: 1 })).resolves.toMatchObject({
      content: [{ id: '7' }],
      totalElements: 1
    })
    expect(http.get).toHaveBeenCalledWith('/activities', { params: { page: 1 } })

    http.get.mockResolvedValueOnce({})
    await expect(activityApi.listActivities()).resolves.toMatchObject({ content: [] })
  })

  it('builds ordinary, sorted, and composite all-activity queries', async () => {
    http.get.mockResolvedValueOnce({ content: [activity] })
    await expect(activityApi.getAllActivities({ category: 'lecture' })).resolves.toHaveLength(1)
    expect(http.get).toHaveBeenLastCalledWith('/activities', {
      params: { page: 0, size: 200, category: 'lecture' }
    })

    http.get.mockResolvedValueOnce({ content: [] })
    await activityApi.getAllActivities({ sort: 'hot', matchWeight: 0.2 })
    expect(http.get).toHaveBeenLastCalledWith('/activities', {
      params: { page: 0, size: 200, sort: 'hot' }
    })

    http.get.mockResolvedValueOnce({ content: [] })
    await activityApi.getAllActivities({ sort: 'composite', matchWeight: 0.7 })
    expect(http.get).toHaveBeenLastCalledWith('/activities', {
      params: { page: 0, size: 200, sort: 'composite', matchWeight: 0.7 }
    })
  })

  it('covers recommended, detail, mine, create, update, and delete endpoints', async () => {
    http.get.mockResolvedValueOnce([activity])
    await expect(activityApi.getRecommended(3)).resolves.toEqual([{ ...activity, id: '7' }])
    expect(http.get).toHaveBeenLastCalledWith('/activities/recommended', { params: { limit: 3 } })

    http.get.mockResolvedValueOnce(activity)
    await expect(activityApi.getActivityById(7)).resolves.toMatchObject({ id: '7' })

    http.get.mockResolvedValueOnce([activity])
    await expect(activityApi.getMyActivities()).resolves.toEqual([{ ...activity, id: '7' }])

    http.post.mockResolvedValueOnce(activity)
    await expect(activityApi.createActivity({ title: 'Workshop' })).resolves.toMatchObject({ id: '7' })

    http.put.mockResolvedValueOnce(activity)
    await expect(activityApi.updateActivity(7, { title: 'Updated' })).resolves.toMatchObject({ id: '7' })

    http.delete.mockResolvedValueOnce({ deleted: true })
    await expect(activityApi.deleteActivity(7)).resolves.toEqual({ deleted: true })
  })
})

describe('registration, feedback, favorite, and check-in APIs', () => {
  it('normalizes and operates on registrations with and without filters', async () => {
    expect(registrationApi.normalizeRegistration(registration)).toMatchObject({ id: '8', activityId: '7' })

    http.post.mockResolvedValueOnce(registration)
    await expect(registrationApi.signup('7')).resolves.toMatchObject({ id: '8' })
    expect(http.post).toHaveBeenLastCalledWith('/registrations', { activityId: 7 })

    http.get.mockResolvedValueOnce([registration])
    await expect(registrationApi.getMyRegistrations()).resolves.toHaveLength(1)

    http.get.mockResolvedValueOnce([registration])
    await registrationApi.listRegistrations('7')
    expect(http.get).toHaveBeenLastCalledWith('/registrations', { params: { activityId: 7 } })

    http.get.mockResolvedValueOnce([])
    await registrationApi.listRegistrations()
    expect(http.get).toHaveBeenLastCalledWith('/registrations', { params: {} })

    http.put.mockResolvedValueOnce({ ...registration, status: 'approved' })
    await expect(registrationApi.reviewRegistration(8, true)).resolves.toMatchObject({ status: 'approved' })

    http.get.mockResolvedValueOnce({ status: 'approved' })
    await expect(registrationApi.getSignupStatus(7)).resolves.toEqual({ status: 'approved' })
  })

  it('normalizes and operates on feedbacks', async () => {
    const feedback = {
      id: 9,
      activityId: 7,
      userId: 'student-1',
      userName: 'Student',
      rating: 5,
      content: 'Great',
      createdAt: '2026-07-02',
      activityTitle: 'Workshop'
    }
    expect(feedbackApi.normalizeFeedback(feedback)).toMatchObject({ id: '9', activityId: '7' })

    http.post.mockResolvedValueOnce(feedback)
    await expect(feedbackApi.submitFeedback({ activityId: '7', rating: 5 })).resolves.toMatchObject({ id: '9' })
    expect(http.post).toHaveBeenLastCalledWith('/feedbacks', { activityId: 7, rating: 5 })

    http.get.mockResolvedValueOnce([feedback])
    await expect(feedbackApi.getMyFeedbacks()).resolves.toHaveLength(1)
    http.get.mockResolvedValueOnce([feedback])
    await expect(feedbackApi.listFeedbacksByActivity('7')).resolves.toHaveLength(1)
  })

  it('covers favorite endpoints', async () => {
    http.get.mockResolvedValueOnce([activity])
    await expect(favoriteApi.getMyFavorites()).resolves.toEqual([{ ...activity, id: '7' }])
    http.post.mockResolvedValueOnce({ favorited: true })
    await expect(favoriteApi.toggleFavorite(7)).resolves.toEqual({ favorited: true })
    http.get.mockResolvedValueOnce({ favorited: true })
    await expect(favoriteApi.getFavoriteStatus(7)).resolves.toEqual({ favorited: true })
  })

  it('normalizes every check-in method and handles empty stats records', async () => {
    const checkIn = { id: 10, activityId: 7, userId: 'student-1', method: 'qrcode' }
    http.post.mockResolvedValueOnce({ token: 't' })
    await checkInApi.createQrSession('7')
    expect(http.post).toHaveBeenLastCalledWith('/checkins/qrcode/session', { activityId: 7 })

    http.post.mockResolvedValueOnce(checkIn)
    await expect(checkInApi.checkInByQr({ activityId: '7', token: 't' })).resolves.toMatchObject({ id: '10' })
    http.post.mockResolvedValueOnce({ code: '123456' })
    await checkInApi.createPasswordSession('7')
    http.post.mockResolvedValueOnce({ ...checkIn, method: 'password' })
    await expect(checkInApi.checkInByPassword({ activityId: '7', code: '123456' })).resolves.toMatchObject({ activityId: '7' })
    http.post.mockResolvedValueOnce({ ...checkIn, method: 'location' })
    await expect(checkInApi.checkInByLocation({
      activityId: '7',
      latitude: 31.2,
      longitude: 121.4
    })).resolves.toMatchObject({ method: 'location' })

    http.get.mockResolvedValueOnce([checkIn, null])
    await expect(checkInApi.getMine()).resolves.toEqual([{ ...checkIn, id: '10', activityId: '7' }, null])
    http.get.mockResolvedValueOnce([checkIn])
    await expect(checkInApi.listByActivity('7')).resolves.toHaveLength(1)
    http.get.mockResolvedValueOnce({ activityId: 7 })
    await expect(checkInApi.getStats('7')).resolves.toEqual({ activityId: '7', records: [] })
    http.get.mockResolvedValueOnce({ activityId: 7, records: [checkIn] })
    await expect(checkInApi.getStats('7')).resolves.toMatchObject({ records: [{ id: '10' }] })
  })
})

describe('authentication, users, analytics, records, home, and clustering APIs', () => {
  it('logs in, registers, manages providers, and logs out', async () => {
    const data = { token: 'token', user: { id: 'student-1', name: 'Student' } }
    http.post.mockResolvedValueOnce(data)
    await expect(authApi.login('student-1', 'secret')).resolves.toEqual(data)
    expect(localStorage.getItem('campus-activity-auth-provider')).toBe('local')

    http.post.mockResolvedValueOnce(data)
    await expect(authApi.register({ id: 'student-1' })).resolves.toEqual(data)

    authApi.setAuthProvider('jaccount')
    expect(authApi.getAuthProvider()).toBe('jaccount')
    authApi.setAuthProvider(null)
    expect(authApi.getAuthProvider()).toBe('local')
    authApi.logout()
    expect(localStorage.getItem('campus-activity-token')).toBeNull()
  })

  it('gets and updates the current user', async () => {
    const user = { id: 'student-1', name: 'Student' }
    http.get.mockResolvedValueOnce(user)
    await expect(userApi.getMe()).resolves.toEqual(user)
    http.put.mockResolvedValueOnce({ ...user, name: 'Updated' })
    await expect(userApi.updateProfile({ name: 'Updated' })).resolves.toMatchObject({ name: 'Updated' })
  })

  it('covers simple aggregate endpoints', async () => {
    http.get.mockResolvedValueOnce({ viewCount: 2 })
    await analyticsApi.getActivityMetrics(7)
    http.get.mockResolvedValueOnce({ metrics: {} })
    await analyticsApi.getFullAnalysis(7)
    http.get.mockResolvedValueOnce({ mySignupCount: 1 })
    await homeApi.getHomeStats()
    http.post.mockResolvedValueOnce({ id: 1 })
    await recordApi.publishRecord(7, { summary: 'Done' })
    http.get.mockResolvedValueOnce({ summary: 'Done' })
    await recordApi.getRecord(7)
  })

  it('covers community clustering query and command endpoints', async () => {
    http.get.mockResolvedValue({})
    http.post.mockResolvedValue({})
    await clusteringApi.getLatestClustering()
    await clusteringApi.getMyCommunity()
    await clusteringApi.getClusteringRuns()
    await clusteringApi.getClusteringRun('run/1')
    await clusteringApi.submitClusteringRun()
    await clusteringApi.getCommunityMembers('community/1')

    expect(http.get).toHaveBeenCalledWith('/admin/community-clustering/runs', {
      params: { page: 0, size: 20 }
    })
    expect(http.get).toHaveBeenCalledWith('/admin/community-clustering/runs/run%2F1')
    expect(http.post).toHaveBeenCalledWith('/admin/community-clustering/runs', { clusterCount: 2 })
    expect(http.get).toHaveBeenCalledWith(
      '/admin/community-clustering/communities/community%2F1/members',
      { params: { page: 0, size: 20 } }
    )
  })
})
