import { useEffect, useState } from 'react'
import {
  Card, Row, Col, Statistic, Select, Spin, Result, Button,
  Tag, Typography, List, Space, Empty
} from 'antd'
import {
  EyeOutlined, RiseOutlined, TeamOutlined, StarOutlined,
  BulbOutlined, BarChartOutlined, LineChartOutlined,
  HeartOutlined, CheckCircleOutlined, FormOutlined, CommentOutlined
} from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { getMyActivities } from '../services/activityApi'
import { getActivityMetrics, triggerAnalysis } from '../services/analyticsApi'

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

/** 报名趋势折线图（演示用模拟数据） */
function SignupTrendLineChart() {
  // 模拟最近 7 天报名趋势数据
  const days = ['D-6', 'D-5', 'D-4', 'D-3', 'D-2', 'D-1', 'D-Day']
  const values = [3, 5, 8, 6, 10, 12, 9]
  const maxVal = Math.max(...values, 1)
  const points = values.map((v, i) => ({
    x: (i / (days.length - 1)) * 100,
    y: 100 - (v / maxVal) * 100
  }))
  const linePath = points.map((p, i) =>
    `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`
  ).join(' ')

  return (
    <div style={{ position: 'relative', height: 160 }}>
      <svg viewBox="0 0 100 100" style={{ width: '100%', height: '100%' }} preserveAspectRatio="none">
        {/* 网格线 */}
        {[0, 25, 50, 75, 100].map(y => (
          <line key={y} x1="0" y1={y} x2="100" y2={y} stroke="#f0f0f0" strokeWidth="0.5" />
        ))}
        {/* 折线 */}
        <path d={linePath} fill="none" stroke="#1677ff" strokeWidth="1.5" />
        {/* 填充区域 */}
        <path d={`${linePath} L100,100 L0,100 Z`} fill="url(#trendGrad)" opacity="0.15" />
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
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0 8px' }}>
        {days.map(d => <Text key={d} style={{ fontSize: 11 }} type="secondary">{d}</Text>)}
      </div>
    </div>
  )
}

/* ==================== 主页面 ==================== */

export default function OrganizerAnalytics() {
  const [myActivities, setMyActivities] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [metrics, setMetrics] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [suggestions, setSuggestions] = useState(null)
  const [suggestionSource, setSuggestionSource] = useState('none')
  const [triggering, setTriggering] = useState(false)

  useEffect(() => {
    getMyActivities()
      .then(activities => {
        setMyActivities(activities)
        const firstEnded = activities.find(a => a.status === 'ended')
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
      setSelectedId(myActivities.find(a => a.status === 'ended')?.id || null)
    }
  }, [myActivities, selectedId])

  useEffect(() => {
    if (!selectedId) return
    setLoading(true)
    setError(null)
    // 仅加载指标数据，不加载缓存的建议，确保每次点击按钮都是实时 LLM 生成
    getActivityMetrics(selectedId)
      .then(data => {
        setMetrics(data)
        setSuggestions(null)
        setSuggestionSource('none')
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }, [selectedId])

  const applyAnalysisResult = data => {
    setMetrics(data.metrics || data)
    setSuggestions(data.suggestions || null)
    setSuggestionSource(data.suggestionSource || 'none')
  }

  const handleTriggerAnalysis = async () => {
    if (!selectedId) return
    setTriggering(true)
    setError(null)
    try {
      applyAnalysisResult(await triggerAnalysis(selectedId))
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
                .filter(a => a.status === 'ended')
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
              getActivityMetrics(selectedId)
                .then(data => { setMetrics(data); setSuggestions(null); setSuggestionSource('none'); })
                .catch(err => setError(err.message))
                .finally(() => setLoading(false))
            }}>重试</Button>} />
        ) : !metrics ? (
          <Empty description="暂无分析数据" />
        ) : (
          <>
            {/* 数据卡片 */}
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
              <Col xs={12} sm={6} md={3}>
                <Card hoverable>
                  <Statistic title="浏览量" value={metrics.viewCount} prefix={<EyeOutlined />} suffix="次" />
                </Card>
              </Col>
              <Col xs={12} sm={6} md={3}>
                <Card hoverable>
                  <Statistic title="收藏人数" value={metrics.favoriteCount} prefix={<HeartOutlined />} suffix="人" valueStyle={{ color: '#eb2f96' }} />
                </Card>
              </Col>
              <Col xs={12} sm={6} md={3}>
                <Card hoverable>
                  <Statistic title="报名人数" value={metrics.signupCount} prefix={<FormOutlined />} suffix="人" />
                </Card>
              </Col>
              <Col xs={12} sm={6} md={3}>
                <Card hoverable>
                  <Statistic title="签到人数" value={metrics.checkInCount} prefix={<CheckCircleOutlined />} suffix="人" valueStyle={{ color: '#52c41a' }} />
                </Card>
              </Col>
              <Col xs={12} sm={6} md={3}>
                <Card hoverable>
                  <Statistic title="评价人数" value={metrics.feedbackCount} prefix={<CommentOutlined />} suffix="人" />
                </Card>
              </Col>
              <Col xs={12} sm={6} md={3}>
                <Card hoverable>
                  <Statistic title="报名转化率" value={metrics.signupRate} prefix={<RiseOutlined />} suffix="%" precision={1} />
                </Card>
              </Col>
              <Col xs={12} sm={6} md={3}>
                <Card hoverable>
                  <Statistic title="到场率" value={metrics.attendanceRate} prefix={<TeamOutlined />} suffix="%" precision={1} valueStyle={{ color: '#52c41a' }} />
                </Card>
              </Col>
              <Col xs={12} sm={6} md={3}>
                <Card hoverable>
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
                  extra={<Text type="secondary" style={{ fontSize: 12 }}>最近 7 天</Text>}
                >
                  <SignupTrendLineChart />
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
                <Space>
                  {suggestionSource !== 'none' && (
                    <Tag color={suggestionSource === 'llm' ? 'processing' : 'default'}>
                      {suggestionSource === 'llm' ? 'LLM 生成' : '规则模板'}
                    </Tag>
                  )}
                  <Button type="primary" loading={triggering} onClick={handleTriggerAnalysis}>
                    生成改进建议
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
