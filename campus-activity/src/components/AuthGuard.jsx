import { Navigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'

export default function AuthGuard({ children }) {
  const { currentUser } = useApp()
  if (!currentUser) return <Navigate to="/" replace />
  return children
}
