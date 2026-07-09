import { useSearchParams } from 'react-router-dom'
import {
  Table, Button, Space, Tag, Select, message
} from 'antd'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { formatDateTime } from '../data/mockData'

const STATUS_MAP = {
  pending: { text: '待审核', color: 'processing' },
  approved: { text: '已通过', color: 'success' },
  rejected: { text: '已拒绝', color: 'error' }
}

export default function SignupManagement() {
  const [searchParams, setSearchParams] = useSearchParams()
  const activityId = searchParams.get('activityId')
  const { currentUser, activities, signups, users, reviewSignup } = useApp()

  const myActivityIds = activities
    .filter(a => a.organizerId === currentUser?.id)
    .map(a => a.id)

  const filteredSignups = signups
    .filter(s => myActivityIds.includes(s.activityId))
    .filter(s => !activityId || s.activityId === activityId)
    .map(s => {
      const user = users.find(u => u.id === s.userId)
      const activity = activities.find(a => a.id === s.activityId)
      return {
        key: s.id,
        signupId: s.id,
        studentId: s.userId,
        name: user?.name || '未知',
        college: user?.college || '-',
        activityTitle: activity?.title || '-',
        status: s.status,
        createdAt: s.createdAt
      }
    })

  const handleReview = (signupId, approved) => {
    reviewSignup(signupId, approved)
    message.success(approved ? '已通过报名' : '已拒绝报名')
  }

  const columns = [
    { title: '活动', dataIndex: 'activityTitle' },
    { title: '学号/工号', dataIndex: 'studentId' },
    { title: '姓名', dataIndex: 'name' },
    { title: '学院', dataIndex: 'college' },
    {
      title: '报名时间',
      dataIndex: 'createdAt',
      render: t => formatDateTime(t)
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: status => (
        <Tag color={STATUS_MAP[status]?.color}>{STATUS_MAP[status]?.text}</Tag>
      )
    },
    {
      title: '操作',
      render: (_, record) => record.status === 'pending' ? (
        <Space>
          <Button type="primary" size="small" onClick={() => handleReview(record.signupId, true)}>
            通过
          </Button>
          <Button danger size="small" onClick={() => handleReview(record.signupId, false)}>
            拒绝
          </Button>
        </Space>
      ) : (
        <Tag color={STATUS_MAP[record.status]?.color}>{STATUS_MAP[record.status]?.text}</Tag>
      )
    }
  ]

  return (
    <AuthGuard>
      <MainLayout title="报名审核">
        <Space style={{ marginBottom: 16 }}>
          <span>筛选活动：</span>
          <Select
            style={{ width: 280 }}
            placeholder="全部活动"
            allowClear
            value={activityId}
            onChange={val => setSearchParams(val ? { activityId: val } : {})}
            options={activities
              .filter(a => myActivityIds.includes(a.id))
              .map(a => ({ label: a.title, value: a.id }))}
          />
        </Space>

        <Table columns={columns} dataSource={filteredSignups} pagination={{ pageSize: 10 }} />
      </MainLayout>
    </AuthGuard>
  )
}
