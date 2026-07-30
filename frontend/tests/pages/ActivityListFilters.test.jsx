import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../helpers/renderWithApp'

vi.mock('antd', async () => {
  const actual = await vi.importActual('antd')
  function Select({ options = [], onChange, value, mode, placeholder }) {
    const label = placeholder || (options.some(option => option.value === 'composite') ? 'sort' : 'select')
    return (
      <select
        aria-label={label}
        multiple={mode === 'multiple'}
        value={value ?? (mode === 'multiple' ? [] : '')}
        onChange={(event) => {
          if (mode === 'multiple') {
            onChange?.([...event.target.selectedOptions].map(option => option.value))
          } else {
            onChange?.(event.target.value || null)
          }
        }}
      >
        <option value="">all</option>
        {options.map(option => <option key={String(option.value)} value={option.value}>{option.label}</option>)}
      </select>
    )
  }
  function Checkbox({ checked, onChange, children }) {
    return <label><input type="checkbox" checked={checked} onChange={onChange} />{children}</label>
  }
  function Slider({ value, onChange }) {
    return <input aria-label="slider" type="range" value={value} onChange={event => onChange?.(Number(event.target.value))} />
  }
  return { ...actual, Select, Checkbox, Slider }
})
vi.mock('../../src/layouts/MainLayout', () => ({
  default: ({ children }) => <main>{children}</main>
}))
vi.mock('../../src/components/AuthGuard', () => ({
  default: ({ children }) => <>{children}</>
}))
vi.mock('../../src/context/AppContext', () => ({
  useApp: () => ({
    currentUser: { interests: ['AI'], availableTime: ['weekend'] }
  })
}))
vi.mock('../../src/services/activityApi', () => ({
  getAllActivities: vi.fn()
}))

import { getAllActivities } from '../../src/services/activityApi'
import ActivityList from '../../src/pages/ActivityList'

const activities = [
  {
    id: '1',
    title: 'AI Lecture',
    category: 'academic',
    location: 'Room 1',
    status: 'published',
    startTime: '2027-01-01T10:00:00',
    endTime: '2027-01-01T12:00:00',
    signupCount: 20,
    maxParticipants: 50,
    hotnessScore: 10,
    tags: ['AI']
  },
  {
    id: '2',
    title: 'Sports Day',
    category: 'sports',
    location: 'Field',
    status: 'ended',
    startTime: '2026-01-01T10:00:00',
    endTime: '2026-01-01T12:00:00',
    signupCount: 40,
    maxParticipants: 100,
    hotnessScore: 30,
    tags: ['sports']
  },
  {
    id: '3',
    title: 'Draft',
    category: 'other',
    location: 'Room 1',
    status: 'draft',
    signupCount: 0,
    maxParticipants: 10,
    hotnessScore: 0,
    tags: []
  }
]

beforeEach(() => {
  vi.clearAllMocks()
  getAllActivities.mockResolvedValue(activities)
})

describe('ActivityList filters', () => {
  it('changes server and client filters, sorting, sliders, and clears them', async () => {
    renderWithRouter(<ActivityList />)
    expect(await screen.findByText('AI Lecture')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('全部类别'), { target: { value: 'academic' } })
    await waitFor(() => expect(getAllActivities).toHaveBeenCalledWith(expect.objectContaining({ category: 'academic' })))
    fireEvent.change(screen.getByLabelText('全部地点'), { target: { value: 'Room 1' } })
    await waitFor(() => expect(getAllActivities).toHaveBeenCalledWith(expect.objectContaining({ location: 'Room 1' })))
    fireEvent.change(screen.getByLabelText('全部状态'), { target: { value: 'published' } })
    await waitFor(() => expect(getAllActivities).toHaveBeenCalledWith(expect.objectContaining({ status: 'published' })))
    await userEvent.selectOptions(screen.getByLabelText('选择兴趣'), ['AI'])
    await userEvent.selectOptions(screen.getByLabelText('全部时段'), ['weekend'])

    const sort = screen.getByLabelText('sort')
    for (const value of ['hot', 'time', 'signup']) {
      fireEvent.change(sort, { target: { value } })
    }

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'AI' } })
    fireEvent.change(sort, { target: { value: 'composite' } })
    const sliders = await screen.findAllByRole('slider')
    fireEvent.change(sliders[0], { target: { value: '50' } })
    fireEvent.change(sliders[1], { target: { value: '5' } })

    const checkboxes = screen.getAllByRole('checkbox')
    await userEvent.click(checkboxes[0])
    await userEvent.click(checkboxes[1])

    const clear = await screen.findByRole('button', { name: /清\s*空筛选/ })
    await userEvent.click(clear)
  }, 15000)

  it('shows and retries a request error', async () => {
    getAllActivities.mockRejectedValueOnce(new Error('list failed'))
    renderWithRouter(<ActivityList />)
    expect(await screen.findByText('list failed')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /重\s*试/ }))
    expect(await screen.findByText('AI Lecture')).toBeInTheDocument()
  })
})
