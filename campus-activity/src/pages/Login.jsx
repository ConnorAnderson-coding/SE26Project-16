import { useState } from 'react'
import { Card, Input, Button, Tabs, Form, Select, message, Typography } from 'antd'
import { UserOutlined, LockOutlined, LoginOutlined } from '@ant-design/icons'
import { Navigate, useNavigate } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import * as authApi from '../services/authApi'
import { COLLEGES, INTEREST_TAGS, AVAILABLE_TIME_OPTIONS } from '../data/mockData'

const { Title, Paragraph } = Typography

export default function Login() {
  const navigate = useNavigate()
  const { login, register, currentUser, initializing } = useApp()
  const [loginForm] = Form.useForm()
  const [registerForm] = Form.useForm()
  const [loading, setLoading] = useState(false)

  if (initializing) return null

  if (currentUser) {
    return <Navigate to="/home" replace/>
  }

  const handleLogin = async (values) => {
    setLoading(true)
    try {
      const result = await login(values.userId, values.password)
      if (result.success) {
        message.success('登录成功')
        navigate('/home')
      } else {
        message.error(result.message)
      }
    } finally {
      setLoading(false)
    }
  }

  const handleRegister = async (values) => {
    setLoading(true)
    try {
      const result = await register(values)
      if (result.success) {
        message.success('注册成功')
        navigate('/home')
      } else {
        message.error(result.message)
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-bg" />
      <Card className="login-card">
        <div className="login-header">
          <Title level={3} style={{ marginBottom: 4 }}>校园活动一站式服务平台</Title>
          <Paragraph type="secondary">
            发现精彩活动 · 智能推荐 · 一站式参与
          </Paragraph>
        </div>

        <Tabs
          items={[
            {
              key: 'login',
              label: '登录',
              children: (
                <Form form={loginForm} onFinish={handleLogin} size="large">
                  <Form.Item
                    name="userId"
                    rules={[{ required: true, message: '请输入学号/工号' }]}
                  >
                    <Input prefix={<UserOutlined />} placeholder="学号/工号" />
                  </Form.Item>
                  <Form.Item
                    name="password"
                    rules={[{ required: true, message: '请输入密码' }]}
                  >
                    <Input.Password prefix={<LockOutlined />} placeholder="密码" />
                  </Form.Item>
                  <Form.Item>
                    <Button type="primary" htmlType="submit" block loading={loading}>
                      登录
                    </Button>
                  </Form.Item>
                  <Form.Item style={{ marginBottom: 8 }}>
                    <Button
                      block
                      icon={<LoginOutlined />}
                      onClick={authApi.startJAccountLogin}
                    >
                      使用 jAccount 单点登录
                    </Button>
                  </Form.Item>
                  <Paragraph type="secondary" style={{ textAlign: 'center', marginBottom: 0 }}>
                    演示账号：524030910001 / 123456（学生）<br />
                    管理员：admin001 / 123456
                  </Paragraph>
                </Form>
              )
            },
            {
              key: 'register',
              label: '注册',
              children: (
                <Form form={registerForm} onFinish={handleRegister} layout="vertical" size="large">
                  <Form.Item
                    name="id"
                    label="学号/工号"
                    rules={[{ required: true, message: '请输入学号/工号' }]}
                  >
                    <Input placeholder="学号/工号" />
                  </Form.Item>
                  <Form.Item
                    name="password"
                    label="密码"
                    rules={[{ required: true, message: '请输入密码' }]}
                  >
                    <Input.Password placeholder="密码" />
                  </Form.Item>
                  <Form.Item
                    name="name"
                    label="姓名"
                    rules={[{ required: true, message: '请输入姓名' }]}
                  >
                    <Input placeholder="姓名" />
                  </Form.Item>
                  <Form.Item name="role" label="身份" initialValue="student">
                    <Select options={[
                      { label: '学生', value: 'student' },
                      { label: '教师', value: 'teacher' }
                    ]} />
                  </Form.Item>
                  <Form.Item
                    name="college"
                    label="学院"
                    rules={[{ required: true, message: '请选择学院' }]}
                  >
                    <Select placeholder="选择学院" options={COLLEGES.map(c => ({ label: c, value: c }))} />
                  </Form.Item>
                  <Form.Item
                    name="grade"
                    label="年级"
                    rules={[{ required: true, message: '请输入年级' }]}
                  >
                    <Input placeholder="如：2024级" />
                  </Form.Item>
                  <Form.Item name="interests" label="兴趣标签">
                    <Select
                      mode="multiple"
                      placeholder="选择兴趣标签"
                      options={INTEREST_TAGS.map(t => ({ label: t, value: t }))}
                    />
                  </Form.Item>
                  <Form.Item name="availableTime" label="可参与时间">
                    <Select
                      mode="multiple"
                      placeholder="选择可参与时间"
                      options={AVAILABLE_TIME_OPTIONS}
                    />
                  </Form.Item>
                  <Form.Item>
                    <Button type="primary" htmlType="submit" block loading={loading}>
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
