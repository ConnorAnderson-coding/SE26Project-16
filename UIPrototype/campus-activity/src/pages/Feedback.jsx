import { useState } from 'react'
import { Card, Form, Input, Rate, Button, Select, message, List, Tag, Typography, Space } from 'antd'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { formatDateTime } from '../data/mockData'

const { Text } = Typography

export default function Feedback() {
  const { currentUser, activities, signups, feedbacks, submitFeedback } = useApp()
  const [form] = Form.useForm()
  const [selectedId, setSelectedId] = useState(null)

  const participatable = signups
    .filter(s => s.userId === currentUser?.id && s.status === 'approved')
    .map(s => activities.find(a => a.id === s.activityId))
    .filter(a => a && (a.status === 'ended' || new Date(a.endTime) < new Date()))

  const myFeedbacks = feedbacks.filter(f => f.userId === currentUser?.id)

  const handleSubmit = (values) => {
    if (!selectedId) {
      message.warning('请选择要评价的活动')
      return
    }
    const result = submitFeedback({
      activityId: selectedId,
      rating: values.rating,
      content: values.content
    })
    result.success ? message.success(result.message) : message.warning(result.message)
    form.resetFields()
  }

  return (
    <AuthGuard>
      <MainLayout title="活动反馈">
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

            <Button type="primary" htmlType="submit">
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
              renderItem={fb => {
                const activity = activities.find(a => a.id === fb.activityId)
                return (
                  <List.Item>
                    <List.Item.Meta
                      title={
                        <Space>
                          <span>{activity?.title || '未知活动'}</span>
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
                )
              }}
            />
          )}
        </Card>
      </MainLayout>
    </AuthGuard>
  )
}