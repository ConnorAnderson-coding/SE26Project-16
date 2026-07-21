import { Alert, Button, Flex, Spin } from 'antd'
import { Navigate, useLocation } from 'react-router-dom'
import { useApp } from '../context/AppContext'

export default function AuthGuard({ children }) {
  const location = useLocation()
  const { authStatus, authError, refreshAuth } = useApp()

  if (authStatus === 'initializing') {
    return (
      <Flex align="center" justify="center" style={{ minHeight: '40vh' }}>
        <Spin tip="正在确认登录状态" size="large" />
      </Flex>
    )
  }

  if (authStatus === 'error') {
    return (
      <Alert
        type="error"
        showIcon
        message="无法确认登录状态"
        description={authError || '请检查网络连接后重试'}
        action={<Button onClick={() => refreshAuth().catch(() => {})}>重试</Button>}
      />
    )
  }

  if (authStatus === 'anonymous') {
    return <Navigate to="/" replace state={{ from: location }} />
  }

  return children
}
