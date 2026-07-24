import { useEffect, useState } from 'react'
import { Table, Tag, Button, Space, Spin, Result } from 'antd'
import { useNavigate } from 'react-router-dom'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { getMyRegistrations } from '../services/registrationApi'
import { formatDateTime } from '../data/mockData'

const STATUS_MAP = {
  pending: { text: '待审核', color: 'processing' },
  approved: { text: '已通过', color: 'success' },
  rejected: { text: '已拒绝', color: 'error' }
}

export default function MyActivities() {
  const navigate = useNavigate()
  const [data, setData] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadData = () => {
    setLoading(true)
    getMyRegistrations()
      .then(regs => setData(regs.map(s => ({
        key: s.id,
        activityId: s.activityId,
        title: s.activityTitle || '未知活动',
        location: s.location || '-',
        startTime: s.startTime,
        status: s.status,
        createdAt: s.createdAt
      }))))
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadData()
  }, [])

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
        <Button type="link" onClick={() => navigate(`/activity/${record.activityId}`)}>
          查看详情
        </Button>
      )
    }
  ]

  return (
    <AuthGuard>
      <MainLayout title="我的报名">
        {loading ? (
          <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
        ) : error ? (
          <Result status="error" title="加载失败" subTitle={error} extra={
            <Button type="primary" onClick={loadData}>重试</Button>
          } />
        ) : (
          <Table columns={columns} dataSource={data} pagination={{ pageSize: 10 }} />
        )}
      </MainLayout>
    </AuthGuard>
  )
}
