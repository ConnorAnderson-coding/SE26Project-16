import { useEffect, useState } from 'react'
import { Alert, Card, Empty, Space, Spin, Tag, Tooltip, Typography } from 'antd'
import MainLayout from '../layouts/MainLayout'
import AuthGuard from '../components/AuthGuard'
import { getLatestClustering, getMyCommunity } from '../services/communityClusteringApi'

const { Paragraph, Text, Title } = Typography

export default function CommunityClusters() {
  const [latest, setLatest] = useState(null)
  const [mine, setMine] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([getLatestClustering(), getMyCommunity()])
      .then(([latestResult, myResult]) => {
        setLatest(latestResult)
        setMine(myResult)
      })
      .catch(err => setError(err.message || '社区聚类结果加载失败'))
      .finally(() => setLoading(false))
  }, [])

  const membership = mine?.membership

  return (
    <AuthGuard>
      <MainLayout title="社区聚类">
        {loading ? (
          <div className="page-loading"><Spin size="large" tip="正在加载社区结果..." /></div>
        ) : error ? (
          <Alert type="info" showIcon message="暂时没有可展示的社区" description={error} />
        ) : !latest?.communities?.length ? (
          <Card><Empty description="当前还没有社区聚类结果" /></Card>
        ) : (
          <>
            <Card className="community-hero" style={{ marginBottom: 24 }}>
              <Text type="secondary">最新版本 {latest.run.version}</Text>
              <Title level={3} style={{ margin: '8px 0' }}>
                {membership ? `你属于「${membership.communityName}」` : '你暂未进入本次聚类样本'}
              </Title>
              <Paragraph style={{ marginBottom: 0 }}>
                社区由近 180 天的真实活动参与行为与个人画像综合生成。图中的其他点保持匿名。
              </Paragraph>
            </Card>

            <Card title="社区分布" style={{ marginBottom: 24 }}>
              <div className="cluster-chart" aria-label="社区聚类散点图">
                {latest.communities.flatMap(community =>
                  community.points.map(point => (
                    <Tooltip
                      key={point.pointId}
                      title={point.currentUser ? `你 · ${community.name}` : community.name}
                    >
                      <span
                        className={`cluster-dot${point.currentUser ? ' cluster-dot-current' : ''}`}
                        aria-label={point.currentUser ? `你在${community.name}中的位置` : `${community.name}匿名成员`}
                        style={{ left: `${point.x}%`, top: `${point.y}%`, background: community.color }}
                      />
                    </Tooltip>
                  ))
                )}
              </div>
              <Space wrap style={{ marginTop: 16 }}>
                {latest.communities.map(community => (
                  <Tag key={community.communityId} color={community.color}>
                    {community.name}（{community.memberCount} 人）
                  </Tag>
                ))}
              </Space>
            </Card>

            <div className="community-grid">
              {latest.communities.map(community => (
                <Card
                  key={community.communityId}
                  title={<Space><span className="community-swatch" style={{ background: community.color }} />{community.name}</Space>}
                  className={membership?.communityId === community.communityId ? 'community-card-current' : ''}
                >
                  <Paragraph>{community.description || '该社区暂无描述'}</Paragraph>
                  <Space wrap>
                    {(community.topInterests || []).map(interest => <Tag key={interest}>{interest}</Tag>)}
                    {!community.topInterests?.length && <Text type="secondary">暂无代表性兴趣</Text>}
                  </Space>
                </Card>
              ))}
            </div>
          </>
        )}
      </MainLayout>
    </AuthGuard>
  )
}
