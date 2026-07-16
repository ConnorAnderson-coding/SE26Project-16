import { useEffect, useState } from 'react'
import { Row, Col, Empty, Spin, Result, Button } from 'antd'
import MainLayout from '../layouts/MainLayout'
import ActivityCard from '../components/ActivityCard'
import AuthGuard from '../components/AuthGuard'
import { getMyFavorites } from '../services/favoriteApi'

export default function MyFavorites() {
  const [favoriteActivities, setFavoriteActivities] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadData = () => {
    setLoading(true)
    getMyFavorites()
      .then(setFavoriteActivities)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadData()
  }, [])

  return (
    <AuthGuard>
      <MainLayout title="我的收藏">
        {loading ? (
          <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
        ) : error ? (
          <Result status="error" title="加载失败" subTitle={error} extra={
            <Button type="primary" onClick={loadData}>重试</Button>
          } />
        ) : favoriteActivities.length === 0 ? (
          <Empty description="暂无收藏的活动" />
        ) : (
          <Row gutter={[16, 16]}>
            {favoriteActivities.map(activity => (
              <Col xs={24} sm={12} lg={8} key={activity.id}>
                <ActivityCard activity={activity} />
              </Col>
            ))}
          </Row>
        )}
      </MainLayout>
    </AuthGuard>
  )
}
