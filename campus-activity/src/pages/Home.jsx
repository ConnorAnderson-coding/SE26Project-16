import { useEffect, useState } from 'react'
import { Row, Col, Card, Typography, Tag, Statistic, Space, Empty, Spin, Result, Button } from 'antd'
import {
  CalendarOutlined,
  HeartOutlined,
  TeamOutlined,
  FireOutlined
} from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import ActivityCard from '../components/ActivityCard'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { getHomeStats } from '../services/homeApi'

const { Title, Paragraph } = Typography

export default function Home() {
  const { currentUser, getRecommendedActivities } = useApp()
  const [recommended, setRecommended] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadData = () => {
    setLoading(true)
    setError(null)
    Promise.all([getRecommendedActivities(), getHomeStats()])
      .then(([rec, st]) => {
        setRecommended(rec)
        setStats(st)
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadData()
  }, [])

  return (
    <AuthGuard>
      <MainLayout title="首页推荐">
        <div className="welcome-banner">
          <Title level={3}>你好，{currentUser?.name} 👋</Title>
          <Paragraph type="secondary">
            根据你的兴趣标签「{(currentUser?.interests || []).join('、') || '暂无'}」、
            可参与时间与社交关系，为你智能推荐以下活动
          </Paragraph>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
        ) : error ? (
          <Result status="error" title="加载失败" subTitle={error} extra={
            <Button type="primary" onClick={loadData}>重试</Button>
          } />
        ) : (
          <>
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
              <Col xs={12} sm={6}>
                <Card>
                  <Statistic title="我的报名" value={stats?.mySignupCount ?? 0} prefix={<CalendarOutlined />} />
                </Card>
              </Col>
              <Col xs={12} sm={6}>
                <Card>
                  <Statistic title="已通过" value={stats?.approvedCount ?? 0} prefix={<TeamOutlined />} valueStyle={{ color: '#52c41a' }} />
                </Card>
              </Col>
              <Col xs={12} sm={6}>
                <Card>
                  <Statistic title="我的收藏" value={stats?.myFavoriteCount ?? 0} prefix={<HeartOutlined />} valueStyle={{ color: '#eb2f96' }} />
                </Card>
              </Col>
              <Col xs={12} sm={6}>
                <Card>
                  <Statistic title="推荐活动" value={recommended.length} prefix={<FireOutlined />} valueStyle={{ color: '#fa8c16' }} />
                </Card>
              </Col>
            </Row>

            <Card
              title={
                <Space>
                  <FireOutlined style={{ color: '#fa541c' }} />
                  <span>智能推荐活动</span>
                </Space>
              }
              extra={<Tag color="processing">基于兴趣 · 时间 · 社交 · 热度</Tag>}
            >
              {recommended.length === 0 ? (
                <Empty description="暂无推荐活动" />
              ) : (
                <Row gutter={[16, 16]}>
                  {recommended.map(activity => (
                    <Col xs={24} sm={12} lg={8} key={activity.id}>
                      <ActivityCard
                        activity={activity}
                        showRecommend
                        recommendScore={activity.recommendScore}
                      />
                    </Col>
                  ))}
                </Row>
              )}
            </Card>
          </>
        )}
      </MainLayout>
    </AuthGuard>
  )
}
