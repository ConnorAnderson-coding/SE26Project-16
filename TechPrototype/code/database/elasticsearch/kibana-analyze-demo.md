# Kibana Dev Tools - IK 分词演示

在 Kibana 打开 **Dev Tools**（http://localhost:5601/app/dev_tools），依次执行以下请求。

## 1. 查看索引文档数

```json
GET campus_activities/_count
```

## 2. 索引时分词（ik_max_analyzer）

对活动标题做**最大粒度**分词，用于写入倒排索引：

```json
POST campus_activities/_analyze
{
  "analyzer": "ik_max_analyzer",
  "text": "AI 与大模型技术前沿讲座"
}
```

预期可看到：`AI`、`大模型`、`技术`、`前沿`、`讲座` 等 token。

## 3. 搜索时分词（ik_smart_analyzer）

对用户查询做**智能粗粒度**分词：

```json
POST campus_activities/_analyze
{
  "analyzer": "ik_smart_analyzer",
  "text": "大模型讲座"
}
```

## 4. 对比：标准 analyzer（无 IK）

```json
POST _analyze
{
  "analyzer": "standard",
  "text": "AI 与大模型技术前沿讲座"
}
```

中文会被整句或按字符切分，检索效果较差。

## 5. 使用字段 mapping 分词

```json
GET campus_activities/_analyze
{
  "field": "title",
  "text": "校园羽毛球友谊赛"
}
```

## 6. BM25 关键词检索验证

```json
GET campus_activities/_search
{
  "query": {
    "multi_match": {
      "query": "大模型",
      "fields": ["title^3", "description"]
    }
  }
}
```

```json
GET campus_activities/_search
{
  "query": {
    "multi_match": {
      "query": "羽毛球",
      "fields": ["title^3", "description", "tags"]
    }
  }
}
```

## 7. 查看某条文档的分词结果（term vectors）

```json
GET campus_activities/_termvectors/1
{
  "fields": ["title", "description"]
}
```

## 8. Analyze 界面（可视化）

1. 打开 Kibana → **Search** → **Elasticsearch** → **Analyze**
2. Index 选择 `campus_activities`
3. Field 选择 `title` 或 `description`
4. 输入：`周末放松的羽毛球友谊赛`
5. 对比 `ik_max_analyzer` 与 `ik_smart_analyzer` 的分词结果
