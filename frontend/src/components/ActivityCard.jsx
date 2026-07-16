import { Card, Tag, Button, Space, Typography } from 'antd'
import {
  EnvironmentOutlined,
  ClockCircleOutlined,
  FireOutlined,
  HeartOutlined,
  TeamOutlined
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { getCategoryLabel, formatDateTime } from '../data/mockData'
import { formatHotness, getHotnessValue } from '../utils/hotness'

const { Text, Paragraph } = Typography

const REASON_COLORS = {
  兴趣匹配: 'green',
  内容相似: 'blue',
  社交相关: 'purple',
  热门活动: 'volcano',
  时间合适: 'cyan',
  热门推荐: 'orange'
}

export default function ActivityCard({
  activity,
  showRecommend = false,
  recommendScore,
  recommendReasons
}) {
  const navigate = useNavigate()
  const reasons = recommendReasons ?? activity?.recommendReasons ?? []
  const hasRecommend = showRecommend && (recommendScore > 0 || reasons.length > 0)

  return (
    <Card
      hoverable
      className="activity-card"
      cover={
        activity.poster ? (
          <div className="activity-card-cover">
            <img src={activity.poster} alt={activity.title} />
            {hasRecommend && (
              <Tag color="volcano" className="recommend-tag">
                智能推荐
                {recommendScore > 0 ? ` ${recommendScore}` : ''}
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
          {hasRecommend && reasons.map(reason => (
            <Tag key={reason} color={REASON_COLORS[reason] || 'default'}>
              {reason}
            </Tag>
          ))}
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
          <Space wrap>
            <Text type="secondary">
              <TeamOutlined /> {activity.signupCount}/{activity.maxParticipants} 人
            </Text>
            <Text type="secondary">
              <HeartOutlined /> {activity.favoriteCount ?? 0} 收藏
            </Text>
            <Text type="secondary">
              <FireOutlined style={{ color: '#fa541c' }} /> 热度 {formatHotness(getHotnessValue(activity))}
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
