export const ACTIVITY_CATEGORIES = [
  { label: '学术讲座', value: 'academic' },
  { label: '体育运动', value: 'sports' },
  { label: '社团活动', value: 'club' },
  { label: '文艺表演', value: 'arts' },
  { label: '志愿服务', value: 'volunteer' },
  { label: '创新创业', value: 'innovation' }
]

export const COLLEGES = [
  '软件学院',
  '计算机学院',
  '信息学院',
  '电子学院',
  '数学学院',
  '外国语学院'
]

export const INTEREST_TAGS = [
  'AI', '摄影', '羽毛球', '篮球', '编程', '音乐',
  '舞蹈', '志愿服务', '创业', '阅读', '电竞', '旅行'
]

export const initialUsers = [
  {
    id: '524030910001',
    password: '123456',
    name: '张三',
    role: 'student',
    college: '软件学院',
    grade: '2024级',
    interests: ['AI', '摄影', '羽毛球'],
    availableTime: ['weekday_evening', 'weekend'],
    friends: ['524030910002', '524030910003']
  },
  {
    id: '524030910002',
    password: '123456',
    name: '李四',
    role: 'student',
    college: '计算机学院',
    grade: '2023级',
    interests: ['编程', '电竞', '篮球'],
    availableTime: ['weekend'],
    friends: ['524030910001']
  },
  {
    id: 'T001',
    password: '123456',
    name: '王老师',
    role: 'teacher',
    college: '软件学院',
    grade: '教师',
    interests: ['AI', '创业'],
    availableTime: ['weekday_morning', 'weekday_afternoon'],
    friends: []
  },
  {
    id: 'admin001',
    password: '123456',
    name: '系统管理员',
    role: 'admin',
    college: '软件学院',
    grade: '管理员',
    interests: [],
    availableTime: [],
    friends: []
  }
]

export const initialActivities = [
  {
    id: '1',
    title: 'AI 与大模型技术前沿讲座',
    category: 'academic',
    description: '本次讲座邀请业界专家，介绍大模型在软件工程、教育等领域的最新应用与发展趋势，适合对 AI 感兴趣的同学参加。',
    startTime: '2026-07-15T14:00:00',
    endTime: '2026-07-15T16:00:00',
    location: '软件大楼 A101',
    organizerId: 'T001',
    organizerName: '王老师',
    college: '软件学院',
    poster: 'https://picsum.photos/seed/ai-lecture/800/400',
    maxParticipants: 120,
    signupCount: 85,
    favoriteCount: 42,
    status: 'published',
    tags: ['AI', '编程'],
    checkInCode: 'AI2026',
    record: null,
    shareStats: { wechat: 45, poster: 12, friend: 8, list: 23 }
  },
  {
    id: '2',
    title: '校园羽毛球友谊赛',
    category: 'sports',
    description: '面向全校师生的羽毛球双打友谊赛，按学院分组，优胜队伍将获得精美奖品。请提前热身，穿运动服参赛。',
    startTime: '2026-07-20T09:00:00',
    endTime: '2026-07-20T12:00:00',
    location: '体育馆羽毛球场',
    organizerId: '524030910002',
    organizerName: '李四',
    college: '计算机学院',
    poster: 'https://picsum.photos/seed/badminton/800/400',
    maxParticipants: 64,
    signupCount: 43,
    favoriteCount: 28,
    status: 'published',
    tags: ['羽毛球', '体育运动'],
    checkInCode: 'BD2026',
    record: null
  },
  {
    id: '3',
    title: '摄影社户外采风活动',
    category: 'club',
    description: '摄影社组织校园及周边人文采风，专业学长带队讲解构图与后期技巧，欢迎零基础同学加入。',
    startTime: '2026-07-18T08:00:00',
    endTime: '2026-07-18T17:00:00',
    location: '图书馆前广场集合',
    organizerId: '524030910001',
    organizerName: '张三',
    college: '软件学院',
    poster: 'https://picsum.photos/seed/photo-club/800/400',
    maxParticipants: 30,
    signupCount: 22,
    favoriteCount: 35,
    status: 'published',
    tags: ['摄影', '艺术'],
    checkInCode: 'PH2026',
    record: null,
    shareStats: { wechat: 28, poster: 18, friend: 15, list: 12 }
  },
  {
    id: '4',
    title: '程序设计竞赛训练营',
    category: 'innovation',
    description: '为期一周的算法与数据结构强化训练，涵盖动态规划、图论等高频考点，为 ACM/ICPC 及各类编程竞赛做准备。',
    startTime: '2026-07-22T19:00:00',
    endTime: '2026-07-29T21:00:00',
    location: '计算机楼 302 实验室',
    organizerId: 'T001',
    organizerName: '王老师',
    college: '计算机学院',
    poster: 'https://picsum.photos/seed/coding/800/400',
    maxParticipants: 50,
    signupCount: 38,
    favoriteCount: 56,
    status: 'published',
    tags: ['编程', 'AI'],
    checkInCode: 'CP2026',
    record: null,
    shareStats: { wechat: 32, poster: 20, friend: 10, list: 18 }
  },
  {
    id: '5',
    title: '校园志愿者招募 — 社区服务日',
    category: 'volunteer',
    description: '组织同学前往周边社区开展助老、环境清洁等志愿服务，可计入志愿服务时长，欢迎热心公益的同学报名。',
    startTime: '2026-07-25T08:30:00',
    endTime: '2026-07-25T16:00:00',
    location: '校门口集合',
    organizerId: '524030910001',
    organizerName: '张三',
    college: '软件学院',
    poster: 'https://picsum.photos/seed/volunteer/800/400',
    maxParticipants: 40,
    signupCount: 31,
    favoriteCount: 19,
    status: 'published',
    tags: ['志愿服务'],
    checkInCode: 'VL2026',
    record: {
      summary: '本次活动共有 31 名志愿者参与，服务时长累计 248 小时，获得社区居民一致好评。',
      photos: [
        'https://picsum.photos/seed/vol1/400/300',
        'https://picsum.photos/seed/vol2/400/300',
        'https://picsum.photos/seed/vol3/400/300'
      ],
      publishedAt: '2026-07-26T10:00:00'
    },
    shareStats: { wechat: 20, poster: 8, friend: 6, list: 15 }
  },
  {
    id: '6',
    title: '校园音乐节 — 夏日之声',
    category: 'arts',
    description: '各社团及个人歌手同台演出，涵盖流行、摇滚、民谣等多种风格，现场还有互动抽奖环节。',
    startTime: '2026-06-28T18:30:00',
    endTime: '2026-06-28T21:30:00',
    location: '中心广场',
    organizerId: '524030910002',
    organizerName: '李四',
    college: '信息学院',
    poster: 'https://picsum.photos/seed/music/800/400',
    maxParticipants: 500,
    signupCount: 412,
    favoriteCount: 198,
    status: 'ended',
    tags: ['音乐', '文艺'],
    checkInCode: 'MU2026',
    record: {
      summary: '音乐节圆满落幕，12 支乐队和 8 位独立歌手带来精彩演出，现场观众超过 800 人。',
      photos: [
        'https://picsum.photos/seed/music1/400/300',
        'https://picsum.photos/seed/music2/400/300'
      ],
      publishedAt: '2026-06-29T09:00:00'
    },
    shareStats: { wechat: 120, poster: 45, friend: 38, list: 55 }
  }
]

export const initialSignups = [
  { id: 's1', activityId: '1', userId: '524030910001', status: 'approved', createdAt: '2026-07-01T10:00:00' },
  { id: 's2', activityId: '2', userId: '524030910001', status: 'pending', createdAt: '2026-07-02T14:00:00' },
  { id: 's3', activityId: '3', userId: '524030910002', status: 'approved', createdAt: '2026-07-03T09:00:00' },
  { id: 's4', activityId: '1', userId: '524030910002', status: 'pending', createdAt: '2026-07-04T11:00:00' },
  { id: 's5', activityId: '3', userId: '524030910001', status: 'approved', createdAt: '2026-07-05T08:00:00' },
  { id: 's6', activityId: '3', userId: 'T001', status: 'approved', createdAt: '2026-07-05T09:00:00' },
  { id: 's7', activityId: '5', userId: '524030910002', status: 'approved', createdAt: '2026-07-10T10:00:00' },
  { id: 's8', activityId: '5', userId: '524030910001', status: 'approved', createdAt: '2026-07-11T11:00:00' },
  { id: 's9', activityId: '6', userId: '524030910002', status: 'approved', createdAt: '2026-06-20T10:00:00' },
  { id: 's10', activityId: '4', userId: '524030910001', status: 'approved', createdAt: '2026-07-12T14:00:00' },
  { id: 's11', activityId: '4', userId: '524030910002', status: 'pending', createdAt: '2026-07-13T09:00:00' }
]

export const initialFavorites = [
  { userId: '524030910001', activityId: '1' },
  { userId: '524030910001', activityId: '4' },
  { userId: '524030910002', activityId: '2' }
]

export const initialFeedbacks = [
  {
    id: 'f1',
    activityId: '6',
    userId: '524030910001',
    userName: '张三',
    rating: 5,
    content: '演出非常精彩，氛围很好，希望每年都能举办！',
    createdAt: '2026-06-29T12:00:00'
  },
  {
    id: 'f2',
    activityId: '6',
    userId: '524030910002',
    userName: '李四',
    rating: 4,
    content: '现场音响效果不错，建议明年增加互动环节。',
    createdAt: '2026-06-29T14:00:00'
  },
  {
    id: 'f3',
    activityId: '3',
    userId: '524030910002',
    userName: '李四',
    rating: 5,
    content: '采风活动组织得很好，学到了很多摄影技巧。',
    createdAt: '2026-07-19T10:00:00'
  }
]

export const initialCheckIns = [
  { id: 'c1', activityId: '6', userId: '524030910001', method: 'qrcode', time: '2026-06-28T18:45:00' },
  { id: 'c2', activityId: '6', userId: '524030910002', method: 'password', time: '2026-06-28T18:50:00' },
  { id: 'c3', activityId: '3', userId: '524030910001', method: 'location', time: '2026-07-18T08:15:00' },
  { id: 'c4', activityId: '3', userId: '524030910002', method: 'qrcode', time: '2026-07-18T08:20:00' },
  { id: 'c5', activityId: '5', userId: '524030910001', method: 'qrcode', time: '2026-07-25T08:45:00' },
  { id: 'c6', activityId: '5', userId: '524030910002', method: 'location', time: '2026-07-25T09:00:00' }
]

export const AVAILABLE_TIME_OPTIONS = [
  { label: '工作日上午', value: 'weekday_morning' },
  { label: '工作日下午', value: 'weekday_afternoon' },
  { label: '工作日晚间', value: 'weekday_evening' },
  { label: '周末', value: 'weekend' }
]

export const SHARE_CHANNEL_LABELS = {
  wechat: '微信分享',
  poster: '海报张贴',
  friend: '好友推荐',
  list: '活动列表'
}

export const initialCommunityClusters = [
  {
    id: 'cluster-tech',
    name: '科创达人',
    description: '偏好编程、AI、创新创业类活动',
    color: '#1677ff',
    topInterests: ['AI', '编程', '创业'],
    members: [
      { userId: '524030910001', x: 68, y: 72 },
      { userId: '524030910002', x: 74, y: 65 },
      { userId: 'T001', x: 62, y: 58 }
    ]
  },
  {
    id: 'cluster-sports',
    name: '运动健将',
    description: '热衷体育运动与户外竞技',
    color: '#52c41a',
    topInterests: ['羽毛球', '篮球', '电竞'],
    members: [
      { userId: '524030910002', x: 35, y: 42 }
    ]
  },
  {
    id: 'cluster-arts',
    name: '文艺青年',
    description: '关注摄影、音乐与艺术活动',
    color: '#eb2f96',
    topInterests: ['摄影', '音乐', '舞蹈'],
    members: [
      { userId: '524030910001', x: 28, y: 78 }
    ]
  },
  {
    id: 'cluster-volunteer',
    name: '公益先锋',
    description: '积极参与志愿服务与社区活动',
    color: '#fa8c16',
    topInterests: ['志愿服务', '阅读'],
    members: [
      { userId: '524030910001', x: 82, y: 28 },
      { userId: 'T001', x: 88, y: 35 }
    ]
  }
]

export function getCategoryLabel(value) {
  return ACTIVITY_CATEGORIES.find(c => c.value === value)?.label || value
}

export function formatDateTime(isoString) {
  if (!isoString) return '-'
  const d = new Date(isoString)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

export function formatDateRange(start, end) {
  return `${formatDateTime(start)} — ${formatDateTime(end)}`
}
