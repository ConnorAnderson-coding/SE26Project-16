import { Result } from 'antd'
import { useApp } from '../context/AppContext'

export default function AdminGuard({ children }) {
  const { currentUser } = useApp()

  if (currentUser?.role !== 'admin') {
    return (
      <Result
        status="403"
        title="403"
        subTitle="你没有访问管理员功能的权限"
      />
    )
  }

  return children
}
