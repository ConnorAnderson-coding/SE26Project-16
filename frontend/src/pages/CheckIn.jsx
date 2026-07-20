import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Alert, Button, Card, Col, Input, QRCode, Row, Select, Space, Spin, Statistic, Table, Tabs, Tag, Typography, message
} from 'antd'
import {
  CheckCircleOutlined, EnvironmentOutlined, KeyOutlined, QrcodeOutlined, ReloadOutlined
} from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { getMyRegistrations } from '../services/registrationApi'
import { getMyActivities, getActivityById } from '../services/activityApi'
import * as checkInApi from '../services/checkInApi'
import { formatDateTime } from '../data/mockData'

const { Title, Text, Paragraph } = Typography

const CHECKIN_METHODS = {
  qrcode: '二维码签到',
  location: '定位签到',
  password: '动态口令签到'
}

function parseQrContent(value) {
  const text = value.trim()
  if (!text) return null
  if (!text.startsWith('CHECKIN:')) {
    return { token: text }
  }
  const [, activityId, token] = text.split(':')
  return activityId && token ? { activityId, token } : null
}

export default function CheckIn() {
  const [searchParams] = useSearchParams()
  const preActivityId = searchParams.get('activityId')
  const { currentUser, checkIns, checkIn: doCheckIn } = useApp()

  const [loading, setLoading] = useState(true)
  const [approvedActivities, setApprovedActivities] = useState([])
  const [organizerActivities, setOrganizerActivities] = useState([])
  const [selectedId, setSelectedId] = useState(preActivityId)
  const [activity, setActivity] = useState(null)
  const [qrSession, setQrSession] = useState(null)
  const [passwordSession, setPasswordSession] = useState(null)
  const [qrInput, setQrInput] = useState('')
  const [password, setPassword] = useState('')
  const [activityStats, setActivityStats] = useState(null)
  const [sessionLoading, setSessionLoading] = useState(false)

  useEffect(() => {
    async function load() {
      setLoading(true)
      try {
        const [regs, orgActs] = await Promise.all([
          getMyRegistrations(),
          getMyActivities()
        ])
        const approved = regs.filter(s => s.status === 'approved')
        const actList = await Promise.all(
          approved.map(s => getActivityById(s.activityId).catch(() => null))
        )
        setApprovedActivities(actList.filter(a => a && a.status !== 'ended'))
        setOrganizerActivities(orgActs.filter(a => a.status === 'published'))
        if (!selectedId) {
          const first = actList.find(Boolean) || orgActs[0]
          if (first) setSelectedId(first.id)
        }
      } catch (err) {
        message.error(err.message || '签到数据加载失败')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  useEffect(() => {
    if (!selectedId) {
      setActivity(null)
      return
    }
    getActivityById(selectedId).then(setActivity).catch(() => setActivity(null))
  }, [selectedId])

  const isOrganizer = activity?.organizerId === currentUser?.id || currentUser?.role === 'admin'
  const hasCheckedIn = checkIns.some(
    c => String(c.activityId) === String(selectedId) && c.userId === currentUser?.id
  )

  const refreshSessions = async () => {
    if (!selectedId || !isOrganizer) return
    setSessionLoading(true)
    try {
      const [nextQr, nextPassword, stats] = await Promise.all([
        checkInApi.createQrSession(selectedId),
        checkInApi.createPasswordSession(selectedId),
        checkInApi.getStats(selectedId)
      ])
      setQrSession(nextQr)
      setPasswordSession(nextPassword)
      setActivityStats(stats)
    } catch (err) {
      message.error(err.message || '签到会话生成失败')
    } finally {
      setSessionLoading(false)
    }
  }

  useEffect(() => {
    setQrSession(null)
    setPasswordSession(null)
    setActivityStats(null)
    if (!selectedId || !isOrganizer) return undefined
    refreshSessions()
    const timer = window.setInterval(refreshSessions, 30000)
    return () => window.clearInterval(timer)
  }, [selectedId, isOrganizer])

  const allSelectable = useMemo(() => [
    ...approvedActivities.map(a => ({ ...a, role: 'participant' })),
    ...organizerActivities
      .filter(a => !approvedActivities.find(p => p.id === a.id))
      .map(a => ({ ...a, role: 'organizer' }))
  ], [approvedActivities, organizerActivities])

  const submitQrCheckIn = async () => {
    const parsed = parseQrContent(qrInput)
    if (!parsed?.token) {
      message.warning('二维码内容无效')
      return
    }
    const activityId = parsed.activityId || selectedId
    const result = await doCheckIn(activityId, 'qrcode', { token: parsed.token })
    result.success ? message.success(result.message) : message.warning(result.message)
  }

  const submitPasswordCheckIn = async () => {
    const result = await doCheckIn(selectedId, 'password', { code: password })
    result.success ? message.success(result.message) : message.warning(result.message)
  }

  const submitLocationCheckIn = async () => {
    if (!navigator.geolocation) {
      message.warning('当前浏览器不支持定位')
      return
    }
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const result = await doCheckIn(selectedId, 'location', {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude
        })
        result.success ? message.success(result.message) : message.warning(result.message)
      },
      () => message.warning('无法获取当前位置'),
      { enableHighAccuracy: true, timeout: 10000 }
    )
  }

  const records = isOrganizer ? (activityStats?.records || []) : checkIns.filter(c => String(c.activityId) === String(selectedId))

  const columns = [
    {
      title: '签到方式',
      dataIndex: 'method',
      render: method => <Tag color="green">{CHECKIN_METHODS[method] || method}</Tag>
    },
    {
      title: '用户',
      render: (_, record) => record.userName || record.userId
    },
    {
      title: '签到时间',
      dataIndex: 'time',
      render: value => formatDateTime(value)
    }
  ]

  if (loading) {
    return (
      <AuthGuard>
        <MainLayout title="活动签到">
          <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
        </MainLayout>
      </AuthGuard>
    )
  }

  return (
    <AuthGuard>
      <MainLayout title="活动签到">
        <Card style={{ marginBottom: 16 }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text>选择活动</Text>
            <Select
              style={{ width: '100%', maxWidth: 480 }}
              placeholder="选择要签到的活动"
              value={selectedId}
              onChange={setSelectedId}
              options={allSelectable.map(a => ({
                label: `${a.title}${a.role === 'organizer' ? '（组织者）' : ''}`,
                value: a.id
              }))}
            />
          </Space>
        </Card>

        {!activity ? (
          <Alert message="请选择活动或先报名并通过审核" type="info" showIcon />
        ) : (
          <>
            {isOrganizer && (
              <Row gutter={16} style={{ marginBottom: 16 }}>
                <Col xs={24} md={8}><Card><Statistic title="已签到" value={activityStats?.checkedInCount || 0} /></Card></Col>
                <Col xs={24} md={8}><Card><Statistic title="未签到" value={activityStats?.uncheckedCount || 0} /></Card></Col>
                <Col xs={24} md={8}><Card><Statistic title="签到率" value={activityStats?.checkInRate || 0} precision={1} suffix="%" /></Card></Col>
              </Row>
            )}

            <Tabs
              items={[
                {
                  key: 'qrcode',
                  label: <><QrcodeOutlined /> 二维码签到</>,
                  children: (
                    <Card>
                      {isOrganizer ? (
                        <Space direction="vertical" align="center" style={{ width: '100%' }}>
                          <Title level={5}>活动签到二维码</Title>
                          {qrSession?.qrContent ? (
                            <QRCode value={qrSession.qrContent} size={220} />
                          ) : (
                            <Spin />
                          )}
                          <Button icon={<ReloadOutlined />} loading={sessionLoading} onClick={refreshSessions}>刷新</Button>
                        </Space>
                      ) : (
                        <Space direction="vertical" style={{ width: '100%' }}>
                          <Input.TextArea
                            rows={3}
                            placeholder="粘贴扫码得到的 CHECKIN 内容或 token"
                            value={qrInput}
                            onChange={e => setQrInput(e.target.value)}
                          />
                          {hasCheckedIn ? (
                            <Alert message="您已完成签到" type="success" showIcon icon={<CheckCircleOutlined />} />
                          ) : (
                            <Button type="primary" block size="large" onClick={submitQrCheckIn}>二维码签到</Button>
                          )}
                        </Space>
                      )}
                    </Card>
                  )
                },
                {
                  key: 'location',
                  label: <><EnvironmentOutlined /> 定位签到</>,
                  children: (
                    <Card>
                      <Title level={5}>定位签到</Title>
                      <Paragraph type="secondary">活动地址：{activity.location}</Paragraph>
                      {isOrganizer ? (
                        <Alert message={`${records.filter(c => c.method === 'location').length} 人通过定位签到`} type="info" showIcon />
                      ) : hasCheckedIn ? (
                        <Alert message="您已完成签到" type="success" showIcon />
                      ) : (
                        <Button type="primary" block size="large" onClick={submitLocationCheckIn}>获取当前位置并签到</Button>
                      )}
                    </Card>
                  )
                },
                {
                  key: 'password',
                  label: <><KeyOutlined /> 动态口令</>,
                  children: (
                    <Card>
                      <Title level={5}>动态口令签到</Title>
                      {isOrganizer ? (
                        <Space direction="vertical" align="center" style={{ width: '100%' }}>
                          <Title level={2} style={{ letterSpacing: 8, color: '#1677ff' }}>{passwordSession?.code || '------'}</Title>
                          <Button icon={<ReloadOutlined />} loading={sessionLoading} onClick={refreshSessions}>刷新</Button>
                        </Space>
                      ) : (
                        <Space direction="vertical" style={{ width: '100%' }}>
                          <Input
                            size="large"
                            placeholder="输入6位动态口令"
                            value={password}
                            maxLength={6}
                            onChange={e => setPassword(e.target.value.replace(/\D/g, ''))}
                            style={{ maxWidth: 300 }}
                          />
                          {hasCheckedIn ? (
                            <Alert message="您已完成签到" type="success" showIcon />
                          ) : (
                            <Button type="primary" size="large" onClick={submitPasswordCheckIn}>口令签到</Button>
                          )}
                        </Space>
                      )}
                    </Card>
                  )
                }
              ]}
            />

            {records.length > 0 && (
              <Card title="签到记录" style={{ marginTop: 16 }}>
                <Table rowKey="id" columns={columns} dataSource={records} pagination={false} />
              </Card>
            )}
          </>
        )}
      </MainLayout>
    </AuthGuard>
  )
}
