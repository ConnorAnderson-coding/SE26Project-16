import { useNavigate } from 'react-router-dom'
import {
  Card, Form, Input, Select, DatePicker, Button, Upload, message, Row, Col
} from 'antd'
import { PlusOutlined, UploadOutlined } from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { ACTIVITY_CATEGORIES } from '../data/mockData'

export default function CreateActivity() {
  const navigate = useNavigate()
  const { createActivity } = useApp()
  const [form] = Form.useForm()

  const handleSubmit = (values) => {
    const activity = {
      title: values.title,
      category: values.category,
      description: values.description,
      startTime: values.timeRange[0].toDate().toISOString(),
      endTime: values.timeRange[1].toDate().toISOString(),
      location: values.location,
      maxParticipants: values.maxParticipants,
      poster: values.poster?.[0]?.url || values.poster?.[0]?.thumbUrl || `https://picsum.photos/seed/${Date.now()}/800/400`,
      tags: values.tags || []
    }
    const id = createActivity(activity)
    message.success('活动发布成功')
    navigate(`/activity/${id}`)
  }

  return (
    <AuthGuard>
      <MainLayout title="发布活动">
        <Card title="活动策划" style={{ maxWidth: 800 }}>
          <Form
            form={form}
            layout="vertical"
            onFinish={handleSubmit}
            initialValues={{ maxParticipants: 50 }}
          >
            <Form.Item
              name="title"
              label="活动名称"
              rules={[{ required: true, message: '请输入活动名称' }]}
            >
              <Input placeholder="输入活动名称" />
            </Form.Item>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="category"
                  label="活动类别"
                  rules={[{ required: true, message: '请选择类别' }]}
                >
                  <Select placeholder="选择类别" options={ACTIVITY_CATEGORIES} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="maxParticipants"
                  label="人数上限"
                  rules={[{ required: true, message: '请输入人数上限' }]}
                >
                  <Input type="number" min={1} />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item
              name="timeRange"
              label="起止时间"
              rules={[{ required: true, message: '请选择活动时间' }]}
            >
              <DatePicker.RangePicker
                showTime
                style={{ width: '100%' }}
                disabledDate={d => d && d.valueOf() < Date.now() - 86400000}
              />
            </Form.Item>

            <Form.Item
              name="location"
              label="活动地点"
              rules={[{ required: true, message: '请输入地点' }]}
            >
              <Input placeholder="如：软件大楼 A101" />
            </Form.Item>

            <Form.Item name="tags" label="活动标签">
              <Select mode="tags" placeholder="输入标签，回车添加" />
            </Form.Item>

            <Form.Item
              name="description"
              label="活动内容"
              rules={[{ required: true, message: '请输入活动简介' }]}
            >
              <Input.TextArea rows={6} placeholder="详细描述活动内容、流程、注意事项等" />
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

            <Form.Item>
              <Button type="primary" htmlType="submit" icon={<UploadOutlined />}>
                发布活动
              </Button>
            </Form.Item>
          </Form>
        </Card>
      </MainLayout>
    </AuthGuard>
  )
}
