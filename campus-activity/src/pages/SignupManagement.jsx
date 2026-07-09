import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Table, Button, Space, Tag, Select, message, Spin, Result
} from 'antd'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { getMyActivities } from '../services/activityApi'
import { listRegistrations } from '../services/registrationApi'
import { formatDateTime } from '../data/mockData'

const STATUS_MAP = {
  pending: { text: '待审核', color: 'processing' },
  approved: { text: '已通过', color: 'success' },
  rejected: { text: '已拒绝', color: 'error' }
}

export default function SignupManagement() {
  const [searchParams, setSearchParams] = useSearchParams()
  const activityId = searchParams.get('activityId')
  const { reviewSignup } = useApp()
  const [activities, setActivities] = useState([])
  const [signups, setSignups] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadData = () => {
    setLoading(true)
    Promise.all([
      getMyActivities(),
      listRegistrations(activityId || undefined)
    ])
      .then(([acts, regs]) => {
        setActivities(acts)
        setSignups(regs)
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadData()
  }, [activityId])

  const filteredSignups = signups.map(s => ({
    key: s.id,
    signupId: s.id,
    studentId: s.userId,
    name: s.userName || '未知',
    college: s.college || '-',
    activityTitle: s.activityTitle || '-',
    status: s.status,
    createdAt: s.createdAt
  }))

  const handleReview = async (signupId, approved) => {
    try {
      await reviewSignup(signupId, approved)
      message.success(approved ? '已通过报名' : '已拒绝报名')
      loadData()
    } catch (err) {
      message.error(err.message || '操作失败')
    }
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
            options={activities.map(a => ({ label: a.title, value: a.id }))}
          />
        </Space>

        {loading ? (
          <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
        ) : error ? (
          <Result status="error" title="加载失败" subTitle={error} extra={
            <Button type="primary" onClick={loadData}>重试</Button>
          } />
        ) : (
          <Table columns={columns} dataSource={filteredSignups} pagination={{ pageSize: 10 }} />
        )}
      </MainLayout>
    </AuthGuard>
  )
}
