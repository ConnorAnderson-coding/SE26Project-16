import { Card, Button } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function ActivityCard({ activity }) {
  const navigate = useNavigate()

  return (
    <Card
      title={activity.title}
      style={{ marginBottom: 20 }}
    >
      <p>地点：{activity.location}</p>
      <p>时间：{activity.time}</p>

      <Button
        type="primary"
        onClick={() =>
          navigate(`/activity/${activity.id}`)
        }
      >
        查看详情
      </Button>
    </Card>
  )
}