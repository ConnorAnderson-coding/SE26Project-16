import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Form, Card, Input, Button, Select, DatePicker, Upload, message, Row, Col, Spin, InputNumber
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import MapLocationPicker from '../components/MapLocationPicker'
import { useApp } from '../context/AppContext'
import { getActivityById } from '../services/activityApi'
import { ACTIVITY_CATEGORIES } from '../data/mockData'

export default function EditActivity() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { updateActivity, currentUser } = useApp()
  const [form] = Form.useForm()
  const [activity, setActivity] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const latitude = Form.useWatch('latitude', form)
  const longitude = Form.useWatch('longitude', form)

  useEffect(() => {
    getActivityById(id)
      .then(setActivity)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }, [id])

  if (loading) {
    return (
      <AuthGuard>
        <MainLayout title="编辑活动">
          <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
        </MainLayout>
      </AuthGuard>
    )
  }

  if (error || !activity || activity.organizerId !== currentUser?.id) {
    return (
      <AuthGuard>
        <MainLayout title="编辑活动">
          <Card>{error || '活动不存在或无权编辑'}</Card>
        </MainLayout>
      </AuthGuard>
    )
  }

  const handleSave = async (values) => {
    try {
      await updateActivity(id, {
        title: values.title,
        category: values.category,
        description: values.description,
        location: values.location,
        maxParticipants: Number(values.maxParticipants),
        latitude: values.latitude ?? null,
        longitude: values.longitude ?? null,
        checkInRadiusMeters: values.checkInRadiusMeters ? Number(values.checkInRadiusMeters) : 200,
        tags: values.tags || [],
        poster: values.poster?.[0]?.url || values.poster?.[0]?.thumbUrl || activity.poster,
        startTime: values.timeRange[0].toDate().toISOString(),
        endTime: values.timeRange[1].toDate().toISOString()
      })
      message.success('活动已更新')
      navigate('/organizer')
    } catch (err) {
      message.error(err.message || '更新失败')
    }
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
              latitude: activity.latitude,
              longitude: activity.longitude,
              checkInRadiusMeters: activity.checkInRadiusMeters || 200,
              maxParticipants: activity.maxParticipants,
              tags: activity.tags,
              timeRange: [dayjs(activity.startTime), dayjs(activity.endTime)],
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

            <Form.Item name="timeRange" label="起止时间" rules={[{ required: true }]}>
              <DatePicker.RangePicker showTime style={{ width: '100%' }} />
            </Form.Item>

            <Form.Item name="location" label="活动地点" rules={[{ required: true }]}>
              <Input />
            </Form.Item>

            <Form.Item label="地图选点">
              <MapLocationPicker
                value={{ latitude, longitude }}
                onChange={({ latitude: nextLatitude, longitude: nextLongitude }) => {
                  form.setFieldsValue({
                    latitude: nextLatitude,
                    longitude: nextLongitude
                  })
                }}
              />
            </Form.Item>

            <Row gutter={16}>
              <Col xs={24} md={8}>
                <Form.Item name="latitude" label="签到纬度">
                  <InputNumber min={-90} max={90} precision={6} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="longitude" label="签到经度">
                  <InputNumber min={-180} max={180} precision={6} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="checkInRadiusMeters" label="签到半径（米）">
                  <InputNumber min={1} precision={0} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>

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
