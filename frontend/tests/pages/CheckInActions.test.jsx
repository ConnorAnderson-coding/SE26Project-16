import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../helpers/renderWithApp'

const mocks = vi.hoisted(() => ({
  doCheckIn: vi.fn(),
  navigate: vi.fn()
}))

vi.mock('antd', async () => {
  const actual = await vi.importActual('antd')
  return {
    ...actual,
    Tabs: ({ items }) => <div>{items.map(item => <section key={item.key}>{item.children}</section>)}</div>
  }
})
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mocks.navigate,
    useSearchParams: () => [new URLSearchParams('activityId=activity-1&token=token-1'), vi.fn()]
  }
})
vi.mock('../../src/layouts/MainLayout', () => ({
  default: ({ children }) => <main>{children}</main>
}))
vi.mock('../../src/components/AuthGuard', () => ({
  default: ({ children }) => <>{children}</>
}))
vi.mock('../../src/context/AppContext', () => ({
  useApp: () => ({
    currentUser: { id: 'student-1', role: 'student' },
    checkIns: [],
    checkIn: mocks.doCheckIn
  })
}))
vi.mock('../../src/services/registrationApi', () => ({
  getMyRegistrations: vi.fn()
}))
vi.mock('../../src/services/activityApi', () => ({
  getMyActivities: vi.fn(),
  getActivityById: vi.fn()
}))
vi.mock('../../src/services/checkInApi', () => ({
  createQrSession: vi.fn(),
  createPasswordSession: vi.fn(),
  getStats: vi.fn()
}))

import * as registrationApi from '../../src/services/registrationApi'
import * as activityApi from '../../src/services/activityApi'
import CheckIn from '../../src/pages/CheckIn'
import QRCodeCheckInCallback from '../../src/pages/QRCodeCheckInCallback'

const activity = {
  id: 'activity-1',
  title: 'Participant Activity',
  organizerId: 'teacher-1',
  location: 'Room 1',
  status: 'published'
}

beforeEach(() => {
  vi.clearAllMocks()
  registrationApi.getMyRegistrations.mockResolvedValue([{
    id: 'signup-1',
    activityId: activity.id,
    status: 'approved'
  }])
  activityApi.getMyActivities.mockResolvedValue([])
  activityApi.getActivityById.mockResolvedValue(activity)
  mocks.doCheckIn.mockResolvedValue({ success: true, message: 'ok' })
})

describe('CheckIn participant actions', () => {
  it('uses scan callback for QR and still submits password and location check-ins', async () => {
    const getCurrentPosition = vi.fn(success => success({
      coords: { latitude: 31.2, longitude: 121.4 }
    }))
    vi.stubGlobal('navigator', {
      ...navigator,
      geolocation: { getCurrentPosition }
    })
    renderWithRouter(<CheckIn />, { route: '/checkin?activityId=activity-1' })

    expect(await screen.findByText(/扫描组织者展示的二维码/)).toBeInTheDocument()
    expect(screen.queryByPlaceholderText(/CHECKIN/)).not.toBeInTheDocument()

    const password = screen.getByPlaceholderText(/6位动态口令/)
    fireEvent.change(password, { target: { value: '12x3456' } })
    await userEvent.click(screen.getByRole('button', { name: /口令签到/ }))
    expect(mocks.doCheckIn).toHaveBeenCalledWith('activity-1', 'password', { code: '123456' })

    await userEvent.click(screen.getByRole('button', { name: /获取当前位置并签到/ }))
    await waitFor(() => expect(mocks.doCheckIn).toHaveBeenCalledWith('activity-1', 'location', {
      latitude: 31.2,
      longitude: 121.4
    }))
  })

  it('auto-submits scanned QR content and redirects home on success', async () => {
    renderWithRouter(<QRCodeCheckInCallback />, {
      route: '/checkin/qrcode?activityId=activity-1&token=token-1'
    })

    await waitFor(() => expect(mocks.doCheckIn).toHaveBeenCalledWith(
      'activity-1',
      'qrcode',
      { token: 'token-1' }
    ))
    expect(mocks.navigate).toHaveBeenCalledWith('/home', {
      replace: true,
      state: {
        toast: {
          type: 'success',
          content: '签到成功'
        }
      }
    })
  })

  it('rejects unsupported geolocation', async () => {
    vi.stubGlobal('navigator', { ...navigator, geolocation: undefined })
    renderWithRouter(<CheckIn />, { route: '/checkin?activityId=activity-1' })
    await screen.findByText(/扫描组织者展示的二维码/)
    expect(mocks.doCheckIn).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: /获取当前位置并签到/ }))
    expect(mocks.doCheckIn).not.toHaveBeenCalled()
  })
})
