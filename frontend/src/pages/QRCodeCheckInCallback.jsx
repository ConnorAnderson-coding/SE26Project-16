import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Result, Spin } from 'antd'
import { useApp } from '../context/AppContext'

export default function QRCodeCheckInCallback() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { currentUser, initializing, checkIn } = useApp()
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const submittedRef = useRef(false)

  useEffect(() => {
    if (initializing || submittedRef.current) return

    const activityId = searchParams.get('activityId')
    const token = searchParams.get('token')
    if (!activityId || !token) {
      setError('二维码内容无效')
      return
    }

    if (!currentUser) {
      setError('请先登录后再扫码签到')
      return
    }

    submittedRef.current = true
    setSubmitting(true)
    checkIn(activityId, 'qrcode', { token })
      .then((result) => {
        if (result.success) {
          navigate('/home', {
            replace: true,
            state: {
              toast: {
                type: 'success',
                content: '签到成功'
              }
            }
          })
          return
        }
        setError(result.message || '签到失败')
      })
      .catch((err) => {
        setError(err.message || '签到失败')
      })
      .finally(() => {
        setSubmitting(false)
      })
  }, [checkIn, currentUser, initializing, navigate, searchParams])

  if (initializing || submitting || (!error && !submittedRef.current)) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <Spin size="large" tip="正在签到..." />
      </div>
    )
  }

  if (error) {
    return (
      <Result
        status="warning"
        title={error}
        extra={<Button type="primary" onClick={() => navigate('/', { replace: true })}>返回登录页</Button>}
      />
    )
  }

  return null
}
