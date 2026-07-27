import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Col, InputNumber, message, Modal, Row, Space, Table, Tag, Typography } from 'antd'
import { ReloadOutlined, TeamOutlined } from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import AdminGuard from '../components/AdminGuard'
import {
  getClusteringRuns,
  getCommunityMembers,
  getLatestClustering,
  submitClusteringRun
} from '../services/communityClusteringApi'

const { Text } = Typography
const statusColors = { PENDING: 'gold', RUNNING: 'blue', SUCCESS: 'green', FAILED: 'red' }

export default function AdminCommunityClustering() {
  const [runs, setRuns] = useState({ items: [], page: 0, size: 10, totalElements: 0 })
  const [latest, setLatest] = useState(null)
  const [members, setMembers] = useState(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [clusterCount, setClusterCount] = useState(2)

  const load = useCallback(async (page = 0) => {
    setLoading(true)
    try {
      const [runPage, latestResult] = await Promise.all([
        getClusteringRuns(page, 10),
        getLatestClustering().catch(() => null)
      ])
      setRuns(runPage)
      setLatest(latestResult)
    } catch (err) {
      message.error(err.message || '聚类任务加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const openMembers = async communityId => {
    try {
      setMembers(await getCommunityMembers(communityId, 0, 20))
    } catch (err) {
      message.error(err.message || '成员加载失败')
    }
  }

  const submit = async () => {
    setSubmitting(true)
    try {
      await submitClusteringRun(clusterCount)
      message.success('聚类任务已提交')
      setModalOpen(false)
      await load(0)
    } catch (err) {
      message.error(err.message || '聚类任务提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  const runColumns = [
    { title: '版本', dataIndex: 'version', ellipsis: true },
    { title: 'K', dataIndex: 'clusterCount', width: 64 },
    { title: '样本', dataIndex: 'sampleCount', width: 80, render: value => value ?? '-' },
    { title: '状态', dataIndex: 'status', width: 100, render: value => <Tag color={statusColors[value]}>{value}</Tag> },
    { title: '创建时间', dataIndex: 'createdAt', render: value => new Date(value).toLocaleString() }
  ]

  const memberColumns = [
    { title: '用户', dataIndex: 'name' },
    { title: '学号/工号', dataIndex: 'userId' },
    { title: '学院', dataIndex: 'college' },
    { title: '年级', dataIndex: 'grade' },
    { title: '中心距离', dataIndex: 'distanceToCenter', render: value => value.toFixed(3) }
  ]

  return (
    <AuthGuard>
      <AdminGuard>
        <MainLayout title="社区聚类管理">
          <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
            <Col><Text type="secondary">提交任务、查看运行状态，并按社区审阅最小成员资料。</Text></Col>
            <Col><Space>
              <Button icon={<ReloadOutlined />} onClick={() => load(runs.page)}>刷新</Button>
              <Button type="primary" icon={<TeamOutlined />} onClick={() => setModalOpen(true)}>新建聚类</Button>
            </Space></Col>
          </Row>

          <Card title="运行历史" style={{ marginBottom: 24 }}>
            <Table
              rowKey="runId"
              loading={loading}
              columns={runColumns}
              dataSource={runs.items}
              pagination={{
                current: runs.page + 1,
                pageSize: runs.size,
                total: runs.totalElements,
                onChange: page => load(page - 1)
              }}
            />
          </Card>

          <Card title="最新成功版本的社区" style={{ marginBottom: 24 }}>
            <Space wrap>
              {(latest?.communities || []).map(community => (
                <Button key={community.communityId} onClick={() => openMembers(community.communityId)}>
                  <span className="community-swatch" style={{ background: community.color }} />
                  {community.name} · {community.memberCount} 人
                </Button>
              ))}
              {!latest?.communities?.length && <Text type="secondary">暂无成功结果</Text>}
            </Space>
          </Card>

          {members && (
            <Card title={`${members.community.name}成员`}>
              <Table rowKey="pointId" columns={memberColumns} dataSource={members.items} pagination={false} />
            </Card>
          )}

          <Modal title="新建社区聚类" open={modalOpen} onOk={submit} confirmLoading={submitting} onCancel={() => setModalOpen(false)}>
            <Space direction="vertical">
              <Text>社区数量 K</Text>
              <InputNumber min={2} max={20} value={clusterCount} onChange={value => setClusterCount(value || 2)} />
              <Text type="secondary">任务将异步执行，提交后可在运行历史中查看状态。</Text>
            </Space>
          </Modal>
        </MainLayout>
      </AdminGuard>
    </AuthGuard>
  )
}
