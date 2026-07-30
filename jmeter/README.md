# JMeter 压测说明（校园活动一站式服务平台）

针对云端部署 `http://10.119.13.196/`（API：`/api/v1`）的性能与并发正确性测试。

## 交付文件

| 文件 | 说明 |
|------|------|
| [campus-activity-perf.jmx](campus-activity-perf.jmx) | 完整测试计划（JMeter 5.x） |
| [users.csv](users.csv) | 120 个 seed 学生账号（密码均为 `123456`） |
| [generate_jmx.py](generate_jmx.py) | 重新生成 `.jmx` 的脚本（改场景时用） |
| [results/](results/) | 建议存放 `.jtl` / HTML 报告（已 gitignore） |

> 仓库内 [`deploy/perf-smoke.ps1`](../deploy/perf-smoke.ps1) 仅作简易冒烟参考，且请求体误用字段 `username`（正确字段为 **`userId`**）。请以本目录 JMeter 计划为准。

## 环境要求

- Apache JMeter **5.5+**（含 JSON Path Assertion / JSR223 Groovy）
- 能访问被测主机：默认 `HOST=10.119.13.196`
- 云端需已部署且演示/组织者账号可用：`T001` / `123456`（创建压测活动）

## 验收口径

| 类型 | 判定 |
|------|------|
| 普通接口（TG1 / TG2） | **100 并发**；采样器挂 **Duration Assertion 3000ms**；建议报告中 **p95 &lt; 3000ms** 且错误率 **&lt; 1%** |
| 语义检索 / 推荐（TG3） | **100 并发**，**单独计量**，**不**套用 3s 失败断言；记录 avg / p95 / max |
| 报名竞态（TG4） | 100 人抢 `maxParticipants=50` 的活动：业务成功次数 **= 50**，最终 `signupCount=50`，无 5xx |
| 收藏竞态（TG5） | 100 人各 toggle 一次：`favoriteCount` 增量应 **= 100**；若不一致记为潜在并发问题 |
| 重复报名（TG6） | **100 并发**；同一用户二次报名不得成功（无意外 `code==0`） |

当前云端活动量约 **1.5k** 级（低于需求文案「≥10k」）。压测可先跑；若课程硬性要求 10k 数据，需先在云端扩数据后再复测。

## 快速运行（非 GUI）

在 **`jmeter/` 目录下**执行（保证 `users.csv` 相对路径可解析）：

```bash
cd jmeter
jmeter -n -t campus-activity-perf.jmx -l results/run1.jtl -e -o results/html
```

Windows（已把 `jmeter` 加入 PATH 时）：

```powershell
cd jmeter
jmeter -n -t campus-activity-perf.jmx -l results/run1.jtl -e -o results/html
```

打开 `results/html/index.html` 查看 Aggregate / 百分位。

## 关键配置

在测试计划 **User Defined Variables**（或命令行 `-J`）中可改：

| 变量 | 默认 | 含义 |
|------|------|------|
| `PROTOCOL` | `http` | 协议 |
| `HOST` | `10.119.13.196` | 主机 |
| `PORT` | （空） | 端口；非 80/443 时填写 |
| `API_PREFIX` | `/api/v1` | API 前缀 |
| `PASSWORD` | `123456` | 压测密码 |
| `ORGANIZER_ID` | `T001` | Setup 创建活动的组织者 |
| `FALLBACK_ACTIVITY_ID` | `1` | Setup 未跑时详情接口回退 ID |
| `MAX_PARTICIPANTS` | `50` | 报名竞态名额（需与 Setup 创建一致） |

示例：

```bash
jmeter -n -t campus-activity-perf.jmx -JHOST=10.119.13.196 -l results/run1.jtl -e -o results/html
```

> 线程数在各 Thread Group 内写死为计划值（TG1～TG6 业务组均为 **100** 并发；TG0/TearDown 为 1）。改并发请在 GUI 中编辑对应线程组，或改 `generate_jmx.py` 后重新生成。

## 线程组与推荐跑法

测试计划开启了 **Run Thread Groups consecutively（串行）**，顺序为：

```text
TG0 Setup → TG1 → TG2 → TG3 → TG4 → TG5 → TG6 → TearDown Verify
```

| 组 | 作用 | 默认 |
|----|------|------|
| **TG0 Setup** | 组织者登录；创建 cap=50 报名活动 + 收藏竞态活动；写入 `TEST_ACTIVITY_ID` / `FAVORITE_ACTIVITY_ID` | 启用 |
| **TG1 Baseline** | 登录 + 读接口（me / stats / 列表·关键词 / 详情 / 报名·收藏·签到·反馈 / 聚类 / mine），**100 并发 + 3s 断言** | 启用 |
| **TG2 Mixed** | 典型浏览路径（无推荐），**100 并发 + 3s 断言** | 启用 |
| **TG3 Slow** | hybrid / semantic / keyword 检索 + `recommended`，**100 并发，无 3s 硬失败** | 启用 |
| **TG4 Race** | 100 用户抢报同一活动 | 启用 |
| **TG5 Favorite** | 100 用户各收藏一次 | 启用 |
| **TG6 Dup** | 100 用户重复报名防护 | 启用 |
| **TearDown** | 核对 `signupCount` / `favoriteCount` 与计数器 | 启用 |

### 场景开关建议

1. **只做 SLA 性能**：启用 TG0 + TG1（+ 可选 TG2）；**禁用** TG3～TG6 与 TearDown（或保留 TearDown，其会因计数为 0 自动跳过竞态断言）。
2. **只观测慢接口**：TG0 + TG3。
3. **只做并发正确性**：TG0 + TG4 + TG5（+ TG6）+ TearDown；建议业务低峰、独占执行。
4. **全量**：保持默认全部启用（耗时长，且 TG1+TG2 会对后端造成较大读压力）。

GUI：选中线程组 → 右键 → Enable / Disable。

## 鉴权与请求约定

1. `POST /api/v1/auth/login`，Body：`{"userId":"...","password":"123456"}`
2. 提取 `$.data.token`，后续请求头：`Authorization: Bearer <token>`
3. 成功业务响应：`{"code":0,"message":"...","data":...}`
4. 报名已满 / 已报名等为**业务失败**（通常 HTTP 4xx + `code!=0`）。TG4/TG6 中 JSR223 会将其计为预期失败，**不**一律记为采样错误。

## 并发正确性如何解读

### TG4 报名名额

对应后端 `RegistrationService.signup`（`SELECT ... FOR UPDATE` + `signupCount` + 唯一约束 `uk_reg_activity_user`）。

- 期望：`signupSuccess == 50`，活动详情 `signupCount == 50`，`signupHttpError == 0`
- 失败含义：超卖、少卖或出现 5xx（锁/超时/连接池等）

也可手工核对（把 `{id}` 换成 Setup 日志中的活动 ID）：

```powershell
$tok = (Invoke-RestMethod -Method Post -Uri "http://10.119.13.196/api/v1/auth/login" `
  -ContentType "application/json" -Body '{"userId":"T001","password":"123456"}').data.token
Invoke-RestMethod -Uri "http://10.119.13.196/api/v1/activities/{id}" `
  -Headers @{ Authorization = "Bearer $tok" }
```

### TG5 收藏计数

对应 `FavoriteService.toggle`（先查再改 + `favorite_count` 增减，**无活动行悲观锁**）。

- 期望：相对 Setup 基线，`favoriteCount` 增量 == 100
- 若 `toggleOk=100` 但 delta ≠ 100：报告为**潜在并发计数漂移**（本计划不修改后端，仅暴露问题）

### jmeter.log 关键字

- `TEST_ACTIVITY_ID=` / `FAVORITE_ACTIVITY_ID=`
- `Signup verify:` / `Favorite verify:`
- `dup unexpected success=`

## 重新生成 .jmx

```bash
python generate_jmx.py
```

修改线程组结构、断言脚本或默认 HOST 时，优先改脚本再生成，避免手工改巨大 XML。

## 注意事项

- 登录使用 BCrypt，100 并发下登录本身可能接近或超过 3s；若仅 Login 超时而读接口达标，报告中请分采样器说明。
- TG4/TG5 每次 Setup 都会**新建**活动，避免污染历史数据；勿对生产重要活动 ID 硬编码施压。
- 不要压测 `POST /search/index/rebuild`、聚类提交、LLM 分析生成等运维/重任务接口。
- 并发正确性请勿与全站大流量读压测同时对同一库打满，以免误判超时为逻辑错误。
