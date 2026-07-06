import { Card, Row, Col, Statistic, Table, Tag } from 'antd'
import {
  UserOutlined, CalendarOutlined, FormOutlined, CheckCircleOutlined
} from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { getCategoryLabel } from '../data/mockData'

export default function AdminDashboard() {
  const { users, activities, signups, checkIns, feedbacks } = useApp()

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
      <MainLayout title="管理后台">
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic title="注册用户" value={users.length} prefix={<UserOutlined />} />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic title="活动总数" value={activities.length} prefix={<CalendarOutlined />} />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic title="报名总数" value={signups.length} prefix={<FormOutlined />} />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic
                title="签到总数"
                value={checkIns.length}
                prefix={<CheckCircleOutlined />}
                valueStyle={{ color: '#52c41a' }}
              />
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col span={24}>
            <Card title="活动类别分布">
              <Row gutter={16}>
                {Object.entries(categoryStats).map(([cat, count]) => (
                  <Col key={cat}>
                    <Statistic title={getCategoryLabel(cat)} value={count} />
                  </Col>
                ))}
              </Row>
            </Card>
          </Col>
        </Row>

        <Card title="活动数据概览" extra={<Tag>{feedbacks.length} 条评价</Tag>}>
          <Table
            columns={columns}
            dataSource={activities.map(a => ({ ...a, key: a.id }))}
            pagination={{ pageSize: 8 }}
          />
        </Card>
      </MainLayout>
    </AuthGuard>
  )
}
