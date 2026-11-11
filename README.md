# ai-interview · AI 面试官（带 AI 亮点的 Java 后端项目）

> 2026-11-08用户补充：用户确认：这是在AI指导下实际完成的JMeter入门学习/采样练习，应记为已实践；原始结果保留。它不等于完整监控体系或容量验证。

> **核心是后端工程，AI 是加分项。** 一个「AI 面试官」平台后端：求职者上传简历 → AI 评审打分 → RAG 问答 → 限流控成本。
> 重心在**限流、成本控制、事务边界、RAG、结构化输出**这些工程治理上，而不是「调模型」。

---

## 一、当前能力

| 能力 | 状态 | 说明 |
|---|---|---|
| AI 对话（同步 / 流式） | ✅ | 同步 + SSE 流式，多轮记忆按 `conversationId` 隔离 |
| 简历结构化评审 | ✅ | 模型输出 JSON 直接映射为对象，不靠字符串截取 |
| **多维度评分** | ✅ | 技术 / 项目 / 表达三维度打分 + 各自理由，总分由代码聚合 |
| 评审结果落库 | ✅ | MyBatis-Plus + MySQL，含历史查询 |
| **RAG 检索增强问答** | ✅ | Redis Stack 向量库，切块 + embedding + 相似度检索 topK |
| **限流防刷** | ✅ | Redis + Lua 原子计数，`fail-open`（Redis 挂时不拒绝服务） |
| 统一返回体 / 全局异常 | ✅ | 业务码 `0` 成功、非 `0` 失败，HTTP 状态码与业务码分离 |
| Web 页面 | ✅ | Vue 3（CDN 单文件），可直接浏览器试 |
| **小样本接口观测** | 有历史报告 | 工作区记录为history的50次请求；当前pom未直接列Actuator/Prometheus依赖，监控环境与复现条件待核实；不宣称容量上限 |

---

## 二、技术栈

- **JDK 17** / **Spring Boot 3.5.16**
- **Spring AI 1.1.0**（`spring-ai-starter-model-openai`，BOM 统一管版本）
- **百炼 / DeepSeek** —— 兼容 OpenAI 协议，复用 OpenAI starter，只改 `base-url`，Profile 切换
- **MyBatis-Plus 3.5.17**（Boot 3 专用版 + 分页插件）
- **MySQL 8**
- **Redis Stack**（向量库 + 限流计数）
- 依赖注入全部使用**构造器注入**

---

## 三、接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/chat/simple?message=&conversationId=` | 同步对话 |
| GET | `/api/chat/stream?message=&conversationId=` | SSE 流式对话 |
| POST | `/api/resume/review` | 简历评审（不入库） |
| POST | `/api/resume/save` | 评审并落库 |
| GET | `/api/resume/history` | 评审历史分页 |
| GET | `/api/resume/{id}` | 单条评审详情 |
| POST | `/ask/question` | RAG 问答（向量检索 + 回答） |

---

## 四、本地启动

### 1. 环境变量（必须）

项目不含任何密钥，通过环境变量注入：

```powershell
# PowerShell，设置后需重启终端和 IDEA
[Environment]::SetEnvironmentVariable("DASHSCOPE_API_KEY", "sk-你的百炼Key", "User")
[Environment]::SetEnvironmentVariable("DB_PASSWORD", "你的数据库密码", "User")
```

> 默认 profile 是 `bailian`（阿里云百炼，chat + embedding 都有，RAG 用它）。
> 只做对话/评审可切 `deepseek` profile（`spring.profiles.active=deepseek`，需 `DEEPSEEK_API_KEY`）。

### 2. 依赖服务

需要本机跑 **MySQL 8** 和 **Redis Stack**（Docker 即可）。建库建表一条命令：

```bash
mysql -u root -p < sql/schema.sql
```

### 3. 运行

```bash
mvn spring-boot:run
```

启动后访问 `http://localhost:8080/index.html`。

---

## 五、设计取舍（比「用了什么」更值得说明）

**1. 为什么用 OpenAI starter 接 DeepSeek/百炼**
接口协议与 OpenAI 兼容，单独引 SDK 只多一层维护成本。只改 `base-url` 和 `model`，换模型厂商的改动面被压到最小。

**2. 结构化输出用 `.entity()` 而不是自己解析 JSON**
由 Spring AI 的转换器负责映射，避免手工 `substring` 截取——那种写法一旦模型多输出一句话就会崩。

**3. 落库时简历原文用入参，不用模型复述的版本**
模型会「润色」输入，把复述内容当原文存下来数据就失真。**原文用原文，模型输出只用于评审结论。**

**4. AI 慢调用不占数据库连接（刻意不加 `@Transactional`）**
`reviewAndSave()` 先调 AI（几秒~几十秒、不碰库），再单条 INSERT 落库。扩大事务范围可能让AI等待期间占用数据库资源；是否实际持有连接取决于事务与连接获取方式，需要运行观测验证。**正确边界：AI 调用留事务外，事务只包住真正要落库的那几条写。**

**5. 限流 fail-open（Redis 挂了不拒绝服务）**
限流用 Redis + Lua 原子计数。但 Redis 不可用时，拦截器选择**放行**（fail-open）而不是拒绝——限流是「防刷省钱」，不该成为「服务可用性」的单点。宁可漏防几个请求，也不能因为 Redis 抖动把正常用户全挡在门外。

**6. 总分由代码聚合，不让模型输出**
大模型算术不可靠，派生总分用确定的代码计算，保证聚合规则一致。所以 `overallScore()` 由代码对三个维度分求平均，模型只负责「分维度打分 + 理由」。

**7. 对话记忆按 conversationId 隔离**
`MessageChatMemoryAdvisor` 绑定 `conversationId`，不同会话互不干扰，同一会话内多轮上下文连续。这是对话记忆，不等于用户身份认证或数据归属隔离。

---

## 六、路线

- [x] **阶段 0** 骨架跑通（对话 / 流式 / 记忆 / 结构化输出）
- [x] **阶段 1** 业务闭环（MyBatis-Plus 落库 + 分页 + 统一返回体）
- [x] **阶段 2** RAG（Redis Stack 向量库 + 检索增强问答）
- [x] **阶段 3** 后端增强（多维度评分 + 限流防刷 + 成功码统一）
- [ ] **近期**：模拟面试最小闭环、账户/JWT与数据隔离；排错与独立修改练习并行。
- [ ] **按需候选**：额度扣减、线程池异步报告、缓存/MQ，依据实际业务与反馈选择。

当前尚未实现完整模拟面试、用户身份认证和数据权限隔离；示例功能不应描述为已完成的多用户平台。

---

## 七、说明

提交历史按功能点拆分，每个提交对应一个可运行的改动，用于记录真实开发过程。
