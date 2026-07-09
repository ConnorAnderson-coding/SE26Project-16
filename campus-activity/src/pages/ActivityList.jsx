import { useState, useMemo } from 'react'
import {
  Row, Col, Input, Select, Card, Typography, Space, Tag, Empty, Slider
} from 'antd'
import { SearchOutlined, FilterOutlined } from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import ActivityCard from '../components/ActivityCard'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import {
  ACTIVITY_CATEGORIES,
  getCategoryLabel
} from '../data/mockData'

const { Text } = Typography

export default function ActivityList() {
  const { activities } = useApp()
  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState(null)
  const [location, setLocation] = useState(null)
  const [sortBy, setSortBy] = useState('hot')
  const [minHot, setMinHot] = useState(0)

  const locations = useMemo(() =>
    [...new Set(activities.map(a => a.location))],
    [activities]
  )

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
  }, [activities, keyword, category, location, sortBy, minHot])

  return (
    <AuthGuard>
      <MainLayout title="活动检索">
        <Card className="filter-card">
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Input
              size="large"
              prefix={<SearchOutlined />}
              placeholder="语义检索：输入关键词，如 AI、羽毛球、志愿服务..."
              value={keyword}
              onChange={e => setKeyword(e.target.value)}
              allowClear
            />

            <Row gutter={[16, 16]}>
              <Col xs={24} sm={8} md={6}>
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
              <Col xs={24} sm={8} md={6}>
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
              <Col xs={24} sm={8} md={6}>
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
              <Col xs={24} sm={24} md={6}>
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
          </Space>
        </Card>

        <div style={{ margin: '16px 0' }}>
          <Space>
            <FilterOutlined />
            <Text>共找到 <Text strong>{filtered.length}</Text> 个活动</Text>
            {keyword && <Tag color="blue">关键词：{keyword}</Tag>}
          </Space>
        </div>

        {filtered.length === 0 ? (
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
