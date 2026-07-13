import { Menu } from 'antd'
import {
  HomeOutlined,
  UnorderedListOutlined,
  PlusCircleOutlined,
  CalendarOutlined,
  HeartOutlined,
  TeamOutlined,
  UserOutlined,
  CheckCircleOutlined,
  StarOutlined,
  DashboardOutlined,
  AuditOutlined,
  BarChartOutlined,
  ClusterOutlined
} from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useApp } from '../context/AppContext'

const participantItems = [
  { key: '/home', icon: <HomeOutlined />, label: '首页推荐' },
  { key: '/activities', icon: <UnorderedListOutlined />, label: '活动检索' },
  { key: '/my', icon: <CalendarOutlined />, label: '我的报名' },
  { key: '/favorites', icon: <HeartOutlined />, label: '我的收藏' },
  { key: '/checkin', icon: <CheckCircleOutlined />, label: '活动签到' },
  { key: '/feedback', icon: <StarOutlined />, label: '活动反馈' },
  { key: '/profile', icon: <UserOutlined />, label: '个人中心' },
  { key: '/community', icon: <ClusterOutlined />, label: '社区聚类' }
]

const organizerItems = [
  { key: '/create', icon: <PlusCircleOutlined />, label: '发布活动' },
  { key: '/organizer', icon: <TeamOutlined />, label: '活动管理' },
  { key: '/signup-management', icon: <AuditOutlined />, label: '报名审核' },
  { key: '/organizer-analytics', icon: <BarChartOutlined />, label: '活动数据分析' }
]

const adminItems = [
  { key: '/admin', icon: <DashboardOutlined />, label: '管理后台' }
]

function resolveSelectedKey(pathname) {
  if (pathname.startsWith('/edit/')) return '/organizer'
  if (pathname.startsWith('/signup-management')) return '/signup-management'
  if (pathname.startsWith('/organizer-analytics')) return '/organizer-analytics'
  if (pathname.startsWith('/analytics')) return '/analytics'
  if (pathname.startsWith('/activity/')) return null

  const allKeys = [
    ...participantItems,
    ...organizerItems,
    ...adminItems
  ].map(i => i.key)

  const exact = allKeys.find(key => pathname === key)
  if (exact) return exact

  const prefix = allKeys
    .filter(key => key !== '/home')
    .find(key => pathname.startsWith(key))

  return prefix || '/home'
}

export default function SideMenu() {
  const navigate = useNavigate()
  const location = useLocation()
  const { currentUser } = useApp()

  const isAdmin = currentUser?.role === 'admin'

  const items = [
    { type: 'group', label: '参与者', children: participantItems },
    { type: 'group', label: '组织者', children: organizerItems },
    ...(isAdmin
      ? [{ type: 'group', label: '管理员', children: adminItems }]
      : [])
  ]

  const selectedKey = resolveSelectedKey(location.pathname)

  return (
    <Menu
      mode="inline"
      selectedKeys={selectedKey ? [selectedKey] : []}
      items={items}
      onClick={({ key }) => navigate(key)}
      style={{ border: 'none' }}
    />
  )
}
