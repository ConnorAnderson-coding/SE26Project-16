import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Card, Tabs, Button, Input, Select, message, Typography, Tag, Space, QRCode, Alert, Spin
} from 'antd'
import {
  QrcodeOutlined, EnvironmentOutlined, KeyOutlined, CheckCircleOutlined
} from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { getMyRegistrations } from '../services/registrationApi'
import { getMyActivities, getActivityById } from '../services/activityApi'
import { getUsers } from '../services/mock/mockApi'
import { formatDateTime } from '../data/mockData'

const { Title, Text, Paragraph } = Typography

const CHECKIN_METHODS = {
  qrcode: '二维码签到',
  location: '定位签到',
  password: '动态口令签到'
}

export default function CheckIn() {
  const [searchParams] = useSearchParams()
  const preActivityId = searchParams.get('activityId')
  const { currentUser, checkIns, checkIn: doCheckIn } = useApp()

  const [loading, setLoading] = useState(true)
  const [approvedActivities, setApprovedActivities] = useState([])
  const [organizerActivities, setOrganizerActivities] = useState([])
  const [users, setUsers] = useState([])
  const [selectedId, setSelectedId] = useState(preActivityId)
  const [activity, setActivity] = useState(null)
  const [password, setPassword] = useState('')
  const [locationVerified, setLocationVerified] = useState(false)

  useEffect(() => {
    async function load() {
      setLoading(true)
      try {
        const [regs, orgActs, userList] = await Promise.all([
          getMyRegistrations(),
          getMyActivities(),
          getUsers()
        ])
        const approved = regs.filter(s => s.status === 'approved')
        const actList = await Promise.all(
          approved.map(s => getActivityById(s.activityId).catch(() => null))
        )
        setApprovedActivities(actList.filter(a => a && a.status !== 'ended'))
        setOrganizerActivities(orgActs.filter(a => a.status === 'published'))
        setUsers(userList)
        if (!selectedId) {
          const first = actList.find(Boolean) || orgActs[0]
          if (first) setSelectedId(first.id)
        }
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  useEffect(() => {
    if (selectedId) {
      getActivityById(selectedId).then(setActivity).catch(() => setActivity(null))
    }
  }, [selectedId])

  const hasCheckedIn = checkIns.some(
    c => c.activityId === selectedId && c.userId === currentUser?.id
  )
  const isOrganizer = activity?.organizerId === currentUser?.id
  const activityCheckIns = checkIns.filter(c => c.activityId === selectedId)

  const handleCheckIn = async (method) => {
    if (method === 'password' && password !== activity?.checkInCode) {
      message.error('口令错误，请重新输入')
      return
    }
    if (method === 'location' && !locationVerified) {
      message.warning('请先验证定位')
      return
    }
    const result = await doCheckIn(selectedId, method)
    result.success ? message.success(result.message) : message.warning(result.message)
  }

  const verifyLocation = () => {
    setLocationVerified(true)
    message.success('定位验证成功：您已在活动范围内（模拟）')
  }

  const allSelectable = [
    ...approvedActivities.map(a => ({ ...a, role: 'participant' })),
    ...organizerActivities
      .filter(a => !approvedActivities.find(p => p.id === a.id))
      .map(a => ({ ...a, role: 'organizer' }))
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
          <Tabs
            items={[
              {
                key: 'qrcode',
                label: <><QrcodeOutlined /> 二维码签到</>,
                children: (
                  <Card>
                    {isOrganizer ? (
                      <>
                        <Title level={5}>活动签到二维码（组织者展示）</Title>
                        <Paragraph type="secondary">参与者扫描以下二维码完成签到</Paragraph>
                        <div style={{ textAlign: 'center', padding: 24 }}>
                          <QRCode value={`CHECKIN:${activity.id}:${activity.checkInCode}`} size={200} />
                          <Paragraph style={{ marginTop: 12 }}>口令：{activity.checkInCode}</Paragraph>
                        </div>
                        <Alert message={`已有 ${activityCheckIns.length} 人完成签到`} type="success" showIcon />
                      </>
                    ) : (
                      <>
                        <Title level={5}>扫描二维码签到</Title>
                        <Paragraph type="secondary">请向组织者出示的二维码扫描区域靠近（以下为模拟扫码）</Paragraph>
                        <div style={{ textAlign: 'center', padding: 24 }}>
                          <div className="qr-scan-area">
                            <QrcodeOutlined style={{ fontSize: 64, color: '#1677ff' }} />
                            <Paragraph>模拟扫描区域</Paragraph>
                          </div>
                        </div>
                        {hasCheckedIn ? (
                          <Alert message="您已完成签到" type="success" showIcon icon={<CheckCircleOutlined />} />
                        ) : (
                          <Button type="primary" block size="large" onClick={() => handleCheckIn('qrcode')}>
                            模拟扫码签到
                          </Button>
                        )}
                      </>
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
                    {!isOrganizer && (
                      <>
                        {!locationVerified && (
                          <Button onClick={verifyLocation} style={{ marginBottom: 16 }}>获取当前位置</Button>
                        )}
                        {locationVerified && (
                          <Alert message="定位已验证" type="success" showIcon style={{ marginBottom: 16 }} />
                        )}
                        {hasCheckedIn ? (
                          <Alert message="您已完成签到" type="success" showIcon />
                        ) : (
                          <Button type="primary" block size="large" disabled={!locationVerified} onClick={() => handleCheckIn('location')}>
                            定位签到
                          </Button>
                        )}
                      </>
                    )}
                    {isOrganizer && (
                      <Alert message={`${activityCheckIns.filter(c => c.method === 'location').length} 人通过定位签到`} type="info" showIcon />
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
                      <>
                        <Paragraph>当前活动口令（可告知参与者）：</Paragraph>
                        <Title level={2} style={{ letterSpacing: 8, color: '#1677ff' }}>{activity.checkInCode}</Title>
                      </>
                    ) : (
                      <>
                        <Paragraph type="secondary">请输入组织者公布的动态口令</Paragraph>
                        <Input size="large" placeholder="输入口令" value={password} onChange={e => setPassword(e.target.value)} style={{ marginBottom: 16, maxWidth: 300 }} />
                        {hasCheckedIn ? (
                          <Alert message="您已完成签到" type="success" showIcon />
                        ) : (
                          <Button type="primary" size="large" onClick={() => handleCheckIn('password')}>口令签到</Button>
                        )}
                      </>
                    )}
                  </Card>
                )
              }
            ]}
          />
        )}

        {activity && activityCheckIns.length > 0 && (
          <Card title="签到记录" style={{ marginTop: 16 }}>
            {activityCheckIns.map(c => {
              const user = users.find(u => u.id === c.userId)
              return (
                <div key={c.id} className="checkin-record">
                  <Space>
                    <Tag color="green">{CHECKIN_METHODS[c.method]}</Tag>
                    <Text>{user?.name || c.userId}</Text>
                    <Text type="secondary">{formatDateTime(c.time)}</Text>
                  </Space>
                </div>
              )
            })}
          </Card>
        )}
      </MainLayout>
    </AuthGuard>
  )
}
