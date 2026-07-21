import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Flex,
  Row,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  Tooltip
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { ApiError } from '../api/http'
import { getLatestClustering, getMyClustering } from '../api/clustering'
import AuthGuard from '../components/AuthGuard'
import MainLayout from '../layouts/MainLayout'
import { formatDateTime } from '../data/mockData'
import { useApp } from '../context/AppContext'
import { getCommunityClusters, getUsers } from '../services/mock/mockApi'

const { Paragraph, Text, Title } = Typography
const SAFE_COLOR = '#1677ff'
const HEX_COLOR = /^#[0-9a-f]{6}$/i

function safeColor(value) {
  return typeof value === 'string' && HEX_COLOR.test(value) ? value : SAFE_COLOR
}

function initialResource() {
  return { status: 'loading', data: null, error: null }
}

function resultState(result, previous) {
  if (result.status === 'fulfilled') {
    return { status: 'success', data: result.value, error: null }
  }
  if (result.reason?.name === 'AbortError') return previous
  if (result.reason instanceof ApiError && result.reason.code === 'NO_SUCCESSFUL_RUN') {
    return { status: 'empty', data: null, error: null }
  }
  return {
    status: 'error',
    data: previous.data,
    error: result.reason instanceof ApiError
      ? result.reason.message
      : '社区聚类数据加载失败'
  }
}

function CommunityScatter({ communities }) {
  return (
    <svg
      className="community-scatter"
      viewBox="0 0 100 100"
      role="img"
      aria-label="社区聚类匿名散点图，带描边的大圆点表示当前用户"
    >
      <rect x="0" y="0" width="100" height="100" rx="2" fill="#fafafa" />
      {communities.flatMap(community => community.points.map(point => {
        const color = safeColor(community.color)
        return (
          <circle
            key={point.pointId}
            cx={point.x}
            cy={point.y}
            r={point.currentUser ? 2.3 : 1.45}
            fill={color}
            stroke={point.currentUser ? '#111827' : '#ffffff'}
            strokeWidth={point.currentUser ? 0.8 : 0.35}
            data-current-user={point.currentUser ? 'true' : 'false'}
          >
            <title>
              {point.currentUser ? '当前用户匿名点' : '匿名社区点'}，坐标 {point.x}，{point.y}
            </title>
          </circle>
        )
      }))}
    </svg>
  )
}

function MembershipCard({ resource }) {
  if (resource.status === 'loading' && !resource.data) {
    return <Spin description="正在加载个人归属" />
  }
  if (resource.status === 'empty') {
    return <Empty description="当前还没有可用的社区聚类结果" />
  }
  if (resource.status === 'error' && !resource.data) {
    return <Alert type="error" showIcon title="个人归属加载失败" description={resource.error} />
  }

  const membership = resource.data?.membership
  if (!membership) {
    return <Empty description="当前用户未进入本次聚类样本" />
  }
  const color = safeColor(membership.color)
  return (
    <div>
      <Title level={4} style={{ marginTop: 0 }}>
        你属于 <Tag color={color}>{membership.communityName}</Tag> 社区
      </Title>
      <Descriptions size="small" column={{ xs: 1, sm: 2 }}>
        <Descriptions.Item label="社区编号">{membership.clusterNo}</Descriptions.Item>
        <Descriptions.Item label="坐标">({membership.x}, {membership.y})</Descriptions.Item>
        <Descriptions.Item label="与中心距离">
          {membership.distanceToCenter}
        </Descriptions.Item>
      </Descriptions>
    </div>
  )
}

function LatestClustering({ resource }) {
  if (resource.status === 'loading' && !resource.data) {
    return (
      <Flex justify="center" style={{ padding: 48 }}>
        <Spin description="正在加载社区结果" size="large" />
      </Flex>
    )
  }
  if (resource.status === 'empty') {
    return (
      <Empty
        description={
          <span>当前还没有可用的社区聚类结果，管理员运行聚类后可查看。</span>
        }
      />
    )
  }
  if (resource.status === 'error' && !resource.data) {
    return <Alert type="error" showIcon title="社区结果加载失败" description={resource.error} />
  }

  const { run, communities } = resource.data
  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {resource.status === 'error' && (
        <Alert type="warning" showIcon title="刷新失败" description={resource.error} />
      )}
      <Descriptions bordered size="small" column={{ xs: 1, sm: 2, lg: 5 }}>
        <Descriptions.Item label="运行版本">{run.version}</Descriptions.Item>
        <Descriptions.Item label="算法">{run.algorithm}</Descriptions.Item>
        <Descriptions.Item label="社区数量">{run.clusterCount}</Descriptions.Item>
        <Descriptions.Item label="样本数量">{run.sampleCount}</Descriptions.Item>
        <Descriptions.Item label="完成时间">{formatDateTime(run.finishedAt)}</Descriptions.Item>
      </Descriptions>

      <Card title="匿名社区分布">
        <CommunityScatter communities={communities} />
        <Space wrap style={{ marginTop: 16 }}>
          {communities.map(community => (
            <Tag key={community.communityId} color={safeColor(community.color)}>
              {community.name}（{community.memberCount} 人）
            </Tag>
          ))}
        </Space>
      </Card>

      <Row gutter={[16, 16]}>
        {communities.map(community => (
          <Col xs={24} lg={12} key={community.communityId}>
            <Card
              title={<Tag color={safeColor(community.color)}>{community.name}</Tag>}
              extra={`${community.memberCount} 人`}
              style={{ height: '100%' }}
            >
              <Paragraph>{community.description || '暂无社区描述'}</Paragraph>
              <Text type="secondary">代表性兴趣：</Text>
              <Space wrap style={{ marginTop: 8 }}>
                {community.topInterests.length > 0
                  ? community.topInterests.map(interest => <Tag key={interest}>{interest}</Tag>)
                  : <Text type="secondary">暂无</Text>}
              </Space>
            </Card>
          </Col>
        ))}
      </Row>
    </Space>
  )
}

export default function CommunityClusters() {
  const { authStatus } = useApp()
  const [latest, setLatest] = useState(initialResource)
  const [membership, setMembership] = useState(initialResource)
  const requestRef = useRef({ controller: null, sequence: 0 })

  const load = useCallback(async () => {
    requestRef.current.controller?.abort()
    const controller = new AbortController()
    const sequence = requestRef.current.sequence + 1
    requestRef.current = { controller, sequence }
    setLatest(previous => ({ ...previous, status: 'loading', error: null }))
    setMembership(previous => ({ ...previous, status: 'loading', error: null }))

    const results = await Promise.allSettled([
      getLatestClustering({ signal: controller.signal }),
      getMyClustering({ signal: controller.signal })
    ])
    if (requestRef.current.sequence !== sequence || controller.signal.aborted) return
    setLatest(previous => resultState(results[0], previous))
    setMembership(previous => resultState(results[1], previous))
  }, [])

  useEffect(() => {
    if (authStatus !== 'authenticated') return undefined
    load()
    return () => requestRef.current.controller?.abort()
  }, [authStatus, load])

  const loading = latest.status === 'loading' || membership.status === 'loading'

  return (
    <AuthGuard>
      <MainLayout title="社区聚类">
        <Flex justify="space-between" align="center" gap={16} wrap style={{ marginBottom: 16 }}>
          <Paragraph type="secondary" style={{ margin: 0 }}>
            查看最新聚类版本的匿名社区分布和你自己的社区归属。
          </Paragraph>
          <Button
            icon={<ReloadOutlined />}
            onClick={load}
            loading={loading}
            disabled={loading}
          >
            刷新
          </Button>
        </Flex>

        <Card title="我的社区" style={{ marginBottom: 24 }}>
          <MembershipCard resource={membership} />
        </Card>

        <Card title="最新社区结果">
          <LatestClustering resource={latest} />
        </Card>
      </MainLayout>
    </AuthGuard>
  )
}
