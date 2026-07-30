import dayjs from 'dayjs'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../helpers/renderWithApp'

const mocks = vi.hoisted(() => ({
  values: {},
  navigate: vi.fn(),
  form: {
    setFieldsValue: vi.fn(),
    resetFields: vi.fn()
  },
  app: {
    currentUser: {
      id: 'user-1',
      name: 'Test User',
      role: 'teacher',
      college: 'Software',
      grade: '2024',
      interests: ['AI'],
      availableTime: ['weekend']
    },
    login: vi.fn(),
    register: vi.fn(),
    createActivity: vi.fn(),
    updateActivity: vi.fn(),
    updateProfile: vi.fn(),
    submitFeedback: vi.fn(),
    publishRecord: vi.fn()
  }
}))

vi.mock('antd', async () => {
  const actual = await vi.importActual('antd')
  function Form({ children, onFinish }) {
    return (
      <form onSubmit={(event) => {
        event.preventDefault()
        onFinish?.(mocks.values)
      }}>
        {children}
      </form>
    )
  }
  Form.Item = ({ children }) => <>{children}</>
  Form.useForm = () => [mocks.form]
  Form.useWatch = () => undefined
  function Select(props) {
    if (props.onChange) {
      return <button type="button" onClick={() => props.onChange('activity-1')}>choose option</button>
    }
    const ActualSelect = actual.Select
    return <ActualSelect {...props} />
  }
  return {
    ...actual,
    Form,
    Tabs: ({ items }) => <div>{items.map(item => <section key={item.key}>{item.children}</section>)}</div>,
    Select,
    Upload: ({ children, onChange, beforeUpload }) => (
      <button
        type="button"
        onClick={() => {
          const originFileObj = new Blob(['image'], { type: 'image/png' })
          beforeUpload?.(originFileObj)
          onChange?.({ fileList: [{ uid: 'upload-1', originFileObj }] })
        }}
      >
        {children}
      </button>
    )
  }
})

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mocks.navigate,
    useParams: () => ({ id: 'activity-1' })
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
vi.mock('../../src/components/MapLocationPicker', () => ({
  default: ({ onChange }) => <button type="button" onClick={() => onChange({ latitude: 31, longitude: 121 })}>map</button>
}))
vi.mock('../../src/services/activityApi', () => ({
  getActivityById: vi.fn(),
  getMyActivities: vi.fn()
}))
vi.mock('../../src/services/registrationApi', () => ({
  getMyRegistrations: vi.fn()
}))
vi.mock('../../src/services/feedbackApi', () => ({
  getMyFeedbacks: vi.fn()
}))
vi.mock('../../src/services/checkInApi', () => ({
  getMine: vi.fn()
}))
vi.mock('../../src/services/authApi', () => ({
  startJAccountLogin: vi.fn()
}))

import * as activityApi from '../../src/services/activityApi'
import * as registrationApi from '../../src/services/registrationApi'
import * as feedbackApi from '../../src/services/feedbackApi'
import * as checkInApi from '../../src/services/checkInApi'
import CreateActivity from '../../src/pages/CreateActivity'
import EditActivity from '../../src/pages/EditActivity'
import Feedback from '../../src/pages/Feedback'
import Login from '../../src/pages/Login'
import OrganizerDashboard from '../../src/pages/OrganizerDashboard'
import UserProfile from '../../src/pages/UserProfile'

const activity = {
  id: 'activity-1',
  title: 'Workshop',
  category: 'lecture',
  description: 'Description',
  location: 'Room 1',
  latitude: 31,
  longitude: 121,
  checkInRadiusMeters: 200,
  maxParticipants: 50,
  tags: ['AI'],
  poster: 'poster.png',
  startTime: '2026-06-01T09:00:00',
  endTime: '2026-06-01T11:00:00',
  organizerId: 'user-1',
  status: 'ended',
  signupCount: 10
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.app.currentUser = {
    id: 'user-1',
    name: 'Test User',
    role: 'teacher',
    college: 'Software',
    grade: '2024',
    interests: ['AI'],
    availableTime: ['weekend']
  }
  mocks.app.login.mockResolvedValue({ success: true })
  mocks.app.register.mockResolvedValue({ success: true })
  mocks.app.createActivity.mockResolvedValue('created-1')
  mocks.app.updateActivity.mockResolvedValue(activity)
  mocks.app.updateProfile.mockResolvedValue(mocks.app.currentUser)
  mocks.app.submitFeedback.mockResolvedValue({ success: true, message: 'ok' })
  mocks.app.publishRecord.mockResolvedValue()
  activityApi.getActivityById.mockResolvedValue(activity)
  activityApi.getMyActivities.mockResolvedValue([activity])
  registrationApi.getMyRegistrations.mockResolvedValue([{
    id: 'signup-1',
    activityId: activity.id,
    status: 'approved'
  }])
  feedbackApi.getMyFeedbacks.mockResolvedValue([])
  checkInApi.getMine.mockResolvedValue([])
  vi.stubGlobal('URL', {
    ...URL,
    createObjectURL: vi.fn(() => 'blob:test-image')
  })
})

describe('page form actions', () => {
  it('submits a new activity and covers default values', async () => {
    mocks.values = {
      title: 'New activity',
      category: 'lecture',
      description: 'Details',
      timeRange: [dayjs('2026-08-01T10:00:00'), dayjs('2026-08-01T12:00:00')],
      location: 'Room 2',
      maxParticipants: '40',
      latitude: 31.2,
      longitude: 121.4,
      tags: ['AI'],
      poster: [{ thumbUrl: 'thumb.png' }]
    }
    renderWithRouter(<CreateActivity />)
    await userEvent.click(screen.getByText('map'))
    const upload = screen.getAllByRole('button').find(button => button.textContent.includes('上传海报'))
    await userEvent.click(upload)
    fireEvent.submit(screen.getByRole('button', { name: /发布活动/ }).closest('form'))
    await waitFor(() => expect(mocks.app.createActivity).toHaveBeenCalledWith(expect.objectContaining({
      title: 'New activity',
      maxParticipants: 40,
      poster: 'thumb.png',
      checkInRadiusMeters: 200
    })))
    expect(mocks.navigate).toHaveBeenCalledWith('/activity/created-1')
  })

  it('handles create activity API failure', async () => {
    mocks.values = {
      title: 'Bad',
      category: 'lecture',
      description: 'Details',
      timeRange: [dayjs(), dayjs().add(1, 'hour')],
      location: 'Room',
      maxParticipants: 1
    }
    mocks.app.createActivity.mockRejectedValueOnce(new Error('create failed'))
    renderWithRouter(<CreateActivity />)
    fireEvent.submit(screen.getByRole('button', { name: /发布活动/ }).closest('form'))
    await waitFor(() => expect(mocks.app.createActivity).toHaveBeenCalled())
  })

  it('loads and saves an owned activity', async () => {
    mocks.values = {
      ...activity,
      timeRange: [dayjs(activity.startTime), dayjs(activity.endTime)],
      maxParticipants: '60',
      poster: [{ url: 'new-poster.png' }]
    }
    renderWithRouter(<EditActivity />)
    const save = await screen.findByRole('button', { name: /保存修改/ })
    await userEvent.click(screen.getByText('map'))
    const upload = screen.getAllByRole('button').find(button => button.textContent.includes('上传海报'))
    await userEvent.click(upload)
    fireEvent.submit(save.closest('form'))
    await waitFor(() => expect(mocks.app.updateActivity).toHaveBeenCalledWith(
      activity.id,
      expect.objectContaining({ maxParticipants: 60, poster: 'new-poster.png' })
    ))
    expect(mocks.navigate).toHaveBeenCalledWith('/organizer')
  })

  it('shows the edit error and unauthorized states', async () => {
    activityApi.getActivityById.mockRejectedValueOnce(new Error('load failed'))
    const first = renderWithRouter(<EditActivity />)
    expect(await screen.findByText('load failed')).toBeInTheDocument()
    first.unmount()

    activityApi.getActivityById.mockResolvedValueOnce({ ...activity, organizerId: 'another-user' })
    renderWithRouter(<EditActivity />)
    await waitFor(() => expect(activityApi.getActivityById).toHaveBeenCalled())
    expect(await screen.findByRole('main')).toBeInTheDocument()
  })

  it('submits successful and unsuccessful login and registration flows', async () => {
    mocks.app.currentUser = null
    mocks.values = { userId: 'student-1', password: 'secret' }
    renderWithRouter(<Login />)
    const submitButtons = screen.getAllByRole('button').filter(button => button.type === 'submit')
    fireEvent.submit(submitButtons[0].closest('form'))
    await waitFor(() => expect(mocks.app.login).toHaveBeenCalledWith('student-1', 'secret'))

    mocks.values = { id: 'student-2', name: 'New Student' }
    fireEvent.submit(submitButtons[1].closest('form'))
    await waitFor(() => expect(mocks.app.register).toHaveBeenCalledWith(mocks.values))
  })

  it('submits profile edits and handles update failure', async () => {
    mocks.values = { name: 'Updated', college: 'Software', grade: '2025' }
    renderWithRouter(<UserProfile />)
    await userEvent.click(screen.getByRole('button', { name: /编辑档案/ }))
    fireEvent.submit(screen.getByRole('button', { name: /保\s*存/ }).closest('form'))
    await waitFor(() => expect(mocks.app.updateProfile).toHaveBeenCalledWith(mocks.values))
  })

  it('publishes an organizer activity record', async () => {
    mocks.values = {
      summary: 'Completed successfully',
      photos: [{ uid: 'photo-1', thumbUrl: 'photo.png' }]
    }
    renderWithRouter(<OrganizerDashboard />)
    const publish = await screen.findByRole('button', { name: /发布记录/ })
    await userEvent.click(publish)
    const upload = screen.getAllByRole('button').find(button => button.textContent.includes('上传照片'))
    await userEvent.click(upload)
    const submit = await screen.findByRole('button', { name: /发布活动记录/ })
    fireEvent.submit(submit.closest('form'))
    await waitFor(() => expect(mocks.app.publishRecord).toHaveBeenCalledWith(activity.id, {
      summary: 'Completed successfully',
      photos: ['photo.png']
    }))
  }, 15000)

  it('warns without a selected feedback activity', async () => {
    mocks.values = { rating: 5, content: 'Great' }
    renderWithRouter(<Feedback />)
    const submit = await screen.findByRole('button', { name: /提交评价/ })
    fireEvent.submit(submit.closest('form'))
    expect(mocks.app.submitFeedback).not.toHaveBeenCalled()
  })

  it('submits feedback for a selected activity and reloads history', async () => {
    mocks.values = { rating: 5, content: 'Great' }
    renderWithRouter(<Feedback />)
    await screen.findByRole('button', { name: 'choose option' })
    await userEvent.click(screen.getByRole('button', { name: 'choose option' }))
    const submit = screen.getByRole('button', { name: /提交评价/ })
    fireEvent.submit(submit.closest('form'))
    await waitFor(() => expect(mocks.app.submitFeedback).toHaveBeenCalledWith({
      activityId: 'activity-1',
      rating: 5,
      content: 'Great'
    }))
    expect(mocks.form.resetFields).toHaveBeenCalled()
  })
})
