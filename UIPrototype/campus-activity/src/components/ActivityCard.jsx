import { Card, Tag, Button, Space, Typography } from 'antd'
import {
  EnvironmentOutlined,
  ClockCircleOutlined,
  FireOutlined,
  TeamOutlined
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { getCategoryLabel, formatDateTime } from '../data/mockData'

const { Text, Paragraph } = Typography

export default function ActivityCard({ activity, showRecommend = false, recommendScore }) {
  const navigate = useNavigate()

  return (
    <Card
      hoverable
      className="activity-card"
      cover={
        activity.poster ? (
          <div className="activity-card-cover">
            <img src={activity.poster} alt={activity.title} />
            {showRecommend && recommendScore > 0 && (
              <Tag color="volcano" className="recommend-tag">
                智能推荐
              </Tag>
            )}
          </div>
        ) : null
      }
      onClick={() => navigate(`/activity/${activity.id}`)}
    >
      <Space direction="vertical" size={8} style={{ width: '100%' }}>
        <Space wrap>
          <Tag color="blue">{getCategoryLabel(activity.category)}</Tag>
          {activity.status === 'ended' && <Tag>已结束</Tag>}
        </Space>

        <Text strong style={{ fontSize: 16 }}>{activity.title}</Text>

        <Paragraph
          type="secondary"
          ellipsis={{ rows: 2 }}
          style={{ marginBottom: 0 }}
        >
          {activity.description}
        </Paragraph>

        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <Text type="secondary">
            <ClockCircleOutlined /> {formatDateTime(activity.startTime)}
          </Text>
          <Text type="secondary">
            <EnvironmentOutlined /> {activity.location}
          </Text>
          <Space>
            <Text type="secondary">
              <TeamOutlined /> {activity.signupCount}/{activity.maxParticipants} 人
            </Text>
            <Text type="secondary">
              <FireOutlined /> {activity.favoriteCount} 收藏
            </Text>
          </Space>
        </Space>

        <Button
          type="primary"
          block
          onClick={(e) => {
            e.stopPropagation()
            navigate(`/activity/${activity.id}`)
          }}
        >
          查看详情
        </Button>
      </Space>
    </Card>
  )
}
