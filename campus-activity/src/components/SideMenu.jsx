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
  DashboardOutlined
} from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'

const menuItems = [
  { key: '/home', icon: <HomeOutlined />, label: '首页推荐' },
  { key: '/activities', icon: <UnorderedListOutlined />, label: '活动检索' },
  { key: '/create', icon: <PlusCircleOutlined />, label: '发布活动' },
  { key: '/my', icon: <CalendarOutlined />, label: '我的报名' },
  { key: '/favorites', icon: <HeartOutlined />, label: '我的收藏' },
  { key: '/organizer', icon: <TeamOutlined />, label: '活动管理' },
  { key: '/checkin', icon: <CheckCircleOutlined />, label: '活动签到' },
  { key: '/feedback', icon: <StarOutlined />, label: '活动反馈' },
  { key: '/profile', icon: <UserOutlined />, label: '个人中心' },
  { key: '/admin', icon: <DashboardOutlined />, label: '管理后台' }
]

export default function SideMenu() {
  const navigate = useNavigate()
  const location = useLocation()

  const selectedKey = menuItems.find(item =>
    location.pathname === item.key ||
    (item.key !== '/home' && location.pathname.startsWith(item.key))
  )?.key || '/home'

  return (
    <Menu
      mode="inline"
      selectedKeys={[selectedKey]}
      items={menuItems}
      onClick={({ key }) => navigate(key)}
      style={{ border: 'none' }}
    />
  )
}
