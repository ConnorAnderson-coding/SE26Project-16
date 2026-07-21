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
import { getClusteringRun, triggerClustering } from '../api/clustering'
import AuthGuard from '../components/AuthGuard'
import AdminGuard from '../components/AdminGuard'
import MainLayout from '../layouts/MainLayout'
import { formatDateTime } from '../data/mockData'

const { Paragraph, Text } = Typography
const POLL_INTERVAL_MS = 2000
const ACTIVE_STATUSES = new Set(['PENDING', 'RUNNING'])

const STATUS_PRESENTATION = {
  PENDING: { color: 'gold', label: 'PENDING（等待执行）' },
  RUNNING: { color: 'processing', label: 'RUNNING（执行中）' },
  SUCCESS: { color: 'success', label: 'SUCCESS（已成功）' },
  FAILED: { color: 'error', label: 'FAILED（已失败）' }
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
    return '提交结果无法确认，请先检查运行状态，不要重复提交。'
  }
  return messages[error?.code] || (error instanceof ApiError ? error.message : '聚类任务提交失败。')
}

function queryErrorMessage(error) {
  const messages = {
    ACCESS_DENIED: '你没有查询该聚类任务的权限。',
    RUN_NOT_FOUND: '指定的聚类任务不存在。',
    INVALID_RUN_ID: '聚类任务标识无效。',
    INTERNAL_ERROR: '系统暂时无法查询该聚类任务。'
  }
  if (error instanceof ApiError && error.code === 'NETWORK_ERROR') {
    return '运行状态查询暂时失败，可手动重试；系统不会重新提交任务。'
  }
  return messages[error?.code] || (error instanceof ApiError ? error.message : '运行状态查询失败。')
}

function RunDetails({ run }) {
  const status = STATUS_PRESENTATION[run.status] || {
    color: 'default',
    label: run.status || '未知状态'
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
        <Descriptions.Item label="运行标识">{run.runId}</Descriptions.Item>
        <Descriptions.Item label="版本">{run.version}</Descriptions.Item>
        <Descriptions.Item label="算法">{run.algorithm}</Descriptions.Item>
        <Descriptions.Item label="聚类数量">{run.clusterCount}</Descriptions.Item>
        <Descriptions.Item label="随机种子">{run.randomState}</Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag color={status.color}>{status.label}</Tag>
        </Descriptions.Item>
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

export default function AdminCommunityClustering() {
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const [polling, setPolling] = useState(false)
  const [run, setRun] = useState(null)
  const [runPath, setRunPath] = useState(null)
  const [submissionError, setSubmissionError] = useState(null)
  const [queryError, setQueryError] = useState(null)
  const [accepted, setAccepted] = useState(false)
  const [protocolWarning, setProtocolWarning] = useState(false)
  const pollingRef = useRef({ timer: null, controller: null, generation: 0 })
  const submissionControllerRef = useRef(null)

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

  const startPolling = useCallback((path, initialDelay = 0) => {
    stopPolling()
    const generation = pollingRef.current.generation
    setPolling(true)
    setQueryError(null)

    const poll = async () => {
      if (pollingRef.current.generation !== generation) return
      const controller = new AbortController()
      pollingRef.current.controller = controller
      try {
        const nextRun = await getClusteringRun(path, { signal: controller.signal })
        if (pollingRef.current.generation !== generation) return
        setRun(nextRun)
        setQueryError(null)
        if (ACTIVE_STATUSES.has(nextRun.status)) {
          pollingRef.current.timer = setTimeout(poll, POLL_INTERVAL_MS)
        } else {
          setPolling(false)
          pollingRef.current.timer = null
          pollingRef.current.controller = null
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

  useEffect(() => () => {
    submissionControllerRef.current?.abort()
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
      setRunPath(submitted.runPath)
      setAccepted(true)
      setProtocolWarning(submitted.protocolWarning)
      startPolling(submitted.runPath)
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
            提交后服务器会立即返回 PENDING；页面随后轮询当前运行，直至成功或失败。
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
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={submitting}
                  disabled={submitting}
                >
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
              description="服务器已返回 202 Accepted，当前状态为 PENDING；这不表示聚类已经完成。"
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
          {queryError && (
            <Alert
              type="error"
              showIcon
              title="运行状态查询中断"
              description={queryError}
              action={runPath && (
                <Button onClick={() => startPolling(runPath)}>手动重试查询</Button>
              )}
              style={{ marginBottom: 16 }}
            />
          )}

          {run && (
            <Card
              title="当前聚类任务"
              extra={polling ? <Text type="secondary">正在轮询状态…</Text> : null}
            >
              <RunDetails run={run} />
            </Card>
          )}
        </MainLayout>
      </AdminGuard>
    </AuthGuard>
  )
}
