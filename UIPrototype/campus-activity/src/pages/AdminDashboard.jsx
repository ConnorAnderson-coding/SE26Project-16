import { Card, Row, Col, Statistic } from 'antd'

export default function AdminDashboard() {
  return (
    <div className="page">

      <h1>管理员后台</h1>

      <Row gutter={16}>

        <Col span={6}>
          <Card>
            <Statistic
              title="用户数"
              value={1200}
            />
          </Card>
        </Col>

        <Col span={6}>
          <Card>
            <Statistic
              title="活动数"
              value={356}
            />
          </Card>
        </Col>

        <Col span={6}>
          <Card>
            <Statistic
              title="报名数"
              value={5832}
            />
          </Card>
        </Col>

        <Col span={6}>
          <Card>
            <Statistic
              title="签到数"
              value={4721}
            />
          </Card>
        </Col>

      </Row>

    </div>
  )
}