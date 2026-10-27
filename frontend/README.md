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

当前已提供登录、角色工作区和 USER 普通用户工单页面：我的工单列表、创建工单、工单详情及待分配工单编辑。
这些页面通过 `src/api/tickets.js` 调用真实后端接口，不使用写死的工单数据。工单消息回复、客服工作台和管理员工作台将在对应后端接口完成后继续接入。
