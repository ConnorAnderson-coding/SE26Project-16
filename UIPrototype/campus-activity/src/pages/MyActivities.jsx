import { Table, Tag, Button, Space } from 'antd'
import { useNavigate } from 'react-router-dom'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { formatDateTime } from '../data/mockData'

const STATUS_MAP = {
  pending: { text: '待审核', color: 'processing' },
  approved: { text: '已通过', color: 'success' },
  rejected: { text: '已拒绝', color: 'error' }
}

export default function MyActivities() {
  const navigate = useNavigate()
  const { currentUser, signups, activities } = useApp()

  const data = signups
    .filter(s => s.userId === currentUser?.id)
    .map(s => {
      const activity = activities.find(a => a.id === s.activityId)
      return {
        key: s.id,
        activityId: s.activityId,
        title: activity?.title || '未知活动',
        location: activity?.location || '-',
        startTime: activity?.startTime,
        status: s.status,
        createdAt: s.createdAt
      }
    })

  const columns = [
    { title: '活动名称', dataIndex: 'title' },
    { title: '地点', dataIndex: 'location' },
    {
      title: '活动时间',
      dataIndex: 'startTime',
      render: t => formatDateTime(t)
    },
    {
      title: '报名时间',
      dataIndex: 'createdAt',
      render: t => formatDateTime(t)
    },
    {
      title: '报名状态',
      dataIndex: 'status',
      render: status => (
        <Tag color={STATUS_MAP[status]?.color}>{STATUS_MAP[status]?.text}</Tag>
      )
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => navigate(`/activity/${record.activityId}`)}>
            查看详情
          </Button>
          {record.status === 'approved' && (
            <Button type="link" onClick={() => navigate('/checkin')}>
              签到
            </Button>
          )}
        </Space>
      )
    }
  ]

  return (
    <AuthGuard>
      <MainLayout title="我的报名">
        <Table columns={columns} dataSource={data} pagination={{ pageSize: 10 }} />
      </MainLayout>
    </AuthGuard>
  )
}
