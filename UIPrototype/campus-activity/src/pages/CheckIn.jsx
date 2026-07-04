import { Card, Button } from 'antd'

export default function CheckIn() {
  return (
    <div className="page">

      <Card title="活动签到">

        <div
          style={{
            width: 240,
            height: 240,
            background: '#eee',
            marginBottom: 20,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}
        >
          二维码区域
        </div>

        <Button type="primary">
          模拟签到
        </Button>

      </Card>

    </div>
  )
}