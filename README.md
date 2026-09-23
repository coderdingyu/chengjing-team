# 澄镜 · 五人协作版骨架

这是从[现有澄镜系统](https://github.com/coderdingyu/chengjing)拆出的**可运行起点**，使用 React 18 + TypeScript + Vite、Spring Boot 3.4 + Java 17。当前只有首页、五个模块的待实现页面、统一 API 返回格式、后端健康接口和构建检查。它并不声称已经有登录、面试或评分能力。

团队按 [30 项功能分工](docs/30项功能分工.md) 各实现 6 项，每项对应一次由实际开发者完成的功能提交。负责人先上传的骨架提交不计入这 30 次。旧系统是实现参考，不直接整包复制，以便每项都有明确的代码和验收结果。

产品定位、用户流程、跨模块规则与整体验收见 [需求分析说明书](docs/需求分析说明书.md)。

## 目录

| 目录 | 负责人 | 内容 |
| --- | --- | --- |
| `backend/src/main/java/com/chengjing/identity`、`frontend/src/features/identity` | 成员 A | 账号与隐私 |
| `.../preparation`、`frontend/src/features/preparation` | 成员 B | 面试准备 |
| `.../interview`、`frontend/src/features/interview` | 成员 C | 面试进行 |
| `.../assessment`、`frontend/src/features/assessment` | 成员 D | 评分与成长 |
| `.../platform`、`frontend/src/features/platform` | 负责人 E | 模型与平台 |

`backend/.../shared`、`frontend/src/api.ts` 和入口路由属于公共契约。改公共文件前先在 PR 中说明接口影响；功能实现、测试优先放在各自目录。五个 `features` 目录随功能提交创建，骨架只预留模块入口。

## 本机启动

需要 Java 17、Node.js 22 与 npm。**骨架端口是 8081 和 5173**，可与原系统的 8080 同时运行。

```powershell
cd backend
.\mvnw.cmd -s .mvn/settings.xml spring-boot:run
```

另开一个终端：

```powershell
cd frontend
npm ci
npm run dev
```

浏览器打开 http://127.0.0.1:5173 。后端健康接口是 http://127.0.0.1:8081/api/v1/system/health 。提交前在 `frontend` 目录运行 `npm run build`，在 `backend` 目录运行 `.\mvnw.cmd -s .mvn/settings.xml test`。GitHub Actions 也会运行这两项检查。

## 开发规则

- 每个功能一个分支、一个可理解的功能提交和一个 PR；由该功能的实际开发者使用自己的 GitHub 身份提交。[具体步骤](docs/协作上传指南.md)。
- API 使用 `/api/v1` 前缀、`{success,data,message}` 格式；存储与模型都在服务端。详见 [模块契约](docs/模块契约.md)。
- 真实姓名、简历、音视频、API Key、数据库、`.env` 不进入 Git。麦克风与摄像头必须单独征得同意；原始音视频不作为评分依据。
- 评分只根据回答证据；证据不足显示“未覆盖”，不填 0 分。不能把视觉外观用于评分。
