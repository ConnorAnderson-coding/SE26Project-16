import { Navigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'

export default function AdminGuard({ children }) {
  const { currentUser } = useApp()

  if (currentUser?.role !== 'admin') {
    return <Navigate to="/home" replace />
  }

  return children
}
