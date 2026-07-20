import { useState, useMemo, useEffect } from 'react'
import {
  Card, Row, Col, Statistic, Select, Progress, Alert, Table, Tag, Typography, Space
} from 'antd'
import {
  RiseOutlined, TeamOutlined, StarOutlined, HeartOutlined
} from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { getMyActivities } from '../services/activityApi'
import { listRegistrations } from '../services/registrationApi'
import { SHARE_CHANNEL_LABELS } from '../data/mockData'

const { Text, Paragraph } = Typography

const CHECKIN_METHOD_LABELS = {
  qrcode: '二维码',
  location: '定位',
  password: '口令'
}

function buildSuggestions({ signupRate, attendanceRate, avgRating }) {
  const suggestions = []
  if (attendanceRate < 70) {
    suggestions.push('到场率偏低，建议活动前推送签到提醒，并在现场设置明显签到指引。')
  }
  if (avgRating !== null && avgRating < 4) {
    suggestions.push('用户评价有待提升，建议收集更多反馈并针对性优化活动内容与流程。')
  }
  if (signupRate < 50) {
    suggestions.push('报名转化率不足，建议优化活动海报与描述，增加多渠道宣传推广。')
  }
  if (suggestions.length === 0) {
    suggestions.push('活动整体表现良好，可继续保持当前运营策略，并尝试扩大传播覆盖面。')
  }
  return suggestions
}

export default function OrganizerAnalytics() {
  const { checkIns } = useApp()
  const [myActivities, setMyActivities] = useState([])
  const [signups, setSignups] = useState([])
  const [feedbacks] = useState([])

  useEffect(() => {
    getMyActivities().then(setMyActivities)
    listRegistrations().then(setSignups)
  }, [])

  const [selectedId, setSelectedId] = useState(null)

  useEffect(() => {
    if (myActivities.length && !selectedId) {
      setSelectedId(myActivities[0]?.id || null)
    }
  }, [myActivities, selectedId])

  const activity = myActivities.find(a => a.id === selectedId)

  const metrics = useMemo(() => {
    if (!activity) return null

    const activitySignups = signups.filter(s => s.activityId === activity.id)
    const approved = activitySignups.filter(s => s.status === 'approved')
    const activityCheckIns = checkIns.filter(c => c.activityId === activity.id)
    const activityFeedbacks = feedbacks.filter(f => f.activityId === activity.id)

    const signupRate = activity.maxParticipants
      ? Math.round((activity.signupCount / activity.maxParticipants) * 100)
      : 0
    const attendanceRate = approved.length
      ? Math.round((activityCheckIns.length / approved.length) * 100)
      : 0
    const avgRating = activityFeedbacks.length
      ? activityFeedbacks.reduce((sum, f) => sum + f.rating, 0) / activityFeedbacks.length
      : null
    const favoriteConversion = activity.favoriteCount
      ? Math.round((activity.signupCount / activity.favoriteCount) * 100)
      : null

    const statusCounts = {
      pending: activitySignups.filter(s => s.status === 'pending').length,
      approved: activitySignups.filter(s => s.status === 'approved').length,
      rejected: activitySignups.filter(s => s.status === 'rejected').length
    }

    const checkInMethods = activityCheckIns.reduce((acc, c) => {
      acc[c.method] = (acc[c.method] || 0) + 1
      return acc
    }, {})

    const shareStats = activity.shareStats || {}
    const shareTotal = Object.values(shareStats).reduce((s, v) => s + v, 0) || 1

    return {
      signupRate,
      attendanceRate,
      avgRating,
      favoriteConversion,
      statusCounts,
      checkInMethods,
      shareStats,
      shareTotal,
      suggestions: buildSuggestions({ signupRate, attendanceRate, avgRating })
    }
  }, [activity, signups, checkIns, feedbacks])

  return (
    <AuthGuard>
      <MainLayout title="活动数据分析">
          <Card style={{ marginBottom: 16 }}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Text type="secondary">选择要分析的活动</Text>
              <Select
                style={{ width: '100%', maxWidth: 480 }}
                value={selectedId}
                onChange={setSelectedId}
                options={myActivities.map(a => ({ label: a.title, value: a.id }))}
              />
            </Space>
          </Card>

          {!activity || !metrics ? (
            <Alert message="暂无组织活动数据" type="info" showIcon />
          ) : (
            <>
              <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                <Col xs={12} sm={6}>
                  <Card>
                    <Statistic
                      title="报名转化率"
                      value={metrics.signupRate}
                      suffix="%"
                      prefix={<RiseOutlined />}
                    />
                    <Progress percent={metrics.signupRate} showInfo={false} size="small" />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card>
                    <Statistic
                      title="到场率"
                      value={metrics.attendanceRate}
                      suffix="%"
                      prefix={<TeamOutlined />}
                      valueStyle={{ color: '#52c41a' }}
                    />
                    <Progress percent={metrics.attendanceRate} showInfo={false} size="small" strokeColor="#52c41a" />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card>
                    <Statistic
                      title="平均评分"
                      value={metrics.avgRating !== null ? metrics.avgRating.toFixed(1) : '暂无'}
                      prefix={<StarOutlined />}
                      valueStyle={{ color: '#faad14' }}
                    />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card>
                    <Statistic
                      title="收藏转化"
                      value={metrics.favoriteConversion !== null ? metrics.favoriteConversion : 'N/A'}
                      suffix={metrics.favoriteConversion !== null ? '%' : ''}
                      prefix={<HeartOutlined />}
                      valueStyle={{ color: '#eb2f96' }}
                    />
                  </Card>
                </Col>
              </Row>

              <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                <Col xs={24} lg={12}>
                  <Card title="传播路径">
                    {Object.keys(metrics.shareStats).length === 0 ? (
                      <Text type="secondary">暂无传播数据</Text>
                    ) : (
                      Object.entries(metrics.shareStats).map(([key, value]) => (
                        <div key={key} style={{ marginBottom: 12 }}>
                          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                            <Text>{SHARE_CHANNEL_LABELS[key] || key}</Text>
                            <Text type="secondary">
                              {value}（{Math.round((value / metrics.shareTotal) * 100)}%）
                            </Text>
                          </Space>
                          <Progress
                            percent={Math.round((value / metrics.shareTotal) * 100)}
                            showInfo={false}
                          />
                        </div>
                      ))
                    )}
                  </Card>
                </Col>
                <Col xs={24} lg={12}>
                  <Card title="改进建议">
                    {metrics.suggestions.map((s, i) => (
                      <Alert
                        key={i}
                        message={s}
                        type="info"
                        showIcon
                        style={{ marginBottom: i < metrics.suggestions.length - 1 ? 8 : 0 }}
                      />
                    ))}
                  </Card>
                </Col>
              </Row>

              <Row gutter={[16, 16]}>
                <Col xs={24} lg={12}>
                  <Card title="报名状态分布">
                    <Table
                      size="small"
                      pagination={false}
                      dataSource={[
                        { key: 'pending', status: '待审核', count: metrics.statusCounts.pending },
                        { key: 'approved', status: '已通过', count: metrics.statusCounts.approved },
                        { key: 'rejected', status: '已拒绝', count: metrics.statusCounts.rejected }
                      ]}
                      columns={[
                        {
                          title: '状态',
                          dataIndex: 'status',
                          render: (text, record) => (
                            <Tag color={
                              record.key === 'approved' ? 'success'
                                : record.key === 'pending' ? 'processing' : 'error'
                            }>
                              {text}
                            </Tag>
                          )
                        },
                        { title: '人数', dataIndex: 'count' }
                      ]}
                    />
                  </Card>
                </Col>
                <Col xs={24} lg={12}>
                  <Card title="签到方式分布">
                    {Object.keys(metrics.checkInMethods).length === 0 ? (
                      <Text type="secondary">暂无签到记录</Text>
                    ) : (
                      <Table
                        size="small"
                        pagination={false}
                        dataSource={Object.entries(metrics.checkInMethods).map(([method, count]) => ({
                          key: method,
                          method: CHECKIN_METHOD_LABELS[method] || method,
                          count
                        }))}
                        columns={[
                          { title: '签到方式', dataIndex: 'method' },
                          { title: '人数', dataIndex: 'count' }
                        ]}
                      />
                    )}
                  </Card>
                </Col>
              </Row>

              <Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0 }}>
                数据基于当前活动报名、签到与评价记录计算，传播路径为示例数据。
              </Paragraph>
            </>
          )}
        </MainLayout>
    </AuthGuard>
  )
}
