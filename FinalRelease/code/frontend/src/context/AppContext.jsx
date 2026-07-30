import { createContext, useContext, useState, useCallback, useMemo, useEffect } from 'react'
import * as authApi from '../services/authApi'
import * as userApi from '../services/userApi'
import * as activityApi from '../services/activityApi'
import * as registrationApi from '../services/registrationApi'
import * as favoriteApi from '../services/favoriteApi'
import * as recordApi from '../services/recordApi'
import * as feedbackApi from '../services/feedbackApi'
import * as checkInApi from '../services/checkInApi'
import { getStoredUser, getToken, setToken } from '../services/http'

const AppContext = createContext(null)

export function AppProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(getStoredUser())
  const [initializing, setInitializing] = useState(!!getToken())
  const [checkIns, setCheckIns] = useState([])

  useEffect(() => {
    if (!getToken()) {
      setInitializing(false)
      return
    }
    userApi.getMe()
      .then(setCurrentUser)
      .catch(() => {
        authApi.logout()
        setCurrentUser(null)
      })
      .finally(() => setInitializing(false))

    checkInApi.getMine().then(setCheckIns).catch(() => setCheckIns([]))
  }, [])

  const login = useCallback(async (userId, password) => {
    try {
      const data = await authApi.login(userId, password)
      setCurrentUser(data.user)
      checkInApi.getMine().then(setCheckIns).catch(() => setCheckIns([]))
      return { success: true }
    } catch (err) {
      return { success: false, message: err.message }
    }
  }, [])

  const logout = useCallback(() => {
    authApi.logout()
    setCurrentUser(null)
    setCheckIns([])
  }, [])

  const completeJAccountLogin = useCallback(async (token) => {
    setToken(token)
    authApi.setAuthProvider('jaccount')
    const user = await userApi.getMe()
    setCurrentUser(user)
    checkInApi.getMine().then(setCheckIns).catch(() => setCheckIns([]))
    return user
  }, [])

  const isJAccountSession = useCallback(() => {
    return authApi.getAuthProvider() === 'jaccount'
  }, [])

  const register = useCallback(async (data) => {
    try {
      const result = await authApi.register(data)
      setCurrentUser(result.user)
      return { success: true }
    } catch (err) {
      return { success: false, message: err.message }
    }
  }, [])

  const updateProfile = useCallback(async (updates) => {
    const user = await userApi.updateProfile(updates)
    setCurrentUser(user)
    return user
  }, [])

  const createActivity = useCallback(async (activity) => {
    const created = await activityApi.createActivity(activity)
    return created.id
  }, [])

  const updateActivity = useCallback(async (id, updates) => {
    return activityApi.updateActivity(id, updates)
  }, [])

  const signupActivity = useCallback(async (activityId) => {
    if (!currentUser) return { success: false, message: '请先登录' }
    try {
      await registrationApi.signup(activityId)
      return { success: true, message: '报名成功，等待组织者审核' }
    } catch (err) {
      return { success: false, message: err.message }
    }
  }, [currentUser])

  const toggleFavorite = useCallback(async (activityId) => {
    if (!currentUser) return { success: false, message: '请先登录' }
    try {
      const result = await favoriteApi.toggleFavorite(activityId)
      return { success: true, favorited: result.favorited }
    } catch (err) {
      return { success: false, message: err.message }
    }
  }, [currentUser])

  const reviewSignup = useCallback(async (signupId, approved) => {
    await registrationApi.reviewRegistration(signupId, approved)
  }, [])

  const checkIn = useCallback(async (activityId, method, payload = {}) => {
    if (!currentUser) return { success: false, message: '请先登录' }
    try {
      if (method === 'qrcode') {
        await checkInApi.checkInByQr({ activityId, token: payload.token })
      } else if (method === 'location') {
        await checkInApi.checkInByLocation({
          activityId,
          latitude: payload.latitude,
          longitude: payload.longitude
        })
      } else if (method === 'password') {
        await checkInApi.checkInByPassword({ activityId, code: payload.code })
      } else {
        return { success: false, message: '不支持的签到方式' }
      }
      setCheckIns(await checkInApi.getMine())
      return { success: true, message: '签到成功' }
    } catch (err) {
      return { success: false, message: err.message }
    }
  }, [currentUser])

  const submitFeedback = useCallback(async ({ activityId, rating, content }) => {
    if (!currentUser) return { success: false, message: '请先登录' }
    try {
      await feedbackApi.submitFeedback({ activityId, rating, content })
      return { success: true, message: '评价提交成功' }
    } catch (err) {
      return { success: false, message: err.message }
    }
  }, [currentUser])

  const publishRecord = useCallback(async (activityId, record) => {
    await recordApi.publishRecord(activityId, record)
  }, [])

  const getRecommendedActivities = useCallback(async () => {
    return activityApi.getRecommended()
  }, [])

  const value = useMemo(() => ({
    currentUser,
    initializing,
    checkIns,
    login,
    logout,
    completeJAccountLogin,
    isJAccountSession,
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
    getRecommendedActivities
  }), [
    currentUser, initializing, checkIns,
    login, logout, completeJAccountLogin, isJAccountSession, register, updateProfile, createActivity, updateActivity,
    signupActivity, toggleFavorite, reviewSignup, checkIn, submitFeedback,
    publishRecord, getRecommendedActivities
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
