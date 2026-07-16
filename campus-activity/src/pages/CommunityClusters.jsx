import { useEffect, useState } from 'react'
import { Card, Table, Tag, Typography, Tooltip, Space } from 'antd'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { getCommunityClusters, getUsers } from '../services/mock/mockApi'

const { Paragraph, Text } = Typography

export default function CommunityClusters() {
  const [clusters, setClusters] = useState([])
  const [users, setUsers] = useState([])

  useEffect(() => {
    getCommunityClusters().then(setClusters)
    getUsers().then(setUsers)
  }, [])

  const tableData = clusters.map(cluster => ({
    key: cluster.id,
    name: cluster.name,
    count: cluster.members.length,
    topInterests: cluster.topInterests.join('、'),
    description: cluster.description
  }))

  const columns = [
    {
      title: '社区名称',
      dataIndex: 'name',
      render: (name, record) => {
        const cluster = clusters.find(c => c.id === record.key)
        return <Tag color={cluster?.color}>{name}</Tag>
      }
    },
    { title: '成员数', dataIndex: 'count' },
    { title: '代表性兴趣', dataIndex: 'topInterests' },
    { title: '描述', dataIndex: 'description' }
  ]

  return (
    <AuthGuard>
      <MainLayout title="社区聚类">
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          以下为用户社区聚类可视化示例，聚类算法由后端实现后将接入真实数据。
        </Paragraph>

        <Card title="聚类分布图" style={{ marginBottom: 24 }}>
          <div className="cluster-chart">
            {clusters.flatMap(cluster =>
              cluster.members.map((member, idx) => {
                const user = users.find(u => u.id === member.userId)
                return (
                  <Tooltip
                    key={`${cluster.id}-${member.userId}-${idx}`}
                    title={
                      <div>
                        <div><Text strong style={{ color: '#fff' }}>{user?.name || member.userId}</Text></div>
                        <div>{user?.college || '-'}</div>
                        <div>兴趣：{(user?.interests || []).join('、') || '暂无'}</div>
                        <div>社区：{cluster.name}</div>
                      </div>
                    }
                  >
                    <div
                      className="cluster-dot"
                      style={{
                        left: `${member.x}%`,
                        top: `${member.y}%`,
                        background: cluster.color
                      }}
                    />
                  </Tooltip>
                )
              })
            )}
          </div>

          <Space wrap style={{ marginTop: 16 }}>
            {clusters.map(cluster => (
              <Tag key={cluster.id} color={cluster.color}>
                {cluster.name}（{cluster.members.length} 人）
              </Tag>
            ))}
          </Space>
        </Card>

        <Card title="聚类详情">
          <Table columns={columns} dataSource={tableData} pagination={false} />
        </Card>
      </MainLayout>
    </AuthGuard>
  )
}
