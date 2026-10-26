# myai-interview · AI 面试助手

基于 **Spring AI + DeepSeek** 的简历评审与面试对话系统。上传简历文本，由大模型给出结构化评审意见并落库，同时保留历史记录供回看。

> 本项目为个人学习项目，按阶段推进，每个阶段的功能与取舍都记录在提交历史中。

---

## 一、当前能力

| 能力 | 状态 | 说明 |
|---|---|---|
| AI 对话（同步） | 已完成 | `GET /api/chat/simple`，接入 DeepSeek |
| AI 对话（流式） | 已完成 | `GET /api/chat/stream`，SSE 逐字返回 |
| 多轮对话记忆 | 已完成 | 按 `conversationId` 隔离上下文，服务重启后不丢 |
| 简历结构化评审 | 已完成 | 模型返回 JSON 直接映射为对象，不靠字符串截取 |
| 评审结果落库 | 已完成 | MyBatis-Plus + MySQL，含历史查询 |
| 统一返回体 / 全局异常 | 已完成 | 业务异常码 + 统一响应结构 |
| 简易 Web 页面 | 已完成 | 静态页，可直接在浏览器试对话 |
| RAG 检索增强 | 规划中 | 见「四、路线」 |

---

## 二、技术栈

- **JDK 17** / **Spring Boot 3.5.16**
- **Spring AI 1.1.0**（`spring-ai-starter-model-openai`）
- **DeepSeek** —— 兼容 OpenAI 协议，因此直接复用 OpenAI starter，只改 `base-url`，不引额外 SDK
- **MyBatis-Plus 3.5.17**（Spring Boot 3 专用版，版本由 BOM 统一管理）
- **MySQL 8**
- 依赖注入全部使用**构造器注入**，不使用字段注入

---

## 三、接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/chat/simple?message=&conversationId=` | 同步对话，返回完整文本 |
| GET | `/api/chat/stream?message=&conversationId=` | SSE 流式对话，逐块返回 |
| POST | `/api/resume/review` | 提交简历文本，返回结构化评审意见（不入库） |
| POST | `/api/resume/save` | 评审并保存，返回落库后的实体 |
| GET | `/api/resume/history` | 查询评审历史 |
| GET | `/api/resume/{id}` | 查询单条评审详情 |

---

## 四、本地启动

### 1. 环境变量（必须）

项目不含任何密钥，以下两项通过环境变量注入：

```powershell
# PowerShell，设置后需重启终端和 IDEA
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-你的Key", "User")
[Environment]::SetEnvironmentVariable("DB_PASSWORD", "你的数据库密码", "User")
```

### 2. 数据库

建库与建表已写好，一条命令即可：

```bash
mysql -u root -p < sql/schema.sql
```

详见 `sql/schema.sql`。

### 3. 运行

```bash
mvn spring-boot:run
```

启动后访问 `http://localhost:8080/index.html` 可直接在页面上试对话。

---

## 五、几个设计取舍

写在这里，是因为这些点比"用了什么框架"更值得说明。

**1. 为什么用 OpenAI starter 接 DeepSeek**
DeepSeek 的接口协议与 OpenAI 兼容，单独引 SDK 只会多一层维护成本。只改 `base-url` 和 `model`，其余代码与用 OpenAI 完全一致，将来换模型厂商的改动面被压到最小。

**2. 结构化输出用 `.entity()` 而不是自己解析 JSON**
```java
return chatClient.prompt()
        .system("你是一个简历评审员，请根据简历内容给出评审意见。")
        .user(resumeText)
        .call()
        .entity(ResumeReview.class);
```
由 Spring AI 的转换器负责映射，避免手工 `substring` 截取 JSON——那种写法一旦模型多输出一句话就会崩。

**3. 落库时简历原文用入参，不用模型复述的版本**
```java
// 简历内容直接用传入的参数，防止ai复述后产生偏差
resume.setResumeText(resumeText);
```
模型会"润色"输入，若把它复述的内容当原文存下来，数据就失真了。**原文用原文，模型输出只用于评审结论。**

**4. 单条插入不加事务**
`resumeService.save(resume)` 是单条插入，本身原子，加 `@Transactional` 只是多一层代理开销。事务应该加在"多表写入必须同时成功"的场景。

**5. 对话记忆按 conversationId 隔离**
`MessageChatMemoryAdvisor` 绑定 `conversationId`，不同会话互不干扰，同一会话内多轮上下文连续。

---

## 六、路线

- [x] **阶段 0** 骨架跑通，浏览器能看到 AI 返回
- [x] **阶段 1** 对话记忆、结构化输出、异常与返回体统一、评审落库
- [ ] **阶段 2** RAG：简历片段向量化 + 检索增强，让评审能引用简历中的具体段落
- [ ] **阶段 3** 模拟面试：多轮追问、按岗位出题、答案评分

---

## 七、说明

提交历史按功能点拆分，每个提交对应一个可运行的改动，用于记录真实开发过程。
