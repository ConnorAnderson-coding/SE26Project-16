import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

vi.mock('../../src/pages/Login', () => ({ default: () => <div>login page</div> }))
vi.mock('../../src/pages/OAuthCallback', () => ({ default: () => <div>oauth page</div> }))
vi.mock('../../src/pages/Home', () => ({ default: () => <div>home page</div> }))
vi.mock('../../src/pages/ActivityList', () => ({ default: () => <div>activity list page</div> }))
vi.mock('../../src/pages/ActivityDetail', () => ({ default: () => <div>activity detail page</div> }))
vi.mock('../../src/pages/CreateActivity', () => ({ default: () => <div>create page</div> }))
vi.mock('../../src/pages/MyActivities', () => ({ default: () => <div>my activities page</div> }))
vi.mock('../../src/pages/UserProfile', () => ({ default: () => <div>profile page</div> }))
vi.mock('../../src/pages/OrganizerDashboard', () => ({ default: () => <div>organizer page</div> }))
vi.mock('../../src/pages/SignupManagement', () => ({ default: () => <div>signup page</div> }))
vi.mock('../../src/pages/MyFavorites', () => ({ default: () => <div>favorites page</div> }))
vi.mock('../../src/pages/EditActivity', () => ({ default: () => <div>edit page</div> }))
vi.mock('../../src/pages/CheckIn', () => ({ default: () => <div>checkin page</div> }))
vi.mock('../../src/pages/Feedback', () => ({ default: () => <div>feedback page</div> }))
vi.mock('../../src/pages/OrganizerAnalytics', () => ({ default: () => <div>analytics page</div> }))
vi.mock('../../src/pages/CommunityClusters', () => ({ default: () => <div>community page</div> }))
vi.mock('../../src/pages/AdminDashboard', () => ({ default: () => <div>admin page</div> }))
vi.mock('../../src/pages/AdminCommunityClustering', () => ({ default: () => <div>admin clustering page</div> }))

import Router from '../../src/router'
import App from '../../src/App'

beforeEach(() => {
  window.history.replaceState({}, '', '/')
})

describe('application router', () => {
  it.each([
    ['/', 'login page'],
    ['/home', 'home page'],
    ['/activity/12', 'activity detail page'],
    ['/admin/community-clustering', 'admin clustering page']
  ])('renders %s', (path, label) => {
    window.history.replaceState({}, '', path)
    render(<Router />)
    expect(screen.getByText(label)).toBeInTheDocument()
  })

  it('App delegates rendering to Router', () => {
    render(<App />)
    expect(screen.getByText('login page')).toBeInTheDocument()
  })
})
