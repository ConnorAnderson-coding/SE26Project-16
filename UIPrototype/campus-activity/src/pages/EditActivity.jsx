import {
  Form,
  Card,
  Input,
  Button
} from 'antd'

export default function EditActivity() {
  return (
    <div className="page">

      <Card title="编辑活动">

        <Form
          layout="vertical"
          initialValues={{
            title: 'AI技术讲座',
            location: '软件大楼'
          }}
        >
          <Form.Item label="活动名称">
            <Input />
          </Form.Item>

          <Form.Item label="活动地点">
            <Input />
          </Form.Item>

          <Form.Item label="活动简介">
            <Input.TextArea rows={5} />
          </Form.Item>

          <Button type="primary">
            保存修改
          </Button>

        </Form>

      </Card>

    </div>
  )
}