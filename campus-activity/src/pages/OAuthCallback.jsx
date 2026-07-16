import { useEffect } from 'react'
import { Card, Result, Spin, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'

function readCallbackParams() {
  const hash = window.location.hash.startsWith('#')
    ? window.location.hash.slice(1)
    : window.location.hash
  const source = hash || window.location.search.slice(1)
  return new URLSearchParams(source)
}

export default function OAuthCallback() {
  const navigate = useNavigate()
  const { completeJAccountLogin } = useApp()

  useEffect(() => {
    const params = readCallbackParams()
    const token = params.get('token')
    const error = params.get('error')

    if (error) {
      message.error(error)
      navigate('/', { replace: true })
      return
    }

    if (!token) {
      message.error('jAccount 登录回调缺少 token')
      navigate('/', { replace: true })
      return
    }

    completeJAccountLogin(token)
      .then(() => {
        message.success('登录成功')
        navigate('/home', { replace: true })
      })
      .catch((err) => {
        message.error(err.message || 'jAccount 登录失败')
        navigate('/', { replace: true })
      })
  }, [completeJAccountLogin, navigate])

  return (
    <div className="login-page">
      <div className="login-bg" />
      <Card className="login-card">
        <Result
          icon={<Spin size="large" />}
          title="正在完成登录"
        />
      </Card>
    </div>
  )
}
