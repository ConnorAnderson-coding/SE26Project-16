import { Menu } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function Navbar() {

  const navigate = useNavigate()

  return (
    <Menu
      mode="horizontal"
      items={[
        {
          key: 'home',
          label: '首页'
        },
        {
          key: 'activities',
          label: '活动列表'
        },
        {
          key: 'my',
          label: '我的活动'
        },
        {
          key: 'profile',
          label: '个人中心'
        }
      ]}
      onClick={(e) => {
        navigate(`/${e.key}`)
      }}
    />
  )
}