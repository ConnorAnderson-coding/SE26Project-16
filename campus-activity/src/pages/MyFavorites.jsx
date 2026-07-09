import { Row, Col, Empty } from 'antd'
import MainLayout from '../layouts/MainLayout'
import ActivityCard from '../components/ActivityCard'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'

export default function MyFavorites() {
  const { currentUser, favorites, activities } = useApp()

  const favoriteActivities = favorites
    .filter(f => f.userId === currentUser?.id)
    .map(f => activities.find(a => a.id === f.activityId))
    .filter(Boolean)

  return (
    <AuthGuard>
      <MainLayout title="我的收藏">
        {favoriteActivities.length === 0 ? (
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
