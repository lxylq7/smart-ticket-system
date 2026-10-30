# 智能工单前端

Vue 3 + Vite + Element Plus + Vue Router + Axios 的基础前端工程。

## 启动

```powershell
cd E:\smart-ticket-system\frontend
npm install
Copy-Item .env.example .env.local
npm run dev
```

默认访问 `http://localhost:5173`。开发服务器会把 `/api` 转发到
`http://localhost:8080`，因此需要先启动后端。后端地址可通过
`.env.local` 的 `VITE_DEV_API_TARGET` 修改；跨域部署时设置
`VITE_API_BASE_URL` 为完整 API 地址。

生产构建：

```powershell
npm run build
npm run preview
```

登录成功后 JWT 保存在浏览器 localStorage，Axios 自动发送 Bearer Token。
401 会清除登录态并跳回登录页；统一响应中的非 `SUCCESS` code 会转换为 `ApiError`。
角色路由只负责前端页面体验，后端认证和权限校验仍是最终边界。

当前已提供登录、角色工作区、USER 普通用户工单页面和 AGENT 客服工作台。
USER 和 AGENT 的工单详情页已接入真实消息接口：USER 发送公开回复，AGENT 可发送公开回复或内部备注；消息列表按后端权限返回，不使用写死数据。

## 测试与部署

运行前端单元测试：

```powershell
npm test
```

构建生产产物：

```powershell
npm run build
```

也可以使用 Docker 构建 Nginx 静态站点：

```powershell
docker build -t smart-ticket-frontend .
docker run --rm -p 5173:80 --add-host=backend:host-gateway smart-ticket-frontend
```

容器内 Nginx 会把 `/api` 转发到名为 `backend` 的 8080 服务，并使用 history fallback 支持 Vue Router 刷新。
