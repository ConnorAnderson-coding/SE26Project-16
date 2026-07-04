import { Card } from 'antd'

export default function MyFavorites() {

  const favorites = [
    'AI技术讲座',
    '机器学习论坛',
    '程序设计竞赛'
  ]

  return (
    <div className="page">

      <h1>我的收藏</h1>

      {favorites.map(item => (
        <Card
          key={item}
          style={{ marginBottom: 12 }}
        >
          {item}
        </Card>
      ))}

    </div>
  )
}