import { Menu } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function SideMenu() {

  const navigate = useNavigate()

  return (
    <Menu
      mode="inline"
      style={{ height: '100%' }}
      items={[
        {
          key: 'home',
          label: '首页'
        },
        {
          key: 'create',
          label: '发布活动'
        },
        {
          key: 'my',
          label: '我的活动'
        },
        {
          key: 'profile',
          label: '个人中心'
        },
        {
          key: 'organizer',
          label: '活动管理'
        }
      ]}
      onClick={(e) => {
        navigate(`/${e.key}`)
      }}
    />
  )
}