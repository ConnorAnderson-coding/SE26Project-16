import { Card, Input, Button } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function Login() {
  const navigate = useNavigate()

  return (
    <div
      style={{
        height: '100vh',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center'
      }}
    >
      <Card title="校园活动一站式服务平台" style={{ width: 400 }}>
        <Input placeholder="学号" />
        <br />
        <br />

        <Input.Password placeholder="密码" />
        <br />
        <br />

        <Button
          type="primary"
          block
          onClick={() => navigate('/home')}
        >
          登录
        </Button>
      </Card>
    </div>
  )
}