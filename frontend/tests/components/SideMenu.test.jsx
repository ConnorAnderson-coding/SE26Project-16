import { describe, it, expect, beforeEach, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import SideMenu from '../../src/components/SideMenu'
import { renderWithApp } from '../helpers/renderWithApp'
import { setStoredUser, setToken } from '../../src/services/http'

vi.mock('../../src/services/userApi', () => ({
  getMe: vi.fn()
}))

import * as userApi from '../../src/services/userApi'

function seedSession(currentUser) {
  setToken('token')
  setStoredUser(currentUser)
  userApi.getMe.mockResolvedValue(currentUser)
}

describe('SideMenu 组件', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('所有登录用户应看到组织者菜单', async () => {
    seedSession({
      id: 'admin001',
      name: '系统管理员',
      role: 'admin',
      college: '软件学院'
    })

    renderWithApp(<SideMenu />, { route: '/home' })

    await waitFor(() => {
      expect(screen.getByText('发布活动')).toBeInTheDocument()
      expect(screen.getByText('活动管理')).toBeInTheDocument()
      expect(screen.getByText('报名审核')).toBeInTheDocument()
      expect(screen.getByText('活动数据分析')).toBeInTheDocument()
    })
  })

  it('管理员应看到管理后台入口', async () => {
    seedSession({
      id: 'admin001',
      name: '系统管理员',
      role: 'admin',
      college: '软件学院'
    })

    renderWithApp(<SideMenu />, { route: '/home' })

    await waitFor(() => {
      expect(screen.getByText('管理后台')).toBeInTheDocument()
    })
  })

  it('普通学生不应看到管理后台', async () => {
    seedSession({
      id: '524030910001',
      name: '张三',
      role: 'student',
      college: '软件学院'
    })

    renderWithApp(<SideMenu />, { route: '/home' })

    await waitFor(() => {
      expect(screen.getByText('发布活动')).toBeInTheDocument()
      expect(screen.queryByText('管理后台')).not.toBeInTheDocument()
    })
  })
})
