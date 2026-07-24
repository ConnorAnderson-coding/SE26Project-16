import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Table, Button, Space, Tag, Modal, Form, Input, Upload, message, Spin, Result
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import { getMyActivities } from '../services/activityApi'
import { formatDateTime } from '../data/mockData'

const { TextArea } = Input

export default function OrganizerDashboard() {
  const navigate = useNavigate()
  const { publishRecord } = useApp()
  const [myActivities, setMyActivities] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [recordModal, setRecordModal] = useState(null)
  const [form] = Form.useForm()

  const loadData = () => {
    setLoading(true)
    getMyActivities()
      .then(setMyActivities)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadData()
  }, [])

  const openRecordModal = (activity) => {
    setRecordModal(activity)
    form.setFieldsValue({
      summary: activity.record?.summary || ''
    })
  }

  const handlePublishRecord = async (values) => {
    try {
      const photos = (values.photos || []).map(
        f => f.url || f.thumbUrl || `https://picsum.photos/seed/${f.uid}/400/300`
      )
      await publishRecord(recordModal.id, {
        summary: values.summary,
        photos: photos.length ? photos : [`https://picsum.photos/seed/record${Date.now()}/400/300`]
      })
      message.success('活动记录已发布')
      setRecordModal(null)
      form.resetFields()
      loadData()
    } catch (err) {
      message.error(err.message || '发布失败')
    }
  }

  const columns = [
    { title: '活动名称', dataIndex: 'title' },
    {
      title: '活动时间',
      dataIndex: 'startTime',
      render: t => formatDateTime(t)
    },
    {
      title: '报名人数',
      render: (_, r) => `${r.signupCount}/${r.maxParticipants}`
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: status => (
        <Tag color={status === 'published' ? 'green' : status === 'ended' ? 'default' : 'blue'}>
          {status === 'published' ? '报名中' : status === 'ended' ? '已结束' : status}
        </Tag>
      )
    },
    {
      title: '活动记录',
      render: (_, record) => record.record
        ? <Tag color="success">已发布</Tag>
        : <Tag>未发布</Tag>
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space wrap>
          <Button size="small" onClick={() => navigate(`/edit/${record.id}`)}>
            编辑
          </Button>
          <Button size="small" onClick={() => navigate(`/signup-management?activityId=${record.id}`)}>
            审核报名
          </Button>
          <Button size="small" onClick={() => navigate(`/checkin?activityId=${record.id}`)}>
            签到管理
          </Button>
          {!record.record && (
            <Button size="small" type="primary" onClick={() => openRecordModal(record)}>
              发布记录
            </Button>
          )}
          {record.record && (
            <Button size="small" onClick={() => navigate(`/activity/${record.id}`)}>
              查看记录
            </Button>
          )}
        </Space>
      )
    }
  ]

  return (
    <AuthGuard>
      <MainLayout title="活动管理">
        <Button
          type="primary"
          style={{ marginBottom: 16 }}
          onClick={() => navigate('/create')}
        >
          创建活动
        </Button>

        {loading ? (
          <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
        ) : error ? (
          <Result status="error" title="加载失败" subTitle={error} extra={
            <Button type="primary" onClick={loadData}>重试</Button>
          } />
        ) : (
          <Table
            columns={columns}
            dataSource={myActivities.map(a => ({ ...a, key: a.id }))}
            pagination={{ pageSize: 10 }}
          />
        )}

        <Modal
          title={`发布活动记录 — ${recordModal?.title}`}
          open={!!recordModal}
          onCancel={() => setRecordModal(null)}
          footer={null}
          width={600}
        >
          <Form form={form} layout="vertical" onFinish={handlePublishRecord}>
            <Form.Item
              name="summary"
              label="活动总结"
              rules={[{ required: true, message: '请输入活动总结' }]}
            >
              <TextArea rows={5} placeholder="描述活动过程、成果与感想..." />
            </Form.Item>
            <Form.Item
              name="photos"
              label="活动照片"
              valuePropName="fileList"
              getValueFromEvent={(e) => (Array.isArray(e) ? e : e?.fileList)}
            >
              <Upload
                listType="picture-card"
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
                  <div style={{ marginTop: 8 }}>上传照片</div>
                </div>
              </Upload>
            </Form.Item>
            <Button type="primary" htmlType="submit" block>
              发布活动记录
            </Button>
          </Form>
        </Modal>
      </MainLayout>
    </AuthGuard>
  )
}
