import { createContext, useContext, useState, useCallback, useMemo, useEffect } from 'react'
import * as authApi from '../services/authApi'
import * as userApi from '../services/userApi'
import * as activityApi from '../services/activityApi'
import * as registrationApi from '../services/registrationApi'
import * as favoriteApi from '../services/favoriteApi'
import * as recordApi from '../services/recordApi'
import * as feedbackApi from '../services/feedbackApi'
import { getStoredUser, getToken } from '../services/http'
import { checkIn as mockCheckIn, getCheckIns } from '../services/mock/mockApi'

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

    getCheckIns().then(setCheckIns)
  }, [])

  const login = useCallback(async (userId, password) => {
    try {
      const data = await authApi.login(userId, password)
      setCurrentUser(data.user)
      return { success: true }
    } catch (err) {
      return { success: false, message: err.message }
    }
  }, [])

  const logout = useCallback(() => {
    authApi.logout()
    setCurrentUser(null)
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

  const checkIn = useCallback(async (activityId, method) => {
    if (!currentUser) return { success: false, message: '请先登录' }
    const result = await mockCheckIn(activityId, currentUser.id, method)
    if (result.success) {
      setCheckIns(await getCheckIns())
    }
    return result
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
    login, logout, register, updateProfile, createActivity, updateActivity,
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
