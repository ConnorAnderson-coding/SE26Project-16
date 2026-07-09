import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ActivityCard from '../../src/components/ActivityCard'
import { initialActivities } from '../../src/data/mockData'
import { renderWithRouter } from '../helpers/renderWithApp'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate
  }
})

const sampleActivity = initialActivities[0]

describe('ActivityCard 组件', () => {
  it('应展示活动标题与分类', () => {
    renderWithRouter(
      <ActivityCard activity={sampleActivity} />
    )

    expect(screen.getByText(sampleActivity.title)).toBeInTheDocument()
    expect(screen.getByText('学术讲座')).toBeInTheDocument()
  })

  it('应展示活动地点与报名人数', () => {
    renderWithRouter(
      <ActivityCard activity={sampleActivity} />
    )

    expect(screen.getByText(new RegExp(sampleActivity.location))).toBeInTheDocument()
    expect(
      screen.getByText(`${sampleActivity.signupCount}/${sampleActivity.maxParticipants} 人`)
    ).toBeInTheDocument()
  })

  it('已结束活动应显示已结束标签', () => {
    const endedActivity = initialActivities.find(a => a.status === 'ended')

    renderWithRouter(
      <ActivityCard activity={endedActivity} />
    )

    expect(screen.getByText('已结束')).toBeInTheDocument()
  })

  it('开启推荐时应显示智能推荐标签', () => {
    renderWithRouter(
      <ActivityCard
        activity={sampleActivity}
        showRecommend
        recommendScore={50}
      />
    )

    expect(screen.getByText('智能推荐')).toBeInTheDocument()
  })

  it('点击查看详情应跳转至活动详情页', async () => {
    mockNavigate.mockClear()
    const user = userEvent.setup()

    renderWithRouter(
      <ActivityCard activity={sampleActivity} />
    )

    await user.click(screen.getByRole('button', { name: '查看详情' }))

    expect(mockNavigate).toHaveBeenCalledWith(`/activity/${sampleActivity.id}`)
  })
})
