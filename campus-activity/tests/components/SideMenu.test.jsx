import { describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import SideMenu from '../../src/components/SideMenu'
import { renderWithApp } from '../helpers/renderWithApp'

function seedSession(currentUser) {
  localStorage.setItem(
    'campus-activity-state',
    JSON.stringify({
      currentUser,
      users: [],
      activities: [],
      signups: [],
      favorites: [],
      feedbacks: [],
      checkIns: []
    })
  )
}

describe('SideMenu 组件', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('所有登录用户应看到组织者菜单', () => {
    seedSession({
      id: 'admin001',
      name: '系统管理员',
      role: 'admin',
      college: '软件学院'
    })

    renderWithApp(<SideMenu />, { route: '/home' })

    expect(screen.getByText('发布活动')).toBeInTheDocument()
    expect(screen.getByText('活动管理')).toBeInTheDocument()
    expect(screen.getByText('报名审核')).toBeInTheDocument()
    expect(screen.getByText('活动数据分析')).toBeInTheDocument()
  })

  it('管理员应看到管理后台入口', () => {
    seedSession({
      id: 'admin001',
      name: '系统管理员',
      role: 'admin',
      college: '软件学院'
    })

    renderWithApp(<SideMenu />, { route: '/home' })

    expect(screen.getByText('管理后台')).toBeInTheDocument()
  })

  it('普通学生不应看到管理后台', () => {
    seedSession({
      id: '524030910001',
      name: '张三',
      role: 'student',
      college: '软件学院'
    })

    renderWithApp(<SideMenu />, { route: '/home' })

    expect(screen.getByText('发布活动')).toBeInTheDocument()
    expect(screen.queryByText('管理后台')).not.toBeInTheDocument()
  })
})
