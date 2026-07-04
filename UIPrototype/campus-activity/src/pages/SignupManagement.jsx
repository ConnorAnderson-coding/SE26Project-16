import { Table, Button, Space } from 'antd'

export default function SignupManagement() {

  const data = [
    {
      key: 1,
      studentId: '524030910001',
      name: '张三'
    },
    {
      key: 2,
      studentId: '524030910002',
      name: '李四'
    }
  ]

  const columns = [
    {
      title: '学号',
      dataIndex: 'studentId'
    },
    {
      title: '姓名',
      dataIndex: 'name'
    },
    {
      title: '操作',
      render: () => (
        <Space>
          <Button type="primary">
            通过
          </Button>

          <Button danger>
            拒绝
          </Button>
        </Space>
      )
    }
  ]

  return (
    <div className="page">
      <h1>报名审核</h1>

      <Table
        columns={columns}
        dataSource={data}
      />
    </div>
  )
}