import {
  Card,
  Form,
  Input,
  Select,
  DatePicker,
  Button
} from 'antd'

export default function CreateActivity() {
  return (
    <div className="page">

      <Card title="发布活动">

        <Form layout="vertical">

          <Form.Item label="活动名称">
            <Input />
          </Form.Item>

          <Form.Item label="活动类别">
            <Select
              options={[
                {
                  label: '学术',
                  value: 'academic'
                },
                {
                  label: '体育',
                  value: 'sports'
                },
                {
                  label: '社团',
                  value: 'club'
                }
              ]}
            />
          </Form.Item>

          <Form.Item label="活动时间">
            <DatePicker showTime />
          </Form.Item>

          <Form.Item label="活动地点">
            <Input />
          </Form.Item>

          <Form.Item label="活动简介">
            <Input.TextArea rows={5} />
          </Form.Item>

          <Button type="primary">
            发布活动
          </Button>

        </Form>

      </Card>

    </div>
  )
}