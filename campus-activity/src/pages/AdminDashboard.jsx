import { useEffect, useState } from 'react'
import { Card, Row, Col, Statistic, Table, Tag, Spin } from 'antd'
import {
  UserOutlined, CalendarOutlined, FormOutlined, CheckCircleOutlined
} from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import AdminGuard from '../components/AdminGuard'
import { useApp } from '../context/AppContext'
import { getAllActivities } from '../services/activityApi'
import { getUsers } from '../services/mock/mockApi'
import { getCategoryLabel } from '../data/mockData'

export default function AdminDashboard() {
  const { checkIns } = useApp()
  const [activities, setActivities] = useState([])
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([getAllActivities(), getUsers()])
      .then(([acts, usrs]) => {
        setActivities(acts)
        setUsers(usrs)
      })
      .finally(() => setLoading(false))
  }, [])

  const signups = activities.reduce((sum, a) => sum + (a.signupCount || 0), 0)
  const feedbacks = []

  const categoryStats = activities.reduce((acc, a) => {
    acc[a.category] = (acc[a.category] || 0) + 1
    return acc
  }, {})

  const columns = [
    { title: '活动名称', dataIndex: 'title' },
    {
      title: '类别',
      dataIndex: 'category',
      render: c => getCategoryLabel(c)
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: s => (
        <Tag color={s === 'published' ? 'green' : 'default'}>
          {s === 'published' ? '进行中' : '已结束'}
        </Tag>
      )
    },
    { title: '报名', dataIndex: 'signupCount' },
    { title: '收藏', dataIndex: 'favoriteCount' }
  ]

  return (
    <AuthGuard>
      <AdminGuard>
        <MainLayout title="管理后台">
          {loading ? (
            <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
          ) : (
            <>
              <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                <Col xs={12} sm={6}>
                  <Card><Statistic title="注册用户" value={users.length} prefix={<UserOutlined />} /></Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card><Statistic title="活动总数" value={activities.length} prefix={<CalendarOutlined />} /></Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card><Statistic title="报名总数" value={signups} prefix={<FormOutlined />} /></Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card><Statistic title="签到记录" value={checkIns.length} prefix={<CheckCircleOutlined />} /></Card>
                </Col>
              </Row>

              <Card title="活动类别分布" style={{ marginBottom: 24 }}>
                <Row gutter={[8, 8]}>
                  {Object.entries(categoryStats).map(([cat, count]) => (
                    <Col key={cat}>
                      <Tag>{getCategoryLabel(cat)}：{count}</Tag>
                    </Col>
                  ))}
                </Row>
              </Card>

              <Card title="全部活动">
                <Table
                  columns={columns}
                  dataSource={activities.map(a => ({ ...a, key: a.id }))}
                  pagination={{ pageSize: 10 }}
                />
              </Card>
            </>
          )}
        </MainLayout>
      </AdminGuard>
    </AuthGuard>
  )
}
