import { Table } from 'antd'

export default function ActivityList() {
  const data = [
    {
      key: 1,
      title: 'AI讲座',
      location: '软件大楼'
    },
    {
      key: 2,
      title: '羽毛球比赛',
      location: '体育馆'
    }
  ]

  const columns = [
    {
      title: '活动名称',
      dataIndex: 'title'
    },
    {
      title: '地点',
      dataIndex: 'location'
    }
  ]

  return (
    <div className="page">
      <h1>活动列表</h1>
      <Table
        columns={columns}
        dataSource={data}
      />
    </div>
  )
}