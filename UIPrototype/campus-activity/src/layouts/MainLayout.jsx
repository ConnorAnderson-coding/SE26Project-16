import SideMenu from '../components/SideMenu'

export default function MainLayout({ children }) {
  return (
    <div
      style={{
        display: 'flex',
        minHeight: '100vh'
      }}
    >
      <div
        style={{
          width: 220,
          background: '#fff',
          borderRight: '1px solid #eee'
        }}
      >
        <SideMenu />
      </div>

      <div
        style={{
          flex: 1,
          padding: 24
        }}
      >
        {children}
      </div>
    </div>
  )
}