import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import SideMenu from '../../src/components/SideMenu'
import { renderWithApp } from '../helpers/renderWithApp'

function authenticate(role) {
  fetch.mockResolvedValueOnce(new Response(JSON.stringify({
    success: true,
    data: { id: `${role}-1`, name: role, role }
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  }))
}

describe('SideMenu 组件', () => {
  it('所有登录用户应看到组织者菜单', async () => {
    authenticate('student')
    renderWithApp(<SideMenu />, { route: '/home' })

    expect(await screen.findByText('发布活动')).toBeInTheDocument()
    expect(screen.getByText('活动管理')).toBeInTheDocument()
    expect(screen.getByText('报名审核')).toBeInTheDocument()
    expect(screen.getByText('活动数据分析')).toBeInTheDocument()
  })

  it('管理员应看到管理后台入口', async () => {
    authenticate('admin')
    renderWithApp(<SideMenu />, { route: '/home' })

    expect(await screen.findByText('管理后台')).toBeInTheDocument()
  })

  it('普通学生不应看到管理后台', async () => {
    authenticate('student')
    renderWithApp(<SideMenu />, { route: '/home' })

    await screen.findByText('发布活动')
    expect(screen.queryByText('管理后台')).not.toBeInTheDocument()
  })
})
