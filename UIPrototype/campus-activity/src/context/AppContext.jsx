import { createContext, useContext, useState, useCallback, useMemo } from 'react'
import {
  initialUsers,
  initialActivities,
  initialSignups,
  initialFavorites,
  initialFeedbacks,
  initialCheckIns
} from '../data/mockData'

const STORAGE_KEY = 'campus-activity-state'

function loadState() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) return JSON.parse(saved)
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

  const [currentUser, setCurrentUser] = useState(saved?.currentUser ?? null)
  const [users, setUsers] = useState(saved?.users ?? initialUsers)
  const [activities, setActivities] = useState(saved?.activities ?? initialActivities)
  const [signups, setSignups] = useState(saved?.signups ?? initialSignups)
  const [favorites, setFavorites] = useState(saved?.favorites ?? initialFavorites)
  const [feedbacks, setFeedbacks] = useState(saved?.feedbacks ?? initialFeedbacks)
  const [checkIns, setCheckIns] = useState(saved?.checkIns ?? initialCheckIns)

  const persist = useCallback((next) => {
    saveState({
      currentUser: next.currentUser ?? currentUser,
      users: next.users ?? users,
      activities: next.activities ?? activities,
      signups: next.signups ?? signups,
      favorites: next.favorites ?? favorites,
      feedbacks: next.feedbacks ?? feedbacks,
      checkIns: next.checkIns ?? checkIns
    })
  }, [currentUser, users, activities, signups, favorites, feedbacks, checkIns])

  const login = useCallback((userId, password) => {
    const user = users.find(u => u.id === userId && u.password === password)
    if (!user) return { success: false, message: '学号/工号或密码错误' }
    const { password: _, ...safeUser } = user
    setCurrentUser(safeUser)
    persist({ currentUser: safeUser })
    return { success: true }
  }, [users, persist])

  const logout = useCallback(() => {
    setCurrentUser(null)
    persist({ currentUser: null })
  }, [persist])

  const register = useCallback((data) => {
    if (users.some(u => u.id === data.id)) {
      return { success: false, message: '该学号/工号已注册' }
    }
    const newUser = {
      id: data.id,
      password: data.password,
      name: data.name,
      role: data.role || 'student',
      college: data.college,
      grade: data.grade,
      interests: data.interests || [],
      availableTime: data.availableTime || [],
      friends: []
    }
    const nextUsers = [...users, newUser]
    setUsers(nextUsers)
    const { password: _, ...safeUser } = newUser
    setCurrentUser(safeUser)
    persist({ users: nextUsers, currentUser: safeUser })
    return { success: true }
  }, [users, persist])

  const updateProfile = useCallback((updates) => {
    if (!currentUser) return
    const nextUsers = users.map(u =>
      u.id === currentUser.id ? { ...u, ...updates } : u
    )
    const updated = nextUsers.find(u => u.id === currentUser.id)
    const { password: _, ...safeUser } = updated
    setUsers(nextUsers)
    setCurrentUser(safeUser)
    persist({ users: nextUsers, currentUser: safeUser })
  }, [currentUser, users, persist])

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
    users,
    activities,
    signups,
    favorites,
    feedbacks,
    checkIns,
    login,
    logout,
    register,
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
    currentUser, users, activities, signups, favorites, feedbacks, checkIns,
    login, logout, register, updateProfile, createActivity, updateActivity,
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
