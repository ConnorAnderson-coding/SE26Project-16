import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../helpers/renderWithApp'
import MainLayout from '../../src/layouts/MainLayout'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  logout: vi.fn(),
  isJAccountSession: vi.fn()
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => mocks.navigate }
})

vi.mock('../../src/context/AppContext', () => ({
  useApp: () => ({
    currentUser: { id: 'user-1', name: 'Layout User' },
    logout: mocks.logout,
    isJAccountSession: mocks.isJAccountSession
  })
}))

vi.mock('../../src/services/authApi', () => ({
  startJAccountLogout: vi.fn()
}))

vi.mock('../../src/components/SideMenu', () => ({
  default: () => <nav>side menu</nav>
}))

import * as authApi from '../../src/services/authApi'

beforeEach(() => {
  vi.clearAllMocks()
  mocks.isJAccountSession.mockReturnValue(false)
})

describe('MainLayout', () => {
  it('renders the title, user, menu, and page content', () => {
    renderWithRouter(
      <MainLayout title="Custom title"><p>page body</p></MainLayout>
    )
    expect(screen.getByText('Custom title')).toBeInTheDocument()
    expect(screen.getByText('Layout User')).toBeInTheDocument()
    expect(screen.getByText('side menu')).toBeInTheDocument()
    expect(screen.getByText('page body')).toBeInTheDocument()
  })

  it('opens the account menu and navigates to the profile', async () => {
    renderWithRouter(<MainLayout>body</MainLayout>)
    await userEvent.click(screen.getByText('Layout User'))
    const profile = await screen.findByText('个人中心')
    await userEvent.click(profile)
    expect(mocks.navigate).toHaveBeenCalledWith('/profile')
  })

  it('logs out a local account and returns to login', async () => {
    renderWithRouter(<MainLayout>body</MainLayout>)
    await userEvent.click(screen.getByText('Layout User'))
    await userEvent.click(await screen.findByText('退出登录'))
    expect(mocks.logout).toHaveBeenCalled()
    expect(mocks.navigate).toHaveBeenCalledWith('/')
  })

  it('starts the remote logout flow for a jAccount session', async () => {
    mocks.isJAccountSession.mockReturnValue(true)
    renderWithRouter(<MainLayout>body</MainLayout>)
    await userEvent.click(screen.getByText('Layout User'))
    await userEvent.click(await screen.findByText('退出登录'))
    await waitFor(() => expect(authApi.startJAccountLogout).toHaveBeenCalled())
    expect(mocks.navigate).not.toHaveBeenCalledWith('/')
  })
})
