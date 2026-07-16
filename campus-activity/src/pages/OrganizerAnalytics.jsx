import { useEffect, useState } from 'react'
import {
  Card, Row, Col, Statistic, Select, Spin, Result, Button,
  Tag, Typography, List, Space, Empty, Alert
} from 'antd'
import {
  EyeOutlined, RiseOutlined, TeamOutlined, StarOutlined,
  BulbOutlined, BarChartOutlined, LineChartOutlined,
  HeartOutlined, CheckCircleOutlined, FormOutlined, CommentOutlined,
  ReloadOutlined, LoadingOutlined
} from '@ant-design/icons'
import dayjs from 'dayjs'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { getMyActivities } from '../services/activityApi'
import { getFullAnalysis, triggerAnalysis, pollAnalysisUntilReady } from '../services/analyticsApi'

const { Text } = Typography

const CATEGORY_LABELS = {
  promotion: '宣传推广', schedule: '时间安排',
  venue: '场地设施', content: '内容质量', other: '其他'
}
const CATEGORY_COLORS = {
  promotion: 'blue', schedule: 'purple',
  venue: 'orange', content: 'green', other: 'default'
}
const PRIORITY_COLORS = { high: 'red', medium: 'orange', low: 'default' }

/* ==================== 内联图表组件 ==================== */

/** 评分分布柱状图 */
function RatingBarChart({ distribution }) {
  if (!distribution) return <Empty description="暂无评分数据" />
  const entries = Object.entries(distribution).sort(([a], [b]) => Number(a) - Number(b))
  const maxCount = Math.max(...entries.map(([, c]) => c), 1)

  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, height: 160, padding: '8px 0' }}>
      {entries.map(([star, count]) => (
        <div key={star} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
          <Text style={{ fontSize: 12, marginBottom: 4 }}>{count}</Text>
          <div style={{
            width: '100%', maxWidth: 48,
            height: `${Math.max((count / maxCount) * 120, 4)}px`,
            background: 'linear-gradient(180deg, #1677ff, #69b1ff)',
            borderRadius: '4px 4px 0 0',
            transition: 'height 0.3s'
          }} />
          <Text style={{ fontSize: 12, marginTop: 6 }}>{star}★</Text>
        </div>
      ))}
    </div>
  )
}

/**
 * 报名趋势折线图
 * <p>
 * - 单日活动：唯一点画在 x=50 处，避免 0/0 -> NaN 导致 SVG 失效
 * - 多日活动：采样 X 轴标签（最多 6 个），避免日期过多时文字重叠
 */
function SignupTrendLineChart({ trend }) {
  if (!trend || Object.keys(trend).length === 0) {
    return <Empty description="暂无趋势数据" />
  }
  const days = Object.keys(trend)
  const values = Object.values(trend)
  const maxVal = Math.max(...values, 1)

  // 单点数据：放在 x=50，避免除零
  const points = values.map((v, i) => {
    const x = days.length === 1 ? 50 : (i / (days.length - 1)) * 100
    const y = 100 - (v / maxVal) * 100
    return { x, y }
  })
  const linePath = points.map((p, i) =>
    `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`
  ).join(' ')

  // X 轴标签采样：日期过多时只显示首/末 + 中间均布抽样，避免文字重叠
  const sampledLabels = sampleLabels(days, 6)

  return (
    <div style={{ position: 'relative', height: 180 }}>
      <svg viewBox="0 0 100 100" style={{ width: '100%', height: 160 }} preserveAspectRatio="none">
        {/* 网格线 */}
        {[0, 25, 50, 75, 100].map(y => (
          <line key={y} x1="0" y1={y} x2="100" y2={y} stroke="#f0f0f0" strokeWidth="0.5" />
        ))}
        {/* 折线 */}
        <path d={linePath} fill="none" stroke="#1677ff" strokeWidth="1.5" />
        {/* 填充区域 */}
        {days.length > 1 && (
          <path d={`${linePath} L100,100 L0,100 Z`} fill="url(#trendGrad)" opacity="0.15" />
        )}
        <defs>
          <linearGradient id="trendGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#1677ff" />
            <stop offset="100%" stopColor="#1677ff" stopOpacity="0" />
          </linearGradient>
        </defs>
        {/* 数据点 */}
        {points.map((p, i) => (
          <circle key={i} cx={p.x} cy={p.y} r="2" fill="#1677ff" stroke="#fff" strokeWidth="1" />
        ))}
      </svg>
      {/* X 轴标签 */}
      <div style={{ position: 'relative', height: 18, padding: '0 4px' }}>
        {sampledLabels.map(({ index, label }) => (
          <Text
            key={index}
            type="secondary"
            style={{
              position: 'absolute',
              fontSize: 11,
              left: days.length === 1
                ? '50%'
                : `${(index / (days.length - 1)) * 100}%`,
              transform: days.length === 1
                ? 'translateX(-50%)'
                : (index === 0
                    ? 'translateX(0)'
                    : (index === days.length - 1
                        ? 'translateX(-100%)'
                        : 'translateX(-50%)')),
              whiteSpace: 'nowrap'
            }}
          >
            {label}
          </Text>
        ))}
      </div>
      {/* 多日时显示提示：当前显示 X/{total} 个标签 */}
      {days.length > 6 && (
        <Text type="secondary" style={{ fontSize: 11, display: 'block', textAlign: 'right', marginTop: 2 }}>
          显示 {sampledLabels.length}/{days.length} 个日期
        </Text>
      )}
    </div>
  )
}

/**
 * 对日期数组做等距抽样，最多返回 maxCount 个标签。
 * <p>
 * 始终保留首尾，中间按步长抽取。
 */
function sampleLabels(days, maxCount) {
  const n = days.length
  if (n <= maxCount) {
    return days.map((label, index) => ({ index, label }))
  }
  const step = (n - 1) / (maxCount - 1)
  const result = []
  for (let i = 0; i < maxCount; i++) {
    const idx = Math.round(i * step)
    if (!result.some(r => r.index === idx)) {
      result.push({ index: idx, label: days[idx] })
    }
  }
  // 兜底：若因 round 重复导致数量不足，补齐首尾
  if (result[0].index !== 0) result.unshift({ index: 0, label: days[0] })
  if (result[result.length - 1].index !== n - 1) result.push({ index: n - 1, label: days[n - 1] })
  return result
}

/* ==================== 主页面 ==================== */

/**
 * 判断活动是否已结束（用于过滤下拉列表）
 * <p>
 * 满足任一条件即视为已结束：
 * <ol>
 *   <li>状态字段为 "ended"（组织者已发布活动记录）</li>
 *   <li>活动的 endTime 已经早于当前时间</li>
 * </ol>
 */
const isActivityEnded = (activity) => {
  if (!activity) return false
  if (activity.status === 'ended') return true
  if (activity.endTime && new Date(activity.endTime) < new Date()) return true
  return false
}

export default function OrganizerAnalytics() {
  const [myActivities, setMyActivities] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [metrics, setMetrics] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [suggestions, setSuggestions] = useState(null)
  const [suggestionSource, setSuggestionSource] = useState('none')
  const [generatedAt, setGeneratedAt] = useState(null)
  const [triggering, setTriggering] = useState(false)

  useEffect(() => {
    getMyActivities()
      .then(activities => {
        setMyActivities(activities)
        const firstEnded = activities.find(isActivityEnded)
        setSelectedId(firstEnded?.id || null)
        if (!firstEnded) setLoading(false)
      })
      .catch(err => {
        setError(err.message)
        setLoading(false)
      })
  }, [])

  useEffect(() => {
    if (myActivities.length && !selectedId) {
      setSelectedId(myActivities.find(isActivityEnded)?.id || null)
    }
  }, [myActivities, selectedId])

  useEffect(() => {
    if (!selectedId) return
    setLoading(true)
    setError(null)
    // 默认展示数据库中已保存的建议（含 LLM 与规则模板来源）
    getFullAnalysis(selectedId)
      .then(data => {
        setMetrics(data.metrics)
        setSuggestions(data.suggestions && data.suggestions.length > 0 ? data.suggestions : null)
        setSuggestionSource(data.suggestionSource || 'none')
        setGeneratedAt(data.generatedAt || null)
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }, [selectedId])

  const applyAnalysisResult = data => {
    setMetrics(data.metrics || data)
    setSuggestions(data.suggestions || null)
    setSuggestionSource(data.suggestionSource || 'none')
    setGeneratedAt(data.generatedAt || null)
  }

  const handleTriggerAnalysis = async () => {
    if (!selectedId) return
    setTriggering(true)
    setError(null)
    setSuggestions(null)
    setSuggestionSource('pending')
    try {
      const result = await triggerAnalysis(selectedId)
      if (result.status === 'pending') {
        // 后端异步执行：立刻拿到 metrics，进入轮询等 suggestions
        if (result.data?.metrics) {
          setMetrics(result.data.metrics)
        }
        const final = await pollAnalysisUntilReady(selectedId)
        if (final) {
          setSuggestions(final.suggestions && final.suggestions.length > 0 ? final.suggestions : null)
          setSuggestionSource(final.suggestionSource || 'none')
          setGeneratedAt(final.generatedAt || null)
          if (final.analysisStatus === 'failed') {
            setError('LLM 分析失败：' + (final.failureReason || '未知原因'))
          }
        } else {
          setError('分析超时，请稍后刷新页面查看结果')
        }
      } else {
        applyAnalysisResult(result.data)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setTriggering(false)
    }
  }

  const renderSuggestions = () => {
    if (suggestions && suggestions.length > 0) {
      return (
        <List
          dataSource={suggestions}
          renderItem={(item, idx) => (
            <List.Item>
              <List.Item.Meta
                avatar={
                  <BulbOutlined style={{
                    fontSize: 20,
                    color: item.priority === 'high' ? '#ff4d4f'
                      : item.priority === 'medium' ? '#fa8c16' : '#8c8c8c'
                  }} />
                }
                title={
                  <Space size={4}>
                    <Tag color={CATEGORY_COLORS[item.category]}>
                      {CATEGORY_LABELS[item.category] || item.category}
                    </Tag>
                    <Tag color={PRIORITY_COLORS[item.priority]}>
                      {item.priority === 'high' ? '高优' : item.priority === 'medium' ? '中优' : '低优'}
                    </Tag>
                    <Text>建议 {idx + 1}</Text>
                  </Space>
                }
                description={item.content}
              />
            </List.Item>
          )}
        />
      )
    }

    // 尚未生成建议时，提示用户点击按钮
    return (
      <Space direction="vertical" style={{ width: '100%' }} size={12}>
        {triggering && (
          <Alert
            type="info"
            showIcon
            icon={<LoadingOutlined />}
            message="AI 正在生成改进建议"
            description="分析任务已提交，正在专用线程池执行，通常 5-20 秒完成，您可以稍等或先去查看其他活动。"
          />
        )}
        <Empty
          description={
            <span>
              点击「<Text strong>生成改进建议</Text>」按钮，<br />
              让 AI 分析活动数据并生成针对性改进建议
            </span>
          }
        >
          <Button type="primary" loading={triggering} onClick={handleTriggerAnalysis}>
            立即生成
          </Button>
        </Empty>
      </Space>
    )
  }

  return (
    <AuthGuard>
      <MainLayout title="活动数据分析">
        {/* 活动选择 */}
        <Card style={{ marginBottom: 16 }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text type="secondary">选择要分析的活动</Text>
            <Select
              style={{ width: '100%', maxWidth: 480 }}
              placeholder="选择已结束的活动"
              value={selectedId}
              onChange={setSelectedId}
              options={myActivities
                .filter(isActivityEnded)
                .map(a => ({ label: a.title, value: a.id }))}
            />
          </Space>
        </Card>

        {loading ? (
          <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
        ) : error ? (
          <Result status="error" title="加载失败" subTitle={error}
            extra={<Button type="primary" onClick={() => {
              setLoading(true)
              setError(null)
              getFullAnalysis(selectedId)
                .then(data => {
                  setMetrics(data.metrics)
                  setSuggestions(data.suggestions && data.suggestions.length > 0 ? data.suggestions : null)
                  setSuggestionSource(data.suggestionSource || 'none')
                  setGeneratedAt(data.generatedAt || null)
                })
                .catch(err => setError(err.message))
                .finally(() => setLoading(false))
            }}>重试</Button>} />
        ) : !metrics ? (
          <Empty description="暂无分析数据" />
        ) : (
          <>
            {/* 数据卡片 */}
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }} align="stretch">
                          <Col xs={12} sm={12} md={6}>
                            <Card hoverable style={{ height: '100%' }}>
                              <Statistic title="浏览量" value={metrics.viewCount} prefix={<EyeOutlined />} suffix="次" />
                            </Card>
                          </Col>
                          <Col xs={12} sm={12} md={6}>
                            <Card hoverable style={{ height: '100%' }}>
                              <Statistic title="收藏人数" value={metrics.favoriteCount} prefix={<HeartOutlined />} suffix="人" valueStyle={{ color: '#eb2f96' }} />
                            </Card>
                          </Col>
                          <Col xs={12} sm={12} md={6}>
                            <Card hoverable style={{ height: '100%' }}>
                              <Statistic title="报名人数" value={metrics.signupCount} prefix={<FormOutlined />} suffix="人" />
                            </Card>
                          </Col>
                          <Col xs={12} sm={12} md={6}>
                            <Card hoverable style={{ height: '100%' }}>
                              <Statistic title="签到人数" value={metrics.checkInCount} prefix={<CheckCircleOutlined />} suffix="人" valueStyle={{ color: '#52c41a' }} />
                            </Card>
                          </Col>
                          <Col xs={12} sm={12} md={6}>
                            <Card hoverable style={{ height: '100%' }}>
                              <Statistic title="评价人数" value={metrics.feedbackCount} prefix={<CommentOutlined />} suffix="人" />
                            </Card>
                          </Col>
                          <Col xs={12} sm={12} md={6}>
                            <Card hoverable style={{ height: '100%' }}>
                              <Statistic title="报名转化率" value={metrics.signupRate} prefix={<RiseOutlined />} suffix="%" precision={1} />
                            </Card>
                          </Col>
                          <Col xs={12} sm={12} md={6}>
                            <Card hoverable style={{ height: '100%' }}>
                              <Statistic title="到场率" value={metrics.attendanceRate} prefix={<TeamOutlined />} suffix="%" precision={1} valueStyle={{ color: '#52c41a' }} />
                            </Card>
                          </Col>
                          <Col xs={12} sm={12} md={6}>
                            <Card hoverable style={{ height: '100%' }}>
                              <Statistic title="平均评分" value={metrics.avgRating || 0} prefix={<StarOutlined />} suffix="/ 5" precision={2} valueStyle={{ color: '#fa8c16' }} />
                            </Card>
                          </Col>
                        </Row>

            {/* 图表区域 */}
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
              <Col xs={24} lg={12}>
                <Card
                  title={<Space><BarChartOutlined /> 评分分布</Space>}
                >
                  <RatingBarChart distribution={metrics.ratingDistribution} />
                </Card>
              </Col>
              <Col xs={24} lg={12}>
                <Card
                  title={<Space><LineChartOutlined /> 报名趋势</Space>}
                  extra={<Text type="secondary" style={{ fontSize: 12 }}>整个报名期</Text>}
                >
                  <SignupTrendLineChart trend={metrics.signupTrend} />
                </Card>
              </Col>
            </Row>

            {/* AI 建议列表 */}
            <Card
              title={
                <Space>
                  <BulbOutlined style={{ color: '#fa541c' }} />
                  <span>AI 改进建议</span>
                </Space>
              }
              extra={
                <Space size={8}>
                  {suggestionSource !== 'none' && (
                    <>
                      <Tag color={suggestionSource === 'llm' ? 'processing' : 'default'}>
                        {suggestionSource === 'llm' ? 'LLM 生成' : '规则模板'}
                      </Tag>
                      {generatedAt && (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {dayjs(generatedAt).format('YYYY-MM-DD HH:mm')}
                        </Text>
                      )}
                    </>
                  )}
                  <Button
                    type="primary"
                    icon={<ReloadOutlined />}
                    loading={triggering}
                    onClick={handleTriggerAnalysis}
                  >
                    {suggestions && suggestions.length > 0 ? '重新生成' : '生成改进建议'}
                  </Button>
                </Space>
              }
            >
              {renderSuggestions()}
            </Card>
          </>
        )}
      </MainLayout>
    </AuthGuard>
  )
}
