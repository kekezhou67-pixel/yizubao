export const navigation = [
  {
    id: 'find', label: '找房', title: '把日子，安放在喜欢的地方。',
    intro: '通勤的距离、窗外的风景，和刚刚好的租金。慢慢挑，总有适合你的那一间。',
    image: '/house-images/uns_1600210492486-724fe5c67fb0.jpg',
    caption: '有阳光，有绿意，也有自己的生活。',
    links: [
      { label: '先看看，有哪些好房？', name: '全部房源', to: '/houses' },
      { label: '把通勤时间，留给自己。', name: '地铁找房', to: '/app/subway' },
      { label: '说说你想住的地方。', name: '小易工作台', to: '/app/chat' }
    ]
  },
  {
    id: 'ai', label: '小易服务', title: '租房的事，交给小易。',
    intro: '一个工作台，找房、查资料、看合同、算费用。上传文件或直接说需求，不用切换助手。',
    image: '/images/moving-day.png',
    caption: '小一点的房间，也能装下喜欢的生活。',
    links: [
      { label: '想住得离公司近一点？', name: '小易工作台', to: '/app/chat' },
      { label: '喜欢这样的房间？', name: '上传房间照片 · 在小易中处理', to: '/app/chat?task=image', image:'/house-images/uns_1616046229478-9901c5536a45.jpg', caption:'把喜欢的样子，变成找房的线索。' },
      { label: '押金和维修，该谁负责？', name: '带着问题问小易 · 自动检索资料', to: '/app/chat?task=policy', image:'/house-images/uns_1484154218962-a197022b5858.jpg', caption:'关于家的小问题，也值得认真回答。' },
      { label: '签字之前，再看一遍。', name: '上传或粘贴合同 · 在小易中审查', to: '/app/chat?task=contract', image:'/house-images/uns_1522708323590-d24dbb6b0267.jpg', caption:'把条款看明白，让新生活更安心。' }
    ]
  },
  {
    id: 'guide', label: '租房指南', title: '住得安心，也住得明白。',
    intro: '从第一次看房到正式入住，把租房路上的小事，一件件弄清楚。',
    image: '/house-images/uns_1484154218962-a197022b5858.jpg',
    caption: '关于生活，总有值得分享的小经验。',
    links: [
      { label: '看房之前，做一点功课。', name: '租房攻略与社区', to: '/app/community' },
      { label: '搬一次家，要花多少钱？', name: '租房费用计算器', to: '/app/tools?tool=calculator' },
      { label: '遇到租房问题，有据可查。', name: '政策问答', to: '/app/chat?task=policy' }
    ]
  }
]

export function navigationGroup(path) {
  if (path === '/houses' || path.startsWith('/detail') || path === '/app/subway') return 'find'
  if (['/app/chat', '/app/image-search', '/app/rag-qa', '/app/tools', '/app/kb-admin'].includes(path)) return 'ai'
  if (path.includes('community') || path.includes('guide-detail')) return 'guide'
  return path === '/' ? 'home' : null
}

#123