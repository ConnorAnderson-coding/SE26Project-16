import { useState, useEffect, useMemo } from 'react'
import {
  Row, Col, Input, Select, Card, Typography, Space, Tag, Empty, Slider,
  DatePicker, Checkbox, Button, Spin, Result
} from 'antd'
import { SearchOutlined, FilterOutlined, CloseCircleOutlined } from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import ActivityCard from '../components/ActivityCard'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { getAllActivities } from '../services/activityApi'
import {
  ACTIVITY_CATEGORIES,
  INTEREST_TAGS,
  AVAILABLE_TIME_OPTIONS,
  getCategoryLabel
} from '../data/mockData'
import {
  matchesInterestTags,
  matchesTimeRange,
  matchesTimeSlots,
  matchesAvailableTime,
  matchesActivityStatus
} from '../utils/activityFilters'

const { Text } = Typography

const STATUS_OPTIONS = [
  { label: '全部状态', value: null },
  { label: '报名中', value: 'published' },
  { label: '已结束', value: 'ended' }
]

export default function ActivityList() {
  const { currentUser } = useApp()
  const [activities, setActivities] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState(null)
  const [location, setLocation] = useState(null)
  const [sortBy, setSortBy] = useState('hot')
  const [minHot, setMinHot] = useState(0)
  const [interestTags, setInterestTags] = useState([])
  const [timeRange, setTimeRange] = useState(null)
  const [timeSlots, setTimeSlots] = useState([])
  const [status, setStatus] = useState(null)
  const [matchMyInterests, setMatchMyInterests] = useState(false)
  const [matchMyTime, setMatchMyTime] = useState(false)

  const loadActivities = () => {
    setLoading(true)
    setError(null)
    getAllActivities({ keyword: keyword.trim() || undefined, category, status, location })
      .then(setActivities)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadActivities()
  }, [category, status, location])

  const locations = useMemo(() =>
    [...new Set(activities.map(a => a.location))],
    [activities]
  )

  const effectiveInterestTags = matchMyInterests
    ? (currentUser?.interests || [])
    : interestTags

  const filtered = useMemo(() => {
    let result = activities.filter(a => a.status !== 'draft')

    if (keyword.trim()) {
      const kw = keyword.trim().toLowerCase()
      result = result.filter(a =>
        a.title.toLowerCase().includes(kw) ||
        a.description.toLowerCase().includes(kw) ||
        (a.tags || []).some(t => t.toLowerCase().includes(kw)) ||
        a.location.toLowerCase().includes(kw) ||
        getCategoryLabel(a.category).includes(kw)
      )
    }

    if (category) result = result.filter(a => a.category === category)
    if (location) result = result.filter(a => a.location === location)
    result = result.filter(a => a.signupCount + a.favoriteCount >= minHot)
    result = result.filter(a => matchesInterestTags(a, effectiveInterestTags))
    result = result.filter(a => matchesTimeRange(a, timeRange))
    result = result.filter(a => matchesTimeSlots(a, timeSlots))
    result = result.filter(a => matchesActivityStatus(a, status))
    if (matchMyTime) {
      result = result.filter(a => matchesAvailableTime(a, currentUser?.availableTime))
    }

    switch (sortBy) {
      case 'hot':
        result.sort((a, b) => (b.signupCount + b.favoriteCount) - (a.signupCount + a.favoriteCount))
        break
      case 'time':
        result.sort((a, b) => new Date(a.startTime) - new Date(b.startTime))
        break
      case 'signup':
        result.sort((a, b) => b.signupCount - a.signupCount)
        break
      default:
        break
    }

    return result
  }, [
    activities, keyword, category, location, sortBy, minHot,
    effectiveInterestTags, timeRange, timeSlots, status, matchMyTime, currentUser
  ])

  const hasActiveFilters = category || location || minHot > 0 || interestTags.length > 0
    || timeRange || timeSlots.length > 0 || status || matchMyInterests || matchMyTime

  const clearFilters = () => {
    setCategory(null)
    setLocation(null)
    setMinHot(0)
    setInterestTags([])
    setTimeRange(null)
    setTimeSlots([])
    setStatus(null)
    setMatchMyInterests(false)
    setMatchMyTime(false)
  }

  return (
    <AuthGuard>
      <MainLayout title="活动检索">
        <Card className="filter-card">
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Input.Search
              size="large"
              prefix={<SearchOutlined />}
              placeholder="语义检索：输入关键词，如 AI、羽毛球、志愿服务..."
              value={keyword}
              onChange={e => setKeyword(e.target.value)}
              onSearch={loadActivities}
              allowClear
              enterButton="搜索"
            />

            <Row gutter={[16, 16]}>
              <Col xs={24} sm={12} md={8} lg={6}>
                <Text type="secondary">兴趣标签</Text>
                <Select
                  mode="multiple"
                  style={{ width: '100%', marginTop: 4 }}
                  placeholder="选择兴趣"
                  allowClear
                  disabled={matchMyInterests}
                  value={interestTags}
                  onChange={setInterestTags}
                  options={INTEREST_TAGS.map(t => ({ label: t, value: t }))}
                />
              </Col>
              <Col xs={24} sm={12} md={8} lg={6}>
                <Text type="secondary">活动类别</Text>
                <Select
                  style={{ width: '100%', marginTop: 4 }}
                  placeholder="全部类别"
                  allowClear
                  value={category}
                  onChange={setCategory}
                  options={ACTIVITY_CATEGORIES}
                />
              </Col>
              <Col xs={24} sm={12} md={8} lg={6}>
                <Text type="secondary">活动地点</Text>
                <Select
                  style={{ width: '100%', marginTop: 4 }}
                  placeholder="全部地点"
                  allowClear
                  value={location}
                  onChange={setLocation}
                  options={locations.map(l => ({ label: l, value: l }))}
                />
              </Col>
              <Col xs={24} sm={12} md={8} lg={6}>
                <Text type="secondary">活动状态</Text>
                <Select
                  style={{ width: '100%', marginTop: 4 }}
                  placeholder="全部状态"
                  allowClear
                  value={status}
                  onChange={setStatus}
                  options={STATUS_OPTIONS.filter(o => o.value !== null)}
                />
              </Col>
              <Col xs={24} sm={12} md={8} lg={6}>
                <Text type="secondary">活动时间</Text>
                <DatePicker.RangePicker
                  style={{ width: '100%', marginTop: 4 }}
                  value={timeRange}
                  onChange={setTimeRange}
                />
              </Col>
              <Col xs={24} sm={12} md={8} lg={6}>
                <Text type="secondary">活动时段</Text>
                <Select
                  mode="multiple"
                  style={{ width: '100%', marginTop: 4 }}
                  placeholder="全部时段"
                  allowClear
                  value={timeSlots}
                  onChange={setTimeSlots}
                  options={AVAILABLE_TIME_OPTIONS}
                />
              </Col>
              <Col xs={24} sm={12} md={8} lg={6}>
                <Text type="secondary">排序方式</Text>
                <Select
                  style={{ width: '100%', marginTop: 4 }}
                  value={sortBy}
                  onChange={setSortBy}
                  options={[
                    { label: '综合热度', value: 'hot' },
                    { label: '开始时间', value: 'time' },
                    { label: '报名人数', value: 'signup' }
                  ]}
                />
              </Col>
              <Col xs={24} sm={12} md={8} lg={6}>
                <Text type="secondary">最低热度：{minHot}</Text>
                <Slider
                  min={0}
                  max={200}
                  value={minHot}
                  onChange={setMinHot}
                  style={{ marginTop: 4 }}
                />
              </Col>
            </Row>

            <Space wrap>
              <Checkbox
                checked={matchMyInterests}
                onChange={e => setMatchMyInterests(e.target.checked)}
              >
                匹配我的兴趣
              </Checkbox>
              <Checkbox
                checked={matchMyTime}
                onChange={e => setMatchMyTime(e.target.checked)}
              >
                匹配可参与时间
              </Checkbox>
            </Space>
          </Space>
        </Card>

        <div style={{ margin: '16px 0' }}>
          <Space wrap>
            <FilterOutlined />
            <Text>共找到 <Text strong>{filtered.length}</Text> 个活动</Text>
            {keyword && (
              <Tag closable onClose={() => setKeyword('')} color="blue">
                关键词：{keyword}
              </Tag>
            )}
            {category && (
              <Tag closable onClose={() => setCategory(null)}>
                类别：{getCategoryLabel(category)}
              </Tag>
            )}
            {location && (
              <Tag closable onClose={() => setLocation(null)}>
                地点：{location}
              </Tag>
            )}
            {status && (
              <Tag closable onClose={() => setStatus(null)}>
                状态：{status === 'published' ? '报名中' : '已结束'}
              </Tag>
            )}
            {interestTags.length > 0 && !matchMyInterests && (
              <Tag closable onClose={() => setInterestTags([])}>
                兴趣：{interestTags.join('、')}
              </Tag>
            )}
            {matchMyInterests && (
              <Tag closable onClose={() => setMatchMyInterests(false)} color="processing">
                匹配我的兴趣
              </Tag>
            )}
            {matchMyTime && (
              <Tag closable onClose={() => setMatchMyTime(false)} color="processing">
                匹配可参与时间
              </Tag>
            )}
            {timeRange && (
              <Tag closable onClose={() => setTimeRange(null)}>
                时间范围已选
              </Tag>
            )}
            {timeSlots.length > 0 && (
              <Tag closable onClose={() => setTimeSlots([])}>
                时段：{timeSlots.length} 项
              </Tag>
            )}
            {minHot > 0 && (
              <Tag closable onClose={() => setMinHot(0)}>
                热度 ≥ {minHot}
              </Tag>
            )}
            {hasActiveFilters && (
              <Button type="link" size="small" icon={<CloseCircleOutlined />} onClick={clearFilters}>
                清空筛选
              </Button>
            )}
          </Space>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
        ) : error ? (
          <Result status="error" title="加载失败" subTitle={error} extra={
            <Button type="primary" onClick={loadActivities}>重试</Button>
          } />
        ) : filtered.length === 0 ? (
          <Empty description="没有找到匹配的活动" />
        ) : (
          <Row gutter={[16, 16]}>
            {filtered.map(activity => (
              <Col xs={24} sm={12} lg={8} key={activity.id}>
                <ActivityCard activity={activity} />
              </Col>
            ))}
          </Row>
        )}
      </MainLayout>
    </AuthGuard>
  )
}
