import { Navigate } from 'react-router-dom'
import { Spin } from 'antd'
import { useApp } from '../context/AppContext'

export default function AuthGuard({ children }) {
  const { currentUser, initializing } = useApp()
  if (initializing) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
        <Spin size="large" tip="加载中..." />
      </div>
    )
  }
  if (!currentUser) return <Navigate to="/" replace />
  return children
}
