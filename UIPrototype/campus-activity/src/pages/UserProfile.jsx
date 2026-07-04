import { Card, Tag } from 'antd'

export default function UserProfile() {
  return (
    <div className="page">

      <Card title="个人中心">

        <p>姓名：张三</p>

        <p>学院：软件学院</p>

        <p>年级：2024级</p>

        <p>
          兴趣：
          <Tag>AI</Tag>
          <Tag>摄影</Tag>
          <Tag>羽毛球</Tag>
        </p>

      </Card>

    </div>
  )
}