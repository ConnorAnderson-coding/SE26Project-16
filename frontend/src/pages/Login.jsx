import { useState,useEffect } from 'react'
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Select,
  Spin,
  Tabs,
  Typography,
  message
} from 'antd'
import { LockOutlined, LoginOutlined, UserOutlined } from '@ant-design/icons'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/http'
import { useApp } from '../context/AppContext'
import { AVAILABLE_TIME_OPTIONS, COLLEGES, INTEREST_TAGS } from '../data/mockData'
import * as authApi from '../services/authApi'

const { Title, Paragraph } = Typography

function safeReturnPath(locationState) {
  const from = locationState?.from
  if (!from?.pathname || !from.pathname.startsWith('/') || from.pathname === '/') {
    return '/home'
  }
  return `${from.pathname}${from.search || ''}${from.hash || ''}`
}

function loginErrorMessage(error) {
  if (error instanceof ApiError && error.code === 'INVALID_CREDENTIALS') {
    return '账号或密码错误'
  }
  if (error instanceof ApiError && error.code === 'CSRF_TOKEN_INVALID') {
    return '安全校验已失效，请刷新页面后重新提交'
  }
  return error instanceof ApiError ? error.message : '登录失败，请稍后重试'
}

function registerErrorMessage(error) {
  if (error instanceof ApiError && error.code === 'ACCOUNT_ALREADY_EXISTS') {
    return '该账号已存在，请直接登录或更换账号'
  }
  if (error instanceof ApiError && error.code === 'INVALID_AUTH_REQUEST') {
    return '注册信息不符合要求，请检查各字段后重试'
  }
  if (error instanceof ApiError && error.code === 'CSRF_TOKEN_INVALID') {
    return '安全校验已失效，请刷新页面后重新提交'
  }
  return error instanceof ApiError ? error.message : '注册失败，请稍后重试'
}

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login, register, authStatus, authError, refreshAuth } = useApp()
  const [loginForm] = Form.useForm()
  const [registerForm] = Form.useForm()
  const [activeTab, setActiveTab] = useState('login')
  const [loginSubmitting, setLoginSubmitting] = useState(false)
  const [loading, setLoading] = useState(false)
  const [registerSubmitting, setRegisterSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)
  const [registrationNotice, setRegistrationNotice] = useState(null)

  if (authStatus === 'authenticated') {
    return <Navigate to={safeReturnPath(location.state)} replace />
  }

  const handleLogin = async (values) => {
    if (loginSubmitting) return
    setLoginSubmitting(true)
    setFormError(null)
    try {
      await login(values.userId, values.password)
      message.success('登录成功')
      navigate(safeReturnPath(location.state), { replace: true })
    } catch (error) {
      setFormError(loginErrorMessage(error))
    } finally {
      setLoginSubmitting(false)
    }
  }

  const handleRegister = async (values) => {
    if (registerSubmitting) return
    setRegisterSubmitting(true)
    setFormError(null)
    try {
      await register(values)
      registerForm.resetFields()
      setRegistrationNotice('注册成功，请使用新账号登录')
      setActiveTab('login')
    } catch (error) {
      setFormError(registerErrorMessage(error))
    } finally {
      setRegisterSubmitting(false)
    }
  }

  if (authStatus === 'initializing') {
    return (
      <div className="login-page">
        <Spin tip="正在确认登录状态" size="large" />
      </div>
    )
  }

  if (authStatus === 'error') {
    return (
      <div className="login-page">
        <Card className="login-card">
          <Alert
            type="error"
            showIcon
            message="无法连接认证服务"
            description={authError || '请检查网络连接后重试'}
            action={<Button onClick={() => refreshAuth().catch(() => {})}>重试</Button>}
          />
        </Card>
      </div>
    )
  }

  return (
    <div className="login-page">
      <div className="login-bg" />
      <Card className="login-card">
        <div className="login-header">
          <Title level={3} style={{ marginBottom: 4 }}>校园活动一站式服务平台</Title>
          <Paragraph type="secondary">发现精彩活动 · 智能推荐 · 一站式参与</Paragraph>
        </div>

        {registrationNotice && (
          <Alert type="success" showIcon message={registrationNotice} style={{ marginBottom: 16 }} />
        )}
        {formError && (
          <Alert type="error" showIcon message={formError} style={{ marginBottom: 16 }} />
        )}

        <Tabs
          activeKey={activeTab}
          onChange={(key) => {
            setActiveTab(key)
            setFormError(null)
          }}
          items={[
            {
              key: 'login',
              label: '登录',
              children: (
                <Form name="login" form={loginForm} onFinish={handleLogin} size="large">
                  <Form.Item name="id" rules={[{ required: true, message: '请输入学号/工号' }]}>
                    <Input
                      prefix={<UserOutlined />}
                      placeholder="学号/工号"
                      autoComplete="username"
                    />
                  </Form.Item>
                  <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
                    <Input.Password
                      prefix={<LockOutlined />}
                      placeholder="密码"
                      autoComplete="current-password"
                    />
                  </Form.Item>
                  <Form.Item>
                    <Button
                      type="primary"
                      htmlType="submit"
                      block
                      loading={loginSubmitting}
                      disabled={loginSubmitting}
                    >
                      登录
                    </Button>
                  </Form.Item>
                  <Form.Item style={{ marginBottom: 0 }}>
                    <Button
                      block
                      icon={<LoginOutlined />}
                      onClick={authApi.startJAccountLogin}
                    >
                      使用 jAccount 单点登录
                    </Button>
                  </Form.Item>
                </Form>
              )
            },
            {
              key: 'register',
              label: '注册',
              children: (
                <Form
                  name="register"
                  form={registerForm}
                  onFinish={handleRegister}
                  layout="vertical"
                  size="large"
                >
                  <Form.Item name="id" label="学号/工号" rules={[{ required: true, message: '请输入学号/工号' }]}>
                    <Input autoComplete="username" />
                  </Form.Item>
                  <Form.Item
                    name="password"
                    label="密码"
                    rules={[
                      { required: true, message: '请输入密码' },
                      { min: 8, message: '密码至少需要 8 个字符' }
                    ]}
                  >
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                  <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
                    <Input autoComplete="name" />
                  </Form.Item>
                  <Form.Item name="college" label="学院">
                    <Select allowClear options={COLLEGES.map(value => ({ label: value, value }))} />
                  </Form.Item>
                  <Form.Item name="grade" label="年级">
                    <Input placeholder="如：2024级" />
                  </Form.Item>
                  <Form.Item name="interests" label="兴趣标签">
                    <Select mode="multiple" options={INTEREST_TAGS.map(value => ({ label: value, value }))} />
                  </Form.Item>
                  <Form.Item name="availableTime" label="可参与时间">
                    <Select mode="multiple" options={AVAILABLE_TIME_OPTIONS} />
                  </Form.Item>
                  <Form.Item>
                    <Button
                      type="primary"
                      htmlType="submit"
                      block
                      loading={registerSubmitting}
                      disabled={registerSubmitting}
                    >
                      注册
                    </Button>
                  </Form.Item>
                </Form>
              )
            }
          ]}
        />
      </Card>
    </div>
  )
}
