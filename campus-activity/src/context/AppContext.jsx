import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState
} from 'react'
import {
  initialUsers,
  initialActivities,
  initialSignups,
  initialFavorites,
  initialFeedbacks,
  initialCheckIns
} from '../data/mockData'
import { ApiError, subscribeAuthenticationInvalid } from '../api/http'
import { clearCsrfToken } from '../api/csrf'
import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest
} from '../api/auth'

const STORAGE_KEY = 'campus-activity-state'

function loadState() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      const safeState = {
        activities: parsed.activities,
        signups: parsed.signups,
        favorites: parsed.favorites,
        feedbacks: parsed.feedbacks,
        checkIns: parsed.checkIns
      }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(safeState))
      return safeState
    }
  } catch {
    /* ignore */
  }
  return null
}

function saveState(state) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
}

const AppContext = createContext(null)

export function AppProvider({ children }) {
  const saved = loadState()

  const [currentUser, setCurrentUser] = useState(null)
  const [authStatus, setAuthStatus] = useState('initializing')
  const [authError, setAuthError] = useState(null)
  const [users, setUsers] = useState(initialUsers)
  const [activities, setActivities] = useState(saved?.activities ?? initialActivities)
  const [signups, setSignups] = useState(saved?.signups ?? initialSignups)
  const [favorites, setFavorites] = useState(saved?.favorites ?? initialFavorites)
  const [feedbacks, setFeedbacks] = useState(saved?.feedbacks ?? initialFeedbacks)
  const [checkIns, setCheckIns] = useState(saved?.checkIns ?? initialCheckIns)

  const persist = useCallback((next) => {
    saveState({
      activities: next.activities ?? activities,
      signups: next.signups ?? signups,
      favorites: next.favorites ?? favorites,
      feedbacks: next.feedbacks ?? feedbacks,
      checkIns: next.checkIns ?? checkIns
    })
  }, [activities, signups, favorites, feedbacks, checkIns])

  const applyAuthenticatedUser = useCallback((user) => {
    setCurrentUser(user)
    setUsers(existing => {
      const withoutCurrent = existing.filter(item => item.id !== user.id)
      return [...withoutCurrent, user]
    })
    setAuthStatus('authenticated')
    setAuthError(null)
    return user
  }, [])

  const becomeAnonymous = useCallback(() => {
    setCurrentUser(null)
    setUsers(initialUsers)
    setAuthStatus('anonymous')
    setAuthError(null)
  }, [])

  const refreshAuth = useCallback(async ({ signal } = {}) => {
    setAuthStatus('initializing')
    setAuthError(null)
    try {
      const user = await getCurrentUser({ signal })
      if (!user) {
        throw new ApiError({
          status: 200,
          code: 'INVALID_RESPONSE',
          message: '服务器未返回有效用户信息'
        })
      }
      return applyAuthenticatedUser(user)
    } catch (error) {
      if (error?.name === 'AbortError') throw error
      if (error instanceof ApiError && error.code === 'AUTHENTICATION_REQUIRED') {
        becomeAnonymous()
        return null
      }
      setCurrentUser(null)
      setAuthStatus('error')
      setAuthError(error instanceof ApiError ? error.message : '认证状态加载失败')
      throw error
    }
  }, [applyAuthenticatedUser, becomeAnonymous])

  useEffect(() => {
    const controller = new AbortController()
    refreshAuth({ signal: controller.signal }).catch(error => {
      if (error?.name !== 'AbortError') {
        // The error state is exposed through context for an explicit retry.
      }
    })
    return () => controller.abort()
  }, [refreshAuth])

  useEffect(() => subscribeAuthenticationInvalid(() => {
    clearCsrfToken()
    becomeAnonymous()
  }), [becomeAnonymous])

  const login = useCallback(async (credentials) => {
    await loginRequest(credentials)
    const user = await getCurrentUser()
    if (!user) {
      throw new ApiError({
        status: 200,
        code: 'INVALID_RESPONSE',
        message: '登录后未能取得用户信息'
      })
    }
    return applyAuthenticatedUser(user)
  }, [applyAuthenticatedUser])

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } catch (error) {
      if (!(error instanceof ApiError && error.code === 'AUTHENTICATION_REQUIRED')) {
        throw error
      }
      clearCsrfToken()
    }
    becomeAnonymous()
  }, [becomeAnonymous])

  const register = useCallback(async (data) => {
    await registerRequest(data)
    becomeAnonymous()
  }, [becomeAnonymous])

  const updateProfile = useCallback((updates) => {
    if (!currentUser) return
    const safeUpdates = Object.fromEntries(
      ['name', 'college', 'grade', 'interests', 'availableTime']
        .filter(key => updates[key] !== undefined)
        .map(key => [key, updates[key]])
    )
    const nextUsers = users.map(u =>
      u.id === currentUser.id ? { ...u, ...safeUpdates } : u
    )
    const updated = nextUsers.find(u => u.id === currentUser.id)
    setUsers(nextUsers)
    setCurrentUser(updated)
  }, [currentUser, users])

  const createActivity = useCallback((activity) => {
    const id = String(Date.now())
    const newActivity = {
      ...activity,
      id,
      organizerId: currentUser.id,
      organizerName: currentUser.name,
      college: currentUser.college,
      signupCount: 0,
      favoriteCount: 0,
      status: 'published',
      checkInCode: `CK${Date.now().toString(36).toUpperCase().slice(-4)}`,
      record: null
    }
    const next = [...activities, newActivity]
    setActivities(next)
    persist({ activities: next })
    return id
  }, [currentUser, activities, persist])

  const updateActivity = useCallback((id, updates) => {
    const next = activities.map(a => a.id === id ? { ...a, ...updates } : a)
    setActivities(next)
    persist({ activities: next })
  }, [activities, persist])

  const signupActivity = useCallback((activityId) => {
    if (!currentUser) return { success: false, message: '请先登录' }
    const existing = signups.find(
      s => s.activityId === activityId && s.userId === currentUser.id
    )
    if (existing) return { success: false, message: '您已报名该活动' }
    const activity = activities.find(a => a.id === activityId)
    if (!activity) return { success: false, message: '活动不存在' }
    if (activity.signupCount >= activity.maxParticipants) {
      return { success: false, message: '报名人数已满' }
    }
    const newSignup = {
      id: `s${Date.now()}`,
      activityId,
      userId: currentUser.id,
      status: 'pending',
      createdAt: new Date().toISOString()
    }
    const nextSignups = [...signups, newSignup]
    const nextActivities = activities.map(a =>
      a.id === activityId ? { ...a, signupCount: a.signupCount + 1 } : a
    )
    setSignups(nextSignups)
    setActivities(nextActivities)
    persist({ signups: nextSignups, activities: nextActivities })
    return { success: true, message: '报名成功，等待组织者审核' }
  }, [currentUser, signups, activities, persist])

  const toggleFavorite = useCallback((activityId) => {
    if (!currentUser) return { success: false, message: '请先登录' }
    const exists = favorites.some(
      f => f.userId === currentUser.id && f.activityId === activityId
    )
    let nextFavorites
    let delta = 0
    if (exists) {
      nextFavorites = favorites.filter(
        f => !(f.userId === currentUser.id && f.activityId === activityId)
      )
      delta = -1
    } else {
      nextFavorites = [...favorites, { userId: currentUser.id, activityId }]
      delta = 1
    }
    const nextActivities = activities.map(a =>
      a.id === activityId
        ? { ...a, favoriteCount: Math.max(0, a.favoriteCount + delta) }
        : a
    )
    setFavorites(nextFavorites)
    setActivities(nextActivities)
    persist({ favorites: nextFavorites, activities: nextActivities })
    return { success: true, favorited: !exists }
  }, [currentUser, favorites, activities, persist])

  const reviewSignup = useCallback((signupId, approved) => {
    const nextSignups = signups.map(s =>
      s.id === signupId ? { ...s, status: approved ? 'approved' : 'rejected' } : s
    )
    setSignups(nextSignups)
    persist({ signups: nextSignups })
  }, [signups, persist])

  const checkIn = useCallback((activityId, method) => {
    if (!currentUser) return { success: false, message: '请先登录' }
    const signup = signups.find(
      s => s.activityId === activityId && s.userId === currentUser.id && s.status === 'approved'
    )
    if (!signup) return { success: false, message: '您尚未通过该活动的报名审核' }
    const existing = checkIns.find(
      c => c.activityId === activityId && c.userId === currentUser.id
    )
    if (existing) return { success: false, message: '您已签到' }
    const newCheckIn = {
      id: `c${Date.now()}`,
      activityId,
      userId: currentUser.id,
      method,
      time: new Date().toISOString()
    }
    const next = [...checkIns, newCheckIn]
    setCheckIns(next)
    persist({ checkIns: next })
    return { success: true, message: '签到成功' }
  }, [currentUser, signups, checkIns, persist])

  const submitFeedback = useCallback(({ activityId, rating, content }) => {
    if (!currentUser) return { success: false, message: '请先登录' }
    const newFeedback = {
      id: `f${Date.now()}`,
      activityId,
      userId: currentUser.id,
      userName: currentUser.name,
      rating,
      content,
      createdAt: new Date().toISOString()
    }
    const next = [...feedbacks, newFeedback]
    setFeedbacks(next)
    persist({ feedbacks: next })
    return { success: true, message: '评价提交成功' }
  }, [currentUser, feedbacks, persist])

  const publishRecord = useCallback((activityId, record) => {
    const next = activities.map(a =>
      a.id === activityId
        ? {
            ...a,
            status: 'ended',
            record: { ...record, publishedAt: new Date().toISOString() }
          }
        : a
    )
    setActivities(next)
    persist({ activities: next })
  }, [activities, persist])

  const getSignupStatus = useCallback((activityId) => {
    if (!currentUser) return null
    return signups.find(
      s => s.activityId === activityId && s.userId === currentUser.id
    )?.status ?? null
  }, [currentUser, signups])

  const isFavorited = useCallback((activityId) => {
    if (!currentUser) return false
    return favorites.some(
      f => f.userId === currentUser.id && f.activityId === activityId
    )
  }, [currentUser, favorites])

  const getRecommendedActivities = useCallback(() => {
    if (!currentUser) return activities.filter(a => a.status === 'published').slice(0, 4)
    const userInterests = currentUser.interests || []
    const userSignupIds = signups
      .filter(s => s.userId === currentUser.id)
      .map(s => s.activityId)
    const friendIds = currentUser.friends || []

    return activities
      .filter(a => a.status === 'published')
      .map(activity => {
        let score = 0
        const tagMatch = (activity.tags || []).filter(t => userInterests.includes(t)).length
        score += tagMatch * 30
        score += Math.min(activity.favoriteCount, 50)
        score += Math.min(activity.signupCount, 30)
        if (friendIds.some(fid =>
          signups.some(s => s.activityId === activity.id && s.userId === fid && s.status === 'approved')
        )) score += 20
        if (userSignupIds.includes(activity.id)) score -= 100
        return { ...activity, recommendScore: score }
      })
      .sort((a, b) => b.recommendScore - a.recommendScore)
      .slice(0, 6)
  }, [currentUser, activities, signups])

  const value = useMemo(() => ({
    currentUser,
    authStatus,
    authError,
    users,
    activities,
    signups,
    favorites,
    feedbacks,
    checkIns,
    login,
    logout,
    register,
    refreshAuth,
    updateProfile,
    createActivity,
    updateActivity,
    signupActivity,
    toggleFavorite,
    reviewSignup,
    checkIn,
    submitFeedback,
    publishRecord,
    getSignupStatus,
    isFavorited,
    getRecommendedActivities
  }), [
    currentUser, authStatus, authError, users, activities, signups, favorites,
    feedbacks, checkIns, login, logout, register, refreshAuth, updateProfile,
    createActivity, updateActivity,
    signupActivity, toggleFavorite, reviewSignup, checkIn, submitFeedback,
    publishRecord, getSignupStatus, isFavorited, getRecommendedActivities
  ])

  return (
    <AppContext.Provider value={value}>
      {children}
    </AppContext.Provider>
  )
}

export function useApp() {
  const ctx = useContext(AppContext)
  if (!ctx) throw new Error('useApp must be used within AppProvider')
  return ctx
}
