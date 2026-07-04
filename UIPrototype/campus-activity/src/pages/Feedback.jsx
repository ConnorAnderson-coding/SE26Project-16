import {
  Card,
  Input,
  Rate,
  Button
} from 'antd'

export default function Feedback() {
  return (
    <div className="page">

      <Card title="活动评价">

        <Rate />

        <br />
        <br />

        <Input.TextArea
          rows={5}
          placeholder="请输入评价内容"
        />

        <br />
        <br />

        <Button type="primary">
          提交评价
        </Button>

      </Card>

    </div>
  )
}