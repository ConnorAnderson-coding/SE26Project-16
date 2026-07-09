import { useState } from 'react'
import {
  Card, Form, Input, Select, Tag, Button, message, Divider, Typography, Space
} from 'antd'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { useApp } from '../context/AppContext'
import {
  COLLEGES, INTEREST_TAGS, AVAILABLE_TIME_OPTIONS
} from '../data/mockData'

const { Title, Text } = Typography

export default function UserProfile() {
  const { currentUser, updateProfile } = useApp()
  const [editing, setEditing] = useState(false)
  const [form] = Form.useForm()

  const startEdit = () => {
    form.setFieldsValue({
      name: currentUser.name,
      college: currentUser.college,
      grade: currentUser.grade,
      interests: currentUser.interests,
      availableTime: currentUser.availableTime
    })
    setEditing(true)
  }

  const handleSave = (values) => {
    updateProfile(values)
    message.success('个人档案已更新')
    setEditing(false)
  }

  const getTimeLabel = (val) =>
    AVAILABLE_TIME_OPTIONS.find(o => o.value === val)?.label || val

  return (
    <AuthGuard>
      <MainLayout title="个人中心">
        <Card
          title="个人档案"
          extra={
            !editing && (
              <Button type="primary" onClick={startEdit}>编辑档案</Button>
            )
          }
          style={{ maxWidth: 720 }}
        >
          {!editing ? (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <div>
                <Text type="secondary">学号/工号</Text>
                <Title level={5} style={{ margin: '4px 0' }}>{currentUser.id}</Title>
              </div>
              <div>
                <Text type="secondary">姓名</Text>
                <Title level={5} style={{ margin: '4px 0' }}>{currentUser.name}</Title>
              </div>
              <div>
                <Text type="secondary">身份</Text>
                <div style={{ marginTop: 4 }}>
                  <Tag color={currentUser.role === 'teacher' ? 'purple' : 'blue'}>
                    {currentUser.role === 'teacher' ? '教师' : '学生'}
                  </Tag>
                </div>
              </div>
              <div>
                <Text type="secondary">学院</Text>
                <Title level={5} style={{ margin: '4px 0' }}>{currentUser.college}</Title>
              </div>
              <div>
                <Text type="secondary">年级</Text>
                <Title level={5} style={{ margin: '4px 0' }}>{currentUser.grade}</Title>
              </div>
              <Divider />
              <div>
                <Text type="secondary">兴趣标签</Text>
                <div style={{ marginTop: 8 }}>
                  {(currentUser.interests || []).map(tag => (
                    <Tag key={tag} color="processing">{tag}</Tag>
                  ))}
                  {!(currentUser.interests?.length) && <Text type="secondary">暂未设置</Text>}
                </div>
              </div>
              <div>
                <Text type="secondary">可参与时间</Text>
                <div style={{ marginTop: 8 }}>
                  {(currentUser.availableTime || []).map(t => (
                    <Tag key={t}>{getTimeLabel(t)}</Tag>
                  ))}
                  {!(currentUser.availableTime?.length) && <Text type="secondary">暂未设置</Text>}
                </div>
              </div>
            </Space>
          ) : (
            <Form form={form} layout="vertical" onFinish={handleSave}>
              <Form.Item name="name" label="姓名" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
              <Form.Item name="college" label="学院" rules={[{ required: true }]}>
                <Select options={COLLEGES.map(c => ({ label: c, value: c }))} />
              </Form.Item>
              <Form.Item name="grade" label="年级" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
              <Form.Item name="interests" label="兴趣标签">
                <Select
                  mode="multiple"
                  options={INTEREST_TAGS.map(t => ({ label: t, value: t }))}
                />
              </Form.Item>
              <Form.Item name="availableTime" label="可参与时间">
                <Select mode="multiple" options={AVAILABLE_TIME_OPTIONS} />
              </Form.Item>
              <Space>
                <Button type="primary" htmlType="submit">保存</Button>
                <Button onClick={() => setEditing(false)}>取消</Button>
              </Space>
            </Form>
          )}
        </Card>
      </MainLayout>
    </AuthGuard>
  )
}
