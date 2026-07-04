import { Table } from 'antd'

export default function MyActivities() {

  const data = [
    {
      key: 1,
      title: 'AI讲座',
      status: '已通过'
    },
    {
      key: 2,
      title: '羽毛球比赛',
      status: '待审核'
    }
  ]

  return (
    <div className="page">

      <h1>我的活动</h1>

      <Table
        columns={[
          {
            title: '活动名称',
            dataIndex: 'title'
          },
          {
            title: '状态',
            dataIndex: 'status'
          }
        ]}
        dataSource={data}
      />

    </div>
  )
}