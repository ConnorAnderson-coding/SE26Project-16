import { useEffect, useState } from 'react'
import { Card, Form, Input, Rate, Button, Select, message, List, Tag, Typography, Space, Spin } from 'antd'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { getMyRegistrations } from '../services/registrationApi'
import { getActivityById } from '../services/activityApi'
import { getMyFeedbacks } from '../services/feedbackApi'
import { formatDateTime } from '../data/mockData'

const { Text } = Typography

export default function Feedback() {
  const { submitFeedback } = useApp()
  const [form] = Form.useForm()
  const [selectedId, setSelectedId] = useState(null)
  const [participatable, setParticipatable] = useState([])
  const [myFeedbacks, setMyFeedbacks] = useState([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)

  const loadData = async () => {
    setLoading(true)
    try {
      const [regs, fbs] = await Promise.all([
        getMyRegistrations(),
        getMyFeedbacks()
      ])
      const approved = regs.filter(s => s.status === 'approved')
      const activities = await Promise.all(
        approved.map(s => getActivityById(s.activityId).catch(() => null))
      )
      const eligible = activities.filter(
        a => a && (a.status === 'ended' || new Date(a.endTime) < new Date())
      )
      setParticipatable(eligible)
      setMyFeedbacks(fbs)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [])

  const handleSubmit = async (values) => {
    if (!selectedId) {
      message.warning('请选择要评价的活动')
      return
    }
    setSubmitting(true)
    try {
      const result = await submitFeedback({
        activityId: selectedId,
        rating: values.rating,
        content: values.content
      })
      result.success ? message.success(result.message) : message.warning(result.message)
      if (result.success) {
        form.resetFields()
        setSelectedId(null)
        loadData()
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthGuard>
      <MainLayout title="活动反馈">
        {loading ? (
          <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
        ) : (
          <>
            <Card title="提交评价" style={{ maxWidth: 640, marginBottom: 24 }}>
              <Form form={form} layout="vertical" onFinish={handleSubmit}>
                <Form.Item label="选择活动" required>
                  <Select
                    placeholder="选择已参与的活动"
                    value={selectedId}
                    onChange={setSelectedId}
                    options={participatable.map(a => ({ label: a.title, value: a.id }))}
                  />
                </Form.Item>

                <Form.Item
                  name="rating"
                  label="评分"
                  rules={[{ required: true, message: '请评分' }]}
                >
                  <Rate />
                </Form.Item>

                <Form.Item
                  name="content"
                  label="评价内容"
                  rules={[{ required: true, message: '请输入评价' }]}
                >
                  <Input.TextArea rows={5} placeholder="分享您的参与体验、建议与感想..." />
                </Form.Item>

                <Button type="primary" htmlType="submit" loading={submitting}>
                  提交评价
                </Button>
              </Form>
            </Card>

            <Card title="我的评价记录">
              {myFeedbacks.length === 0 ? (
                <Text type="secondary">暂无评价记录</Text>
              ) : (
                <List
                  dataSource={myFeedbacks}
                  renderItem={fb => (
                    <List.Item>
                      <List.Item.Meta
                        title={
                          <Space>
                            <span>{fb.activityTitle || '未知活动'}</span>
                            <Tag>{'★'.repeat(fb.rating)}</Tag>
                          </Space>
                        }
                        description={
                          <>
                            <div>{fb.content}</div>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {formatDateTime(fb.createdAt)}
                            </Text>
                          </>
                        }
                      />
                    </List.Item>
                  )}
                />
              )}
            </Card>
          </>
        )}
      </MainLayout>
    </AuthGuard>
  )
}
