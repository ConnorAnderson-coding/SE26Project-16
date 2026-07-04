import { Table, Button, Space } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function OrganizerDashboard() {
  const navigate = useNavigate()

  const data = [
    {
      key: 1,
      title: 'AI技术讲座',
      signupCount: 85,
      status: '报名中'
    },
    {
      key: 2,
      title: '羽毛球比赛',
      signupCount: 43,
      status: '已结束'
    }
  ]

  const columns = [
    {
      title: '活动名称',
      dataIndex: 'title'
    },
    {
      title: '报名人数',
      dataIndex: 'signupCount'
    },
    {
      title: '状态',
      dataIndex: 'status'
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button
            onClick={() =>
              navigate(`/edit/${record.key}`)
            }
          >
            编辑
          </Button>

          <Button
            onClick={() =>
              navigate('/signup-management')
            }
          >
            审核报名
          </Button>
        </Space>
      )
    }
  ]

  return (
    <div className="page">
      <h1>活动管理</h1>

      <Button
        type="primary"
        style={{ marginBottom: 20 }}
        onClick={() => navigate('/create')}
      >
        创建活动
      </Button>

      <Table
        columns={columns}
        dataSource={data}
      />
    </div>
  )
}