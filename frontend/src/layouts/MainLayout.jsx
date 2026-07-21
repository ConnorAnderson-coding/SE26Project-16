import { Layout, Typography, Avatar, Dropdown, Space, Badge, message } from 'antd'
import {
  UserOutlined,
  LogoutOutlined,
  BellOutlined
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import * as authApi from '../services/authApi'
import SideMenu from '../components/SideMenu'

const { Header, Sider, Content } = Layout

export default function MainLayout({ children, title }) {
  const navigate = useNavigate()
  const { currentUser, logout, isJAccountSession } = useApp()

  const menuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
      onClick: () => navigate('/profile')
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',

      onClick: async () => {
        try {
          const shouldLogoutJAccount = isJAccountSession()
          await logout()
          if (shouldLogoutJAccount) {
            authApi.startJAccountLogout()
          } else {
            navigate('/')
          }
        } catch {
          message.error('退出登录失败，请稍后重试')
        }
      }
    }
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        width={220}
        theme="light"
        style={{ borderRight: '1px solid #f0f0f0' }}
      >
        <div className="logo-area">
          <span className="logo-icon">🎓</span>
          <span className="logo-text">校园活动</span>
        </div>
        <SideMenu />
      </Sider>

      <Layout>
        <Header className="main-header">
          <Typography.Title level={4} style={{ margin: 0 }}>
            {title || '校园活动一站式服务平台'}
          </Typography.Title>

          <Space size="large">
            <Badge count={0} showZero={false}>
              <BellOutlined style={{ fontSize: 18, cursor: 'pointer' }} />
            </Badge>
            <Dropdown menu={{ items: menuItems }} placement="bottomRight">
              <Space style={{ cursor: 'pointer' }}>
                <Avatar icon={<UserOutlined />} style={{ background: '#1677ff' }} />
                <span>{currentUser?.name}</span>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content className="main-content">
          {children}
        </Content>
      </Layout>
    </Layout>
  )
}
