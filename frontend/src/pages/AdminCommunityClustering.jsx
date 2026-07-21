import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Form,
  InputNumber,
  Space,
  Tag,
  Typography
} from 'antd'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/http'
import {
  getClusteringRun,
  getClusteringRuns,
  getCommunityMembers,
  getLatestClustering,
  triggerClustering
} from '../api/clustering'
import AuthGuard from '../components/AuthGuard'
import AdminGuard from '../components/AdminGuard'
import { useApp } from '../context/AppContext'
import MainLayout from '../layouts/MainLayout'
import { formatDateTime } from '../data/mockData'

const { Paragraph, Text } = Typography
const POLL_INTERVAL_MS = 2000
const PAGE_SIZE = 20
const ACTIVE_STATUSES = new Set(['PENDING', 'RUNNING'])

const STATUS_PRESENTATION = {
  PENDING: { color: 'gold', label: 'PENDING（等待执行）' },
  RUNNING: { color: 'processing', label: 'RUNNING（执行中）' },
  SUCCESS: { color: 'success', label: 'SUCCESS（已成功）' },
  FAILED: { color: 'error', label: 'FAILED（已失败）' }
}

const EMPTY_PAGE = {
  items: [],
  page: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0
}

function submissionErrorMessage(error) {
  const messages = {
    INVALID_CLUSTERING_REQUEST: '聚类请求格式错误，请检查输入。',
    NO_EFFECTIVE_USERS: '当前没有可用于聚类的有效用户。',
    INVALID_CLUSTER_COUNT: '聚类数量无效，请根据有效样本数量调整。',
    RUN_CONFLICT: '已存在正在处理的聚类任务，请等待现有任务结束。',
    CLUSTERING_SERVICE_UNAVAILABLE: '聚类执行功能当前关闭，历史社区查询仍可使用。',
    ACCESS_DENIED: '你没有触发社区聚类的权限。',
    CSRF_TOKEN_INVALID: '安全校验已失效，请重新提交；系统不会自动重放本次请求。',
    INTERNAL_ERROR: '系统暂时无法提交聚类任务。'
  }
  if (error instanceof ApiError && error.code === 'NETWORK_ERROR') {
    return '提交结果无法确认，请先检查运行历史，不要重复提交。'
  }
  return messages[error?.code] || (error instanceof ApiError ? error.message : '聚类任务提交失败。')
}

function queryErrorMessage(error, subject = '运行状态') {
  const messages = {
    ACCESS_DENIED: `你没有查询${subject}的权限。`,
    RUN_NOT_FOUND: '指定的聚类任务不存在。',
    INVALID_RUN_ID: '聚类任务标识无效。',
    INVALID_PAGE_REQUEST: '分页参数无效。',
    COMMUNITY_NOT_FOUND: '指定的社区不存在。',
    INVALID_COMMUNITY_ID: '社区标识无效。',
    NO_SUCCESSFUL_RUN: '当前还没有可查看的成功社区结果。',
    INTERNAL_ERROR: `系统暂时无法查询${subject}。`
  }
  if (error instanceof ApiError && error.code === 'NETWORK_ERROR') {
    return `${subject}查询暂时失败，可手动重试。`
  }
  return messages[error?.code] || (error instanceof ApiError ? error.message : `${subject}查询失败。`)
}

function StatusTag({ status }) {
  const presentation = STATUS_PRESENTATION[status] || {
    color: 'default',
    label: status || '未知状态'
  }
  return <Tag color={presentation.color}>{presentation.label}</Tag>
}

function RunDetails({ run }) {
  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
        <Descriptions.Item label="运行标识">{run.runId}</Descriptions.Item>
        <Descriptions.Item label="版本">{run.version}</Descriptions.Item>
        <Descriptions.Item label="算法">{run.algorithm}</Descriptions.Item>
        <Descriptions.Item label="聚类数量">{run.clusterCount}</Descriptions.Item>
        <Descriptions.Item label="随机种子">{run.randomState}</Descriptions.Item>
        <Descriptions.Item label="状态"><StatusTag status={run.status} /></Descriptions.Item>
        <Descriptions.Item label="样本数量">{run.sampleCount ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="特征模式版本">
          {run.featureSchemaVersion || '-'}
        </Descriptions.Item>
        <Descriptions.Item label="创建时间">{formatDateTime(run.createdAt)}</Descriptions.Item>
        <Descriptions.Item label="开始时间">{formatDateTime(run.startedAt)}</Descriptions.Item>
        <Descriptions.Item label="完成时间">{formatDateTime(run.finishedAt)}</Descriptions.Item>
        <Descriptions.Item label="触发者">{run.createdBy || '-'}</Descriptions.Item>
      </Descriptions>

      {run.status === 'SUCCESS' && run.metrics && (
        <Card size="small" title="聚类指标">
          <Descriptions size="small" column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label="Inertia">{run.metrics.inertia}</Descriptions.Item>
            <Descriptions.Item label="PCA 解释方差比例">
              {run.metrics.pcaExplainedVarianceRatio.join('、')}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {run.status === 'FAILED' && run.failure && (
        <Alert
          type="error"
          showIcon
          title={`失败代码：${run.failure.code}`}
          description={run.failure.message}
        />
      )}

      {run.status === 'SUCCESS' && (
        <Button type="primary"><Link to="/community">查看最新社区结果</Link></Button>
      )}
    </Space>
  )
}

function PageButtons({ page, totalPages, loading, onPage }) {
  const displayTotal = Math.max(totalPages, 1)
  return (
    <Space wrap>
      <Button
        disabled={loading || page <= 0}
        onClick={() => onPage(page - 1)}
      >
        上一页
      </Button>
      <Text>第 {page + 1} / {displayTotal} 页</Text>
      <Button
        disabled={loading || totalPages === 0 || page + 1 >= totalPages}
        onClick={() => onPage(page + 1)}
      >
        下一页
      </Button>
    </Space>
  )
}

function HistoryTable({ items, selectedRunId, onSelect }) {
  return (
    <div style={{ overflowX: 'auto' }}>
      <table aria-label="聚类运行历史" style={{ width: '100%', minWidth: 760, borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            <th scope="col">状态</th>
            <th scope="col">版本</th>
            <th scope="col">社区数</th>
            <th scope="col">样本数</th>
            <th scope="col">创建时间</th>
            <th scope="col">触发者</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          {items.map(item => (
            <tr key={item.runId} aria-current={selectedRunId === item.runId ? 'true' : undefined}>
              <td><StatusTag status={item.status} /></td>
              <td>{item.version}</td>
              <td>{item.clusterCount}</td>
              <td>{item.sampleCount ?? '-'}</td>
              <td>{formatDateTime(item.createdAt)}</td>
              <td>{item.createdBy || '-'}</td>
              <td>
                <Button
                  size="small"
                  type={selectedRunId === item.runId ? 'primary' : 'default'}
                  onClick={() => onSelect(item)}
                  aria-label={`选择运行 ${item.runId}`}
                >
                  查看
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function safeProfileValue(value) {
  return typeof value === 'string' && value.trim() ? value : '未提供'
}

function formatNumber(value) {
  return Number.isFinite(value) ? value.toFixed(2) : '-'
}

function MemberTable({ items }) {
  return (
    <div style={{ overflowX: 'auto' }}>
      <table aria-label="管理员社区成员" style={{ width: '100%', minWidth: 900, borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            <th scope="col">用户 ID</th>
            <th scope="col">姓名</th>
            <th scope="col">学院</th>
            <th scope="col">年级</th>
            <th scope="col">点标识</th>
            <th scope="col">X</th>
            <th scope="col">Y</th>
            <th scope="col">中心距离</th>
          </tr>
        </thead>
        <tbody>
          {items.map(member => (
            <tr key={member.pointId}>
              <td>{safeProfileValue(member.userId)}</td>
              <td>{safeProfileValue(member.name)}</td>
              <td>{safeProfileValue(member.college)}</td>
              <td>{safeProfileValue(member.grade)}</td>
              <td>{member.pointId}</td>
              <td>{formatNumber(member.x)}</td>
              <td>{formatNumber(member.y)}</td>
              <td>{formatNumber(member.distanceToCenter)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function AdminCommunityClustering() {
  const { currentUser } = useApp()
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const [polling, setPolling] = useState(false)
  const [run, setRun] = useState(null)
  const [runPath, setRunPath] = useState(null)
  const [selectedRunId, setSelectedRunId] = useState(null)
  const [history, setHistory] = useState(EMPTY_PAGE)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState(null)
  const [latest, setLatest] = useState(null)
  const [latestLoading, setLatestLoading] = useState(false)
  const [latestError, setLatestError] = useState(null)
  const [members, setMembers] = useState(null)
  const [membersLoading, setMembersLoading] = useState(false)
  const [membersError, setMembersError] = useState(null)
  const [selectedCommunityId, setSelectedCommunityId] = useState(null)
  const [submissionError, setSubmissionError] = useState(null)
  const [queryError, setQueryError] = useState(null)
  const [accepted, setAccepted] = useState(false)
  const [protocolWarning, setProtocolWarning] = useState(false)

  const pollingRef = useRef({ timer: null, controller: null, generation: 0 })
  const historyRef = useRef({ controller: null, generation: 0, page: 0 })
  const latestRef = useRef({ controller: null, generation: 0 })
  const membersRef = useRef({ controller: null, generation: 0 })
  const submissionControllerRef = useRef(null)
  const selectRunRef = useRef(null)
  const reloadHistoryRef = useRef(null)
  const reloadLatestRef = useRef(null)

  const stopPolling = useCallback(() => {
    const state = pollingRef.current
    if (state.timer !== null) clearTimeout(state.timer)
    state.controller?.abort()
    pollingRef.current = {
      timer: null,
      controller: null,
      generation: state.generation + 1
    }
    setPolling(false)
  }, [])

  const startRunRequest = useCallback((path, shouldPoll = false, initialDelay = 0) => {
    stopPolling()
    const generation = pollingRef.current.generation
    setPolling(shouldPoll)
    setQueryError(null)

    const poll = async () => {
      if (pollingRef.current.generation !== generation) return
      const controller = new AbortController()
      pollingRef.current.controller = controller
      try {
        const nextRun = await getClusteringRun(path, { signal: controller.signal })
        if (pollingRef.current.generation !== generation) return
        setRun(nextRun)
        setSelectedRunId(nextRun.runId)
        setQueryError(null)
        if (ACTIVE_STATUSES.has(nextRun.status)) {
          setPolling(true)
          pollingRef.current.timer = setTimeout(poll, POLL_INTERVAL_MS)
        } else {
          setPolling(false)
          pollingRef.current.timer = null
          pollingRef.current.controller = null
          if (shouldPoll) {
            reloadHistoryRef.current?.()
            if (nextRun.status === 'SUCCESS') reloadLatestRef.current?.()
          }
        }
      } catch (error) {
        if (error?.name === 'AbortError' || pollingRef.current.generation !== generation) return
        setPolling(false)
        setQueryError(queryErrorMessage(error))
        pollingRef.current.timer = null
        pollingRef.current.controller = null
      }
    }

    pollingRef.current.timer = setTimeout(poll, initialDelay)
  }, [stopPolling])

  const selectRun = useCallback((summary) => {
    const path = `/api/v1/admin/community-clustering/runs/${encodeURIComponent(summary.runId)}`
    setSelectedRunId(summary.runId)
    setRun(summary)
    setRunPath(path)
    startRunRequest(path, ACTIVE_STATUSES.has(summary.status))
  }, [startRunRequest])
  selectRunRef.current = selectRun

  const loadHistory = useCallback(async (page, { selectInitial = false } = {}) => {
    historyRef.current.controller?.abort()
    const controller = new AbortController()
    const generation = historyRef.current.generation + 1
    historyRef.current = { controller, generation, page }
    setHistoryLoading(true)
    setHistoryError(null)
    try {
      const response = await getClusteringRuns({ page, size: PAGE_SIZE, signal: controller.signal })
      if (historyRef.current.generation !== generation) return
      setHistory(response)
      if (selectInitial && response.items.length > 0) {
        const initial = response.items.find(item => ACTIVE_STATUSES.has(item.status))
          || response.items[0]
        selectRunRef.current?.(initial)
      }
    } catch (error) {
      if (error?.name === 'AbortError' || historyRef.current.generation !== generation) return
      setHistoryError(queryErrorMessage(error, '运行历史'))
    } finally {
      if (historyRef.current.generation === generation) {
        historyRef.current.controller = null
        setHistoryLoading(false)
      }
    }
  }, [])

  const loadLatest = useCallback(async () => {
    latestRef.current.controller?.abort()
    const controller = new AbortController()
    const generation = latestRef.current.generation + 1
    latestRef.current = { controller, generation }
    setLatestLoading(true)
    setLatestError(null)
    try {
      const response = await getLatestClustering({ signal: controller.signal })
      if (latestRef.current.generation !== generation) return
      setLatest(response)
    } catch (error) {
      if (error?.name === 'AbortError' || latestRef.current.generation !== generation) return
      setLatest(null)
      setLatestError(queryErrorMessage(error, '最新社区'))
    } finally {
      if (latestRef.current.generation === generation) {
        latestRef.current.controller = null
        setLatestLoading(false)
      }
    }
  }, [])

  const loadMembers = useCallback(async (communityId, page = 0) => {
    membersRef.current.controller?.abort()
    const controller = new AbortController()
    const generation = membersRef.current.generation + 1
    membersRef.current = { controller, generation }
    setSelectedCommunityId(communityId)
    setMembersLoading(true)
    setMembersError(null)
    try {
      const response = await getCommunityMembers({
        communityId,
        page,
        size: PAGE_SIZE,
        signal: controller.signal
      })
      if (membersRef.current.generation !== generation) return
      setMembers(response)
    } catch (error) {
      if (error?.name === 'AbortError' || membersRef.current.generation !== generation) return
      setMembers(null)
      setMembersError(queryErrorMessage(error, '社区成员'))
    } finally {
      if (membersRef.current.generation === generation) {
        membersRef.current.controller = null
        setMembersLoading(false)
      }
    }
  }, [])

  reloadHistoryRef.current = () => loadHistory(historyRef.current.page)
  reloadLatestRef.current = loadLatest

  useEffect(() => {
    if (currentUser?.role !== 'admin') return undefined
    loadHistory(0, { selectInitial: true })
    loadLatest()
    return () => {
      historyRef.current.controller?.abort()
      latestRef.current.controller?.abort()
    }
  }, [currentUser?.role, loadHistory, loadLatest])

  useEffect(() => () => {
    submissionControllerRef.current?.abort()
    historyRef.current.controller?.abort()
    latestRef.current.controller?.abort()
    membersRef.current.controller?.abort()
    stopPolling()
  }, [stopPolling])

  const handleSubmit = async ({ clusterCount }) => {
    if (submitting) return
    stopPolling()
    submissionControllerRef.current?.abort()
    const controller = new AbortController()
    submissionControllerRef.current = controller
    setSubmitting(true)
    setSubmissionError(null)
    setQueryError(null)
    setAccepted(false)
    setProtocolWarning(false)
    try {
      const submitted = await triggerClustering(clusterCount, { signal: controller.signal })
      setRun(submitted.run)
      setSelectedRunId(submitted.run.runId)
      setRunPath(submitted.runPath)
      setAccepted(true)
      setProtocolWarning(submitted.protocolWarning)
      await loadHistory(0)
      startRunRequest(submitted.runPath, true)
    } catch (error) {
      if (error?.name !== 'AbortError') {
        setSubmissionError(submissionErrorMessage(error))
      }
    } finally {
      if (submissionControllerRef.current === controller) {
        submissionControllerRef.current = null
        setSubmitting(false)
      }
    }
  }

  return (
    <AuthGuard>
      <AdminGuard>
        <MainLayout title="管理员社区聚类">
          <Paragraph type="secondary">
            页面从数据库运行历史恢复活动任务；刷新不会重新提交 POST。历史运行详情与最新成功社区分别查询，latest 不代表任意选中的历史运行。
          </Paragraph>

          <Card title="触发聚类" style={{ marginBottom: 24 }}>
            {submissionError && (
              <Alert
                type="error"
                showIcon
                title="任务提交失败"
                description={submissionError}
                style={{ marginBottom: 16 }}
              />
            )}
            <Form
              form={form}
              name="community-clustering-trigger"
              layout="inline"
              initialValues={{ clusterCount: 2 }}
              onFinish={handleSubmit}
            >
              <Form.Item
                name="clusterCount"
                label="社区数量"
                rules={[
                  { required: true, message: '请输入社区数量' },
                  {
                    validator: (_, value) => Number.isInteger(value) && value >= 2
                      ? Promise.resolve()
                      : Promise.reject(new Error('社区数量必须是至少为 2 的整数'))
                  }
                ]}
              >
                <InputNumber step={1} aria-label="社区数量" />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={submitting} disabled={submitting}>
                  提交聚类任务
                </Button>
              </Form.Item>
            </Form>
          </Card>

          {accepted && (
            <Alert
              type="info"
              showIcon
              title="聚类任务已接受"
              description="服务器已返回 202 Accepted；这不表示聚类已经完成。运行历史已刷新，页面会查询该任务直至终态。"
              style={{ marginBottom: 16 }}
            />
          )}
          {protocolWarning && (
            <Alert
              type="warning"
              showIcon
              title="响应地址未通过校验"
              description="页面已改用该运行标识对应的安全同源详情路径继续查询。"
              style={{ marginBottom: 16 }}
            />
          )}

          <Card title="运行历史" style={{ marginBottom: 24 }}>
            {historyError && (
              <Alert
                type="error"
                showIcon
                title="运行历史加载失败"
                description={historyError}
                action={<Button onClick={() => loadHistory(historyRef.current.page)}>重试运行历史</Button>}
                style={{ marginBottom: 16 }}
              />
            )}
            {historyLoading && <Paragraph>正在加载运行历史…</Paragraph>}
            {!historyLoading && !historyError && history.items.length === 0 && (
              <Paragraph type="secondary">暂无聚类运行。</Paragraph>
            )}
            {history.items.length > 0 && (
              <HistoryTable items={history.items} selectedRunId={selectedRunId} onSelect={selectRun} />
            )}
            <div style={{ marginTop: 16 }}>
              <PageButtons
                page={history.page}
                totalPages={history.totalPages}
                loading={historyLoading}
                onPage={page => loadHistory(page)}
              />
            </div>
          </Card>

          {queryError && (
            <Alert
              type="error"
              showIcon
              title="运行状态查询中断"
              description={queryError}
              action={runPath && (
                <Button onClick={() => startRunRequest(runPath, ACTIVE_STATUSES.has(run?.status))}>
                  手动重试查询
                </Button>
              )}
              style={{ marginBottom: 16 }}
            />
          )}

          {run && (
            <Card
              title="选中的聚类任务"
              extra={polling ? <Text type="secondary">正在串行轮询状态…</Text> : null}
              style={{ marginBottom: 24 }}
            >
              <RunDetails run={run} />
            </Card>
          )}

          <Card title="查看最新社区及成员（管理员内部信息）">
            <Alert
              type="warning"
              showIcon
              title="隐私提示"
              description="本区域只展示最新 SUCCESS 运行的社区。成员身份仅供管理员内部查看，不包含密码、角色、联系方式、关系或完整兴趣。"
              style={{ marginBottom: 16 }}
            />
            {latestLoading && <Paragraph>正在加载最新社区…</Paragraph>}
            {latestError && (
              <Alert
                type="info"
                showIcon
                title="最新社区暂不可用"
                description={latestError}
                action={<Button onClick={loadLatest}>重试最新社区</Button>}
                style={{ marginBottom: 16 }}
              />
            )}
            {latest?.communities?.length > 0 && (
              <Space wrap style={{ marginBottom: 16 }}>
                {latest.communities.map(community => (
                  <Button
                    key={community.communityId}
                    type={selectedCommunityId === community.communityId ? 'primary' : 'default'}
                    onClick={() => loadMembers(community.communityId, 0)}
                    aria-label={`查看社区成员 ${community.name}`}
                  >
                    {community.name}（簇 {community.clusterNo}，{community.memberCount} 人）
                  </Button>
                ))}
              </Space>
            )}
            {membersError && (
              <Alert
                type="error"
                showIcon
                title="社区成员加载失败"
                description={membersError}
                action={selectedCommunityId && (
                  <Button onClick={() => loadMembers(selectedCommunityId, members?.page || 0)}>
                    重试社区成员
                  </Button>
                )}
                style={{ marginBottom: 16 }}
              />
            )}
            {membersLoading && <Paragraph>正在加载社区成员…</Paragraph>}
            {members && (
              <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
                <Descriptions size="small" bordered column={{ xs: 1, md: 3 }}>
                  <Descriptions.Item label="社区名称">{members.community.name}</Descriptions.Item>
                  <Descriptions.Item label="簇编号">{members.community.clusterNo}</Descriptions.Item>
                  <Descriptions.Item label="成员总数">{members.community.memberCount}</Descriptions.Item>
                </Descriptions>
                {members.items.length === 0
                  ? <Paragraph type="secondary">本页没有成员。</Paragraph>
                  : <MemberTable items={members.items} />}
                <PageButtons
                  page={members.page}
                  totalPages={members.totalPages}
                  loading={membersLoading}
                  onPage={page => loadMembers(members.community.communityId, page)}
                />
              </Space>
            )}
          </Card>
        </MainLayout>
      </AdminGuard>
    </AuthGuard>
  )
}
