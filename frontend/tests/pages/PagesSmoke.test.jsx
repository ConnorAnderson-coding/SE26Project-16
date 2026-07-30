import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../helpers/renderWithApp'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  setSearchParams: vi.fn(),
  app: {
    currentUser: {
      id: 'user-1',
      name: 'Test User',
      role: 'admin',
      college: 'Software',
      grade: '2024',
      interests: ['AI'],
      availableTime: ['weekend']
    },
    checkIns: [],
    initializing: false,
    login: vi.fn(),
    register: vi.fn(),
    completeJAccountLogin: vi.fn(),
    signupActivity: vi.fn(),
    toggleFavorite: vi.fn(),
    updateProfile: vi.fn(),
    createActivity: vi.fn(),
    updateActivity: vi.fn(),
    reviewSignup: vi.fn(),
    checkIn: vi.fn(),
    submitFeedback: vi.fn(),
    publishRecord: vi.fn(),
    getRecommendedActivities: vi.fn(),
    logout: vi.fn(),
    isJAccountSession: vi.fn()
  }
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mocks.navigate,
    useParams: () => ({ id: 'activity-1' }),
    useSearchParams: () => [new URLSearchParams('activityId=activity-1'), mocks.setSearchParams]
  }
})

vi.mock('../../src/context/AppContext', () => ({
  useApp: () => mocks.app
}))

vi.mock('../../src/layouts/MainLayout', () => ({
  default: ({ children, title }) => <main><h1>{title}</h1>{children}</main>
}))

vi.mock('../../src/components/AuthGuard', () => ({
  default: ({ children }) => <>{children}</>
}))

vi.mock('../../src/components/AdminGuard', () => ({
  default: ({ children }) => <>{children}</>
}))

vi.mock('../../src/components/MapLocationPicker', () => ({
  default: ({ onChange }) => (
    <button type="button" onClick={() => onChange({ latitude: 31.2, longitude: 121.4 })}>
      choose map location
    </button>
  )
}))

vi.mock('../../src/services/activityApi', () => ({
  getAllActivities: vi.fn(),
  getActivityById: vi.fn(),
  getMyActivities: vi.fn()
}))

vi.mock('../../src/services/registrationApi', () => ({
  getMyRegistrations: vi.fn(),
  listRegistrations: vi.fn(),
  getSignupStatus: vi.fn()
}))

vi.mock('../../src/services/favoriteApi', () => ({
  getMyFavorites: vi.fn(),
  getFavoriteStatus: vi.fn()
}))

vi.mock('../../src/services/feedbackApi', () => ({
  getMyFeedbacks: vi.fn(),
  listFeedbacksByActivity: vi.fn()
}))

vi.mock('../../src/services/homeApi', () => ({
  getHomeStats: vi.fn()
}))

vi.mock('../../src/services/analyticsApi', () => ({
  getFullAnalysis: vi.fn()
}))

vi.mock('../../src/services/checkInApi', () => ({
  createQrSession: vi.fn(),
  createPasswordSession: vi.fn(),
  getMine: vi.fn(),
  getStats: vi.fn()
}))

vi.mock('../../src/services/communityClusteringApi', () => ({
  getLatestClustering: vi.fn(),
  getMyCommunity: vi.fn(),
  getClusteringRuns: vi.fn(),
  getCommunityMembers: vi.fn(),
  submitClusteringRun: vi.fn()
}))

vi.mock('../../src/services/mock/mockApi', () => ({
  getUsers: vi.fn()
}))

vi.mock('../../src/services/authApi', () => ({
  startJAccountLogin: vi.fn()
}))

import * as activityApi from '../../src/services/activityApi'
import * as registrationApi from '../../src/services/registrationApi'
import * as favoriteApi from '../../src/services/favoriteApi'
import * as feedbackApi from '../../src/services/feedbackApi'
import * as homeApi from '../../src/services/homeApi'
import * as analyticsApi from '../../src/services/analyticsApi'
import * as checkInApi from '../../src/services/checkInApi'
import * as clusteringApi from '../../src/services/communityClusteringApi'
import * as mockApi from '../../src/services/mock/mockApi'
import * as authApi from '../../src/services/authApi'

import ActivityDetail from '../../src/pages/ActivityDetail'
import ActivityList from '../../src/pages/ActivityList'
import AdminCommunityClustering from '../../src/pages/AdminCommunityClustering'
import AdminDashboard from '../../src/pages/AdminDashboard'
import CheckIn from '../../src/pages/CheckIn'
import CommunityClusters from '../../src/pages/CommunityClusters'
import CreateActivity from '../../src/pages/CreateActivity'
import EditActivity from '../../src/pages/EditActivity'
import Feedback from '../../src/pages/Feedback'
import Home from '../../src/pages/Home'
import Login from '../../src/pages/Login'
import MyActivities from '../../src/pages/MyActivities'
import MyFavorites from '../../src/pages/MyFavorites'
import OAuthCallback from '../../src/pages/OAuthCallback'
import OrganizerAnalytics from '../../src/pages/OrganizerAnalytics'
import OrganizerDashboard from '../../src/pages/OrganizerDashboard'
import SignupManagement from '../../src/pages/SignupManagement'
import UserProfile from '../../src/pages/UserProfile'

const activity = {
  id: 'activity-1',
  title: 'Testing Workshop',
  description: 'A useful workshop for testing.',
  category: 'lecture',
  location: 'Room 101',
  latitude: 31.2,
  longitude: 121.4,
  organizerId: 'user-1',
  organizerName: 'Test User',
  startTime: '2026-06-01T09:00:00',
  endTime: '2026-06-01T11:00:00',
  signupDeadline: '2026-05-31T18:00:00',
  status: 'ended',
  maxParticipants: 100,
  signupCount: 20,
  favoriteCount: 5,
  hotnessScore: 12.5,
  tags: ['AI'],
  coverImage: 'https://example.test/activity.png',
  checkInMethods: ['qrcode', 'location', 'password']
}

const publishedActivity = {
  ...activity,
  id: 'activity-2',
  title: 'Published Workshop',
  status: 'published',
  startTime: '2027-06-01T09:00:00',
  endTime: '2027-06-01T11:00:00'
}

const registration = {
  id: 'signup-1',
  activityId: activity.id,
  activityTitle: activity.title,
  userId: 'student-1',
  userName: 'Student One',
  college: 'Software',
  location: activity.location,
  startTime: activity.startTime,
  status: 'approved',
  createdAt: '2026-05-01T10:00:00'
}

const clustering = {
  run: { runId: 'run-1', version: 'v1', clusterCount: 2, status: 'SUCCESS' },
  communities: [{
    communityId: 'community-1',
    name: 'AI Community',
    color: '#1677ff',
    memberCount: 2,
    description: 'People interested in AI',
    topInterests: ['AI'],
    points: [
      { pointId: 'point-1', x: 30, y: 40, currentUser: true },
      { pointId: 'point-2', x: 60, y: 70, currentUser: false }
    ]
  }]
}

function renderPage(ui, route = '/home') {
  return renderWithRouter(ui, { route })
}

beforeEach(() => {
  vi.clearAllMocks()
  window.history.replaceState({}, '', '/')

  mocks.app.currentUser = {
    id: 'user-1',
    name: 'Test User',
    role: 'admin',
    college: 'Software',
    grade: '2024',
    interests: ['AI'],
    availableTime: ['weekend']
  }
  mocks.app.checkIns = []
  mocks.app.login.mockResolvedValue({ success: true })
  mocks.app.register.mockResolvedValue({ success: true })
  mocks.app.completeJAccountLogin.mockResolvedValue(mocks.app.currentUser)
  mocks.app.signupActivity.mockResolvedValue({ success: true, message: 'signed up' })
  mocks.app.toggleFavorite.mockResolvedValue({ success: true, favorited: true })
  mocks.app.updateProfile.mockResolvedValue(mocks.app.currentUser)
  mocks.app.createActivity.mockResolvedValue(activity.id)
  mocks.app.updateActivity.mockResolvedValue(activity)
  mocks.app.reviewSignup.mockResolvedValue()
  mocks.app.checkIn.mockResolvedValue({ success: true, message: 'checked in' })
  mocks.app.submitFeedback.mockResolvedValue({ success: true, message: 'submitted' })
  mocks.app.publishRecord.mockResolvedValue()
  mocks.app.getRecommendedActivities.mockResolvedValue([publishedActivity])
  mocks.app.isJAccountSession.mockReturnValue(false)

  activityApi.getAllActivities.mockResolvedValue([activity, publishedActivity])
  activityApi.getActivityById.mockResolvedValue(activity)
  activityApi.getMyActivities.mockResolvedValue([activity, publishedActivity])
  registrationApi.getMyRegistrations.mockResolvedValue([registration])
  registrationApi.listRegistrations.mockResolvedValue([{ ...registration, status: 'pending' }])
  registrationApi.getSignupStatus.mockResolvedValue({ status: 'approved' })
  favoriteApi.getMyFavorites.mockResolvedValue([activity])
  favoriteApi.getFavoriteStatus.mockResolvedValue({ favorited: false })
  feedbackApi.getMyFeedbacks.mockResolvedValue([{
    id: 'feedback-1',
    activityId: activity.id,
    activityTitle: activity.title,
    rating: 5,
    content: 'Great',
    createdAt: '2026-06-02T10:00:00'
  }])
  feedbackApi.listFeedbacksByActivity.mockResolvedValue([{
    id: 'feedback-1',
    userName: 'Student One',
    rating: 5,
    content: 'Great',
    createdAt: '2026-06-02T10:00:00'
  }])
  homeApi.getHomeStats.mockResolvedValue({
    mySignupCount: 1,
    approvedCount: 1,
    myFavoriteCount: 1
  })
  checkInApi.getMine.mockResolvedValue([{ activityId: activity.id }])
  checkInApi.createQrSession.mockResolvedValue({ token: 'qr-token', content: 'CHECKIN:activity-1:qr-token' })
  checkInApi.createPasswordSession.mockResolvedValue({ code: '123456' })
  checkInApi.getStats.mockResolvedValue({ total: 1, records: [] })
  clusteringApi.getLatestClustering.mockResolvedValue(clustering)
  clusteringApi.getMyCommunity.mockResolvedValue({
    membership: { communityId: 'community-1', communityName: 'AI Community' }
  })
  clusteringApi.getClusteringRuns.mockResolvedValue({
    items: [{
      runId: 'run-1',
      version: 'v1',
      clusterCount: 2,
      sampleCount: 10,
      status: 'SUCCESS',
      createdAt: '2026-06-01T10:00:00'
    }],
    page: 0,
    size: 10,
    totalElements: 1
  })
  clusteringApi.getCommunityMembers.mockResolvedValue({
    community: { name: 'AI Community' },
    items: [{
      pointId: 'point-1',
      name: 'Student One',
      userId: 'student-1',
      college: 'Software',
      grade: '2024',
      distanceToCenter: 0.123
    }]
  })
  clusteringApi.submitClusteringRun.mockResolvedValue({ runId: 'run-2' })
  mockApi.getUsers.mockResolvedValue([mocks.app.currentUser])
  analyticsApi.getFullAnalysis.mockResolvedValue({
    metrics: {
      viewCount: 100,
      signupCount: 20,
      favoriteCount: 5,
      checkInCount: 18,
      feedbackCount: 4,
      averageRating: 4.5,
      signupRate: 0.2,
      checkInRate: 0.9,
      ratingDistribution: { 4: 1, 5: 3 },
      signupTrend: {
        '2026-05-01': 2,
        '2026-05-02': 3,
        '2026-05-03': 5
      },
      snapshotAt: '2026-06-02T10:00:00'
    },
    suggestions: [{
      category: 'content',
      priority: 'high',
      content: 'Keep the practical examples.'
    }],
    suggestionSource: 'llm',
    generatedAt: '2026-06-02T11:00:00'
  })
})

describe('all page components', () => {
  it('renders Home data and retries a failed request', async () => {
    const firstRender = renderPage(<Home />)
    expect(await screen.findByText(publishedActivity.title)).toBeInTheDocument()
    expect(homeApi.getHomeStats).toHaveBeenCalled()
    firstRender.unmount()

    mocks.app.getRecommendedActivities.mockRejectedValueOnce(new Error('home failed'))
    renderPage(<Home />)
    const retry = await screen.findByRole('button', { name: /重\s*试/ })
    mocks.app.getRecommendedActivities.mockResolvedValueOnce([])
    await userEvent.click(retry)
    await waitFor(() => expect(mocks.app.getRecommendedActivities).toHaveBeenCalledTimes(3))
  })

  it('renders ActivityList and applies text filtering', async () => {
    renderPage(<ActivityList />, '/activities')
    expect(await screen.findByText(activity.title)).toBeInTheDocument()
    const search = screen.getByRole('searchbox')
    fireEvent.change(search, { target: { value: 'Published' } })
    await waitFor(() => expect(activityApi.getAllActivities).toHaveBeenCalled())
  })

  it('renders ActivityDetail and performs signup and favorite actions', async () => {
    renderPage(<ActivityDetail />, '/activity/activity-1')
    expect(await screen.findByText(activity.title)).toBeInTheDocument()
    const buttons = screen.getAllByRole('button')
    const signupButton = buttons.find(button => button.textContent.includes('报名'))
    const favoriteButton = buttons.find(button => button.textContent.includes('收藏'))
    if (signupButton) await userEvent.click(signupButton)
    if (favoriteButton) await userEvent.click(favoriteButton)
    await waitFor(() => expect(activityApi.getActivityById).toHaveBeenCalled())
  })

  it('renders a published ActivityDetail, signs up, favorites, and navigates to check-in', async () => {
    activityApi.getActivityById.mockResolvedValue(publishedActivity)
    registrationApi.getSignupStatus
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({ status: 'approved' })
    favoriteApi.getFavoriteStatus.mockResolvedValue({ favorited: false })
    renderPage(<ActivityDetail />, '/activity/activity-2')
    expect(await screen.findByText(publishedActivity.title)).toBeInTheDocument()
    const signup = screen.getAllByRole('button').find(button => button.textContent.includes('立即报名'))
    expect(signup).toBeTruthy()
    await userEvent.click(signup)
    await waitFor(() => expect(mocks.app.signupActivity).toHaveBeenCalledWith('activity-1'))
    const favorite = screen.getAllByRole('button').find(button => button.textContent.includes('收藏活动'))
    await userEvent.click(favorite)
    expect(mocks.app.toggleFavorite).toHaveBeenCalled()
  })

  it('shows ActivityDetail load errors and retries', async () => {
    activityApi.getActivityById.mockRejectedValueOnce(new Error('detail failed'))
    renderPage(<ActivityDetail />, '/activity/activity-1')
    expect(await screen.findByText('detail failed')).toBeInTheDocument()
    const retry = screen.getByRole('button', { name: /重\s*试/ })
    await userEvent.click(retry)
    expect(await screen.findByText(activity.title)).toBeInTheDocument()
  })

  it('renders CreateActivity and map interaction', async () => {
    renderPage(<CreateActivity />, '/create')
    expect(screen.getByText('choose map location')).toBeInTheDocument()
    await userEvent.click(screen.getByText('choose map location'))
    expect(screen.getByRole('button', { name: /发布|提交|创建/ })).toBeInTheDocument()
  })

  it('loads EditActivity into its form', async () => {
    renderPage(<EditActivity />, '/edit/activity-1')
    expect(await screen.findByText('choose map location')).toBeInTheDocument()
    expect(activityApi.getActivityById).toHaveBeenCalledWith('activity-1')
  })

  it('renders MyActivities registrations', async () => {
    renderPage(<MyActivities />, '/my')
    expect(await screen.findByText(activity.title)).toBeInTheDocument()
  })

  it('renders MyFavorites cards', async () => {
    renderPage(<MyFavorites />, '/favorites')
    expect(await screen.findByText(activity.title)).toBeInTheDocument()
  })

  it('renders UserProfile and enters edit mode', async () => {
    renderPage(<UserProfile />, '/profile')
    expect(screen.getByText('Test User')).toBeInTheDocument()
    const edit = screen.getAllByRole('button').find(button => button.textContent.includes('编辑'))
    expect(edit).toBeTruthy()
    await userEvent.click(edit)
  })

  it('renders OrganizerDashboard activities', async () => {
    renderPage(<OrganizerDashboard />, '/organizer')
    expect(await screen.findByText(activity.title)).toBeInTheDocument()
    const buttons = screen.getAllByRole('button')
    for (const text of ['编辑', '审核报名', '签到管理', '创建活动']) {
      const button = buttons.find(item => item.textContent.includes(text))
      if (button) await userEvent.click(button)
    }
    expect(mocks.navigate).toHaveBeenCalled()
  }, 10000)

  it('renders SignupManagement and reviews a pending signup', async () => {
    renderPage(<SignupManagement />, '/signup-management?activityId=activity-1')
    expect(await screen.findByText('Student One')).toBeInTheDocument()
    const approve = screen.getByRole('button', { name: /通\s*过/ })
    await userEvent.click(approve)
    await waitFor(() => expect(mocks.app.reviewSignup).toHaveBeenCalledWith('signup-1', true))
    const reject = screen.getByRole('button', { name: /拒\s*绝/ })
    await userEvent.click(reject)
    await waitFor(() => expect(mocks.app.reviewSignup).toHaveBeenCalledWith('signup-1', false))
  })

  it('renders CheckIn and creates organizer sessions', async () => {
    renderPage(<CheckIn />, '/checkin?activityId=activity-1')
    await waitFor(() => expect(checkInApi.createQrSession).toHaveBeenCalledWith('activity-1'))
    expect(checkInApi.createPasswordSession).toHaveBeenCalledWith('activity-1')
    const refresh = screen.getAllByRole('button').find(button => button.textContent.includes('刷新'))
    if (refresh) await userEvent.click(refresh)
    await waitFor(() => expect(checkInApi.getStats).toHaveBeenCalled())
  })

  it('renders Feedback history and available registrations', async () => {
    renderPage(<Feedback />, '/feedback')
    expect(await screen.findAllByText(activity.title)).not.toHaveLength(0)
    expect(feedbackApi.getMyFeedbacks).toHaveBeenCalled()
  })

  it('renders OrganizerAnalytics charts and suggestions', async () => {
    renderPage(<OrganizerAnalytics />, '/organizer-analytics')
    await waitFor(() => expect(analyticsApi.getFullAnalysis).toHaveBeenCalledWith(activity.id))
    expect(screen.getByText('Keep the practical examples.')).toBeInTheDocument()
  })

  it('renders OrganizerAnalytics failed and empty suggestion states', async () => {
    analyticsApi.getFullAnalysis.mockResolvedValueOnce({
      metrics: {
        viewCount: 1,
        signupCount: 0,
        favoriteCount: 0,
        checkInCount: 0,
        feedbackCount: 0,
        averageRating: 0,
        ratingDistribution: null,
        signupTrend: {}
      },
      suggestions: [],
      suggestionSource: 'failed',
      failureReason: 'model unavailable'
    })
    const failed = renderPage(<OrganizerAnalytics />, '/organizer-analytics')
    expect(await screen.findByText(/model unavailable/)).toBeInTheDocument()
    failed.unmount()

    analyticsApi.getFullAnalysis.mockResolvedValueOnce({
      metrics: {
        viewCount: 1,
        signupCount: 1,
        favoriteCount: 1,
        checkInCount: 1,
        feedbackCount: 1,
        averageRating: 5,
        ratingDistribution: { 5: 1 },
        signupTrend: { '2026-01-01': 1 }
      },
      suggestions: [],
      suggestionSource: 'none'
    })
    renderPage(<OrganizerAnalytics />, '/organizer-analytics')
    await waitFor(() => expect(analyticsApi.getFullAnalysis).toHaveBeenCalled())
  })

  it('renders OrganizerAnalytics API errors and retries', async () => {
    analyticsApi.getFullAnalysis.mockRejectedValueOnce(new Error('analysis failed'))
    renderPage(<OrganizerAnalytics />, '/organizer-analytics')
    expect(await screen.findByText('analysis failed')).toBeInTheDocument()
    analyticsApi.getFullAnalysis.mockResolvedValueOnce({
      metrics: null,
      suggestions: [],
      suggestionSource: 'none'
    })
    await userEvent.click(screen.getByRole('button', { name: /重\s*试/ }))
    await waitFor(() => expect(analyticsApi.getFullAnalysis).toHaveBeenCalledTimes(2))
  })

  it('renders CommunityClusters membership and points', async () => {
    renderPage(<CommunityClusters />, '/community')
    expect(await screen.findAllByText(/AI Community/)).not.toHaveLength(0)
  })

  it('renders AdminDashboard aggregates', async () => {
    renderPage(<AdminDashboard />, '/admin')
    expect(await screen.findByText(activity.title)).toBeInTheDocument()
    expect(mockApi.getUsers).toHaveBeenCalled()
  })

  it('renders AdminCommunityClustering history', async () => {
    renderPage(<AdminCommunityClustering />, '/admin/community-clustering')
    expect(await screen.findByText('v1')).toBeInTheDocument()
    expect(clusteringApi.getClusteringRuns).toHaveBeenCalledWith(0, 10)
    await userEvent.click(screen.getByRole('button', { name: /AI Community/ }))
    expect(await screen.findByText('Student One')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /新建聚类/ }))
    const ok = await screen.findByRole('button', { name: /确\s*定|OK/i })
    await userEvent.click(ok)
    await waitFor(() => expect(clusteringApi.submitClusteringRun).toHaveBeenCalledWith(2))
  })

  it('renders Login and invokes jAccount entry', async () => {
    mocks.app.currentUser = null
    renderPage(<Login />, '/')
    const jaccount = screen.getAllByRole('button').find(button => button.textContent.includes('jAccount'))
    expect(jaccount).toBeTruthy()
    await userEvent.click(jaccount)
    expect(authApi.startJAccountLogin).toHaveBeenCalled()
  })

  it('handles a successful OAuth callback', async () => {
    window.history.replaceState({}, '', '/oauth/callback#token=test-token')
    renderPage(<OAuthCallback />, '/oauth/callback#token=test-token')
    await waitFor(() => expect(mocks.app.completeJAccountLogin).toHaveBeenCalledWith('test-token'))
    expect(mocks.navigate).toHaveBeenCalledWith('/home', { replace: true })
  })

  it('handles OAuth callback error, missing token, and rejected login', async () => {
    window.history.replaceState({}, '', '/oauth/callback#error=denied')
    const denied = renderPage(<OAuthCallback />, '/oauth/callback#error=denied')
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/', { replace: true }))
    denied.unmount()

    mocks.navigate.mockClear()
    window.history.replaceState({}, '', '/oauth/callback')
    const missing = renderPage(<OAuthCallback />, '/oauth/callback')
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/', { replace: true }))
    missing.unmount()

    mocks.navigate.mockClear()
    mocks.app.completeJAccountLogin.mockRejectedValueOnce(new Error('remote failed'))
    window.history.replaceState({}, '', '/oauth/callback#token=bad')
    renderPage(<OAuthCallback />, '/oauth/callback#token=bad')
    await waitFor(() => expect(mocks.app.completeJAccountLogin).toHaveBeenCalledWith('bad'))
    expect(mocks.navigate).toHaveBeenCalledWith('/', { replace: true })
  })
})
