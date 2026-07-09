import { useParams, useNavigate } from 'react-router-dom'
import {
  Card, Button, Tag, Space, Typography, Row, Col, Image, Divider,
  message, Descriptions, Empty
} from 'antd'
import {
  EnvironmentOutlined, ClockCircleOutlined, TeamOutlined,
  HeartOutlined, HeartFilled, UserOutlined, FireOutlined
} from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import {
  getCategoryLabel, formatDateRange, formatDateTime
} from '../data/mockData'

const { Title, Paragraph, Text } = Typography

const STATUS_MAP = {
  pending: { text: '待审核', color: 'processing' },
  approved: { text: '已通过', color: 'success' },
  rejected: { text: '已拒绝', color: 'error' }
}

export default function ActivityDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const {
    activities, signupActivity, toggleFavorite,
    getSignupStatus, isFavorited, feedbacks
  } = useApp()

  const activity = activities.find(a => a.id === id)
  if (!activity) {
    return (
      <AuthGuard>
        <MainLayout title="活动详情">
          <Empty description="活动不存在" />
        </MainLayout>
      </AuthGuard>
    )
  }

  const signupStatus = getSignupStatus(id)
  const favorited = isFavorited(id)
  const activityFeedbacks = feedbacks.filter(f => f.activityId === id)

  const handleSignup = () => {
    const result = signupActivity(id)
    result.success ? message.success(result.message) : message.warning(result.message)
  }

  const handleFavorite = () => {
    const result = toggleFavorite(id)
    if (result.success) {
      message.success(result.favorited ? '已收藏' : '已取消收藏')
    } else {
      message.warning(result.message)
    }
  }

  return (
    <AuthGuard>
      <MainLayout title="活动详情">
        <Row gutter={24}>
          <Col xs={24} lg={14}>
            <Card>
              {activity.poster && (
                <img
                  src={activity.poster}
                  alt={activity.title}
                  style={{ width: '100%', borderRadius: 8, marginBottom: 16, maxHeight: 320, objectFit: 'cover' }}
                />
              )}

              <Space wrap style={{ marginBottom: 12 }}>
                <Tag color="blue">{getCategoryLabel(activity.category)}</Tag>
                {activity.status === 'ended' && <Tag>已结束</Tag>}
                {activity.status === 'published' && <Tag color="green">报名中</Tag>}
                {(activity.tags || []).map(tag => (
                  <Tag key={tag}>{tag}</Tag>
                ))}
              </Space>

              <Title level={3}>{activity.title}</Title>

              <Descriptions column={1} style={{ marginBottom: 16 }}>
                <Descriptions.Item label={<><ClockCircleOutlined /> 活动时间</>}>
                  {formatDateRange(activity.startTime, activity.endTime)}
                </Descriptions.Item>
                <Descriptions.Item label={<><EnvironmentOutlined /> 活动地点</>}>
                  {activity.location}
                </Descriptions.Item>
                <Descriptions.Item label={<><UserOutlined /> 组织者</>}>
                  {activity.organizerName} · {activity.college}
                </Descriptions.Item>
                <Descriptions.Item label={<><TeamOutlined /> 报名情况</>}>
                  {activity.signupCount} / {activity.maxParticipants} 人
                </Descriptions.Item>
                <Descriptions.Item label={<><FireOutlined /> 收藏</>}>
                  {activity.favoriteCount} 人收藏
                </Descriptions.Item>
              </Descriptions>

              <Divider orientation="left">活动介绍</Divider>
              <Paragraph>{activity.description}</Paragraph>

              <Space wrap>
                <Button
                  type="primary"
                  size="large"
                  onClick={handleSignup}
                  disabled={!!signupStatus || activity.status === 'ended'}
                >
                  {signupStatus
                    ? STATUS_MAP[signupStatus]?.text
                    : activity.status === 'ended' ? '活动已结束' : '立即报名'}
                </Button>
                <Button
                  size="large"
                  icon={favorited ? <HeartFilled style={{ color: '#eb2f96' }} /> : <HeartOutlined />}
                  onClick={handleFavorite}
                >
                  {favorited ? '已收藏' : '收藏活动'}
                </Button>
                {signupStatus === 'approved' && activity.status !== 'ended' && (
                  <Button size="large" onClick={() => navigate('/checkin')}>
                    去签到
                  </Button>
                )}
                {activity.status === 'ended' && (
                  <Button size="large" onClick={() => navigate('/feedback')}>
                    去评价
                  </Button>
                )}
              </Space>
            </Card>
          </Col>

          <Col xs={24} lg={10}>
            {activity.record && (
              <Card title="活动记录" style={{ marginBottom: 16 }}>
                <Paragraph>{activity.record.summary}</Paragraph>
                <Image.PreviewGroup>
                  <Row gutter={[8, 8]}>
                    {activity.record.photos.map((photo, i) => (
                      <Col span={8} key={i}>
                        <Image src={photo} style={{ borderRadius: 4 }} />
                      </Col>
                    ))}
                  </Row>
                </Image.PreviewGroup>
                <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
                  发布于 {formatDateTime(activity.record.publishedAt)}
                </Paragraph>
              </Card>
            )}

            <Card title={`活动评价 (${activityFeedbacks.length})`}>
              {activityFeedbacks.length === 0 ? (
                <Empty description="暂无评价" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                activityFeedbacks.map(fb => (
                  <div key={fb.id} className="feedback-item">
                    <Space>
                      <Text strong>{fb.userName}</Text>
                      <Tag>{'★'.repeat(fb.rating)}</Tag>
                    </Space>
                    <Paragraph style={{ marginBottom: 4 }}>{fb.content}</Paragraph>
                    <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 12 }}>
                      {formatDateTime(fb.createdAt)}
                    </Paragraph>
                  </div>
                ))
              )}
            </Card>
          </Col>
        </Row>
      </MainLayout>
    </AuthGuard>
  )
}