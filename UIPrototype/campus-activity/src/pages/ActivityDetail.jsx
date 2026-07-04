import { Card, Button } from 'antd'

export default function ActivityDetail() {
  return (
    <div className="page">

      <Card title="AI技术讲座">

        <p>时间：2026-07-15</p>

        <p>地点：软件大楼</p>

        <p>主办方：计算机学院</p>

        <p>
          本次讲座介绍大模型和软件工程的发展趋势。
        </p>

        <Button type="primary">
          报名
        </Button>

        <Button
          style={{ marginLeft: 10 }}
        >
          收藏
        </Button>

      </Card>

    </div>
  )
}