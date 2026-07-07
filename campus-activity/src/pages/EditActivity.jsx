import { useParams, useNavigate } from 'react-router-dom'
import {
  Form, Card, Input, Button, Select, DatePicker, Upload, message, Row, Col
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { ACTIVITY_CATEGORIES } from '../data/mockData'

export default function EditActivity() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { activities, updateActivity, currentUser } = useApp()
  const [form] = Form.useForm()

  const activity = activities.find(a => a.id === id)

  if (!activity || activity.organizerId !== currentUser?.id) {
    return (
      <AuthGuard>
        <MainLayout title="编辑活动">
          <Card>活动不存在或无权编辑</Card>
        </MainLayout>
      </AuthGuard>
    )
  }

  const handleSave = (values) => {
    updateActivity(id, {
      title: values.title,
      category: values.category,
      description: values.description,
      location: values.location,
      maxParticipants: values.maxParticipants,
      tags: values.tags || [],
      poster: values.poster?.[0]?.url || values.poster?.[0]?.thumbUrl || activity.poster,
      ...(values.timeRange ? {
        startTime: values.timeRange[0].toDate().toISOString(),
        endTime: values.timeRange[1].toDate().toISOString()
      } : {})
    })
    message.success('活动已更新')
    navigate('/organizer')
  }

  return (
    <AuthGuard>
      <MainLayout title="编辑活动">
        <Card title="编辑活动" style={{ maxWidth: 800 }}>
          <Form
            form={form}
            layout="vertical"
            onFinish={handleSave}
            initialValues={{
              title: activity.title,
              category: activity.category,
              description: activity.description,
              location: activity.location,
              maxParticipants: activity.maxParticipants,
              tags: activity.tags,
              poster: activity.poster
                ? [{ uid: '-1', name: 'poster.jpg', status: 'done', url: activity.poster }]
                : []
            }}
          >
            <Form.Item name="title" label="活动名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="category" label="活动类别" rules={[{ required: true }]}>
                  <Select options={ACTIVITY_CATEGORIES} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="maxParticipants" label="人数上限" rules={[{ required: true }]}>
                  <Input type="number" />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item name="timeRange" label="起止时间">
              <DatePicker.RangePicker showTime style={{ width: '100%' }} />
            </Form.Item>

            <Form.Item name="location" label="活动地点" rules={[{ required: true }]}>
              <Input />
            </Form.Item>

            <Form.Item name="tags" label="活动标签">
              <Select mode="tags" />
            </Form.Item>

            <Form.Item name="description" label="活动简介" rules={[{ required: true }]}>
              <Input.TextArea rows={5} />
            </Form.Item>

            <Form.Item
              name="poster"
              label="活动海报"
              valuePropName="fileList"
              getValueFromEvent={(e) => (Array.isArray(e) ? e : e?.fileList)}
            >
              <Upload
                listType="picture-card"
                maxCount={1}
                beforeUpload={() => false}
                onChange={({ fileList }) => {
                  fileList.forEach(file => {
                    if (file.originFileObj && !file.url) {
                      file.url = URL.createObjectURL(file.originFileObj)
                      file.thumbUrl = file.url
                    }
                  })
                }}
              >
                <div>
                  <PlusOutlined />
                  <div style={{ marginTop: 8 }}>上传海报</div>
                </div>
              </Upload>
            </Form.Item>

            <Button type="primary" htmlType="submit">
              保存修改
            </Button>
          </Form>
        </Card>
      </MainLayout>
    </AuthGuard>
  )
}
