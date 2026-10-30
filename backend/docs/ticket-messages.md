# 工单消息接口

消息表沿用现有 `ticket_message`，消息可见性为 `PUBLIC`（公开回复）或 `INTERNAL`（内部备注）。
新增消息与 `ticket_operation_history` 写入在同一个事务中完成。

## 权限规则

| 操作人 | 可查看 | 可新增 |
| --- | --- | --- |
| USER | 自己工单的 PUBLIC 消息 | 自己工单的 PUBLIC 消息 |
| 当前处理 AGENT | 分配给自己的 PUBLIC、INTERNAL 消息 | PUBLIC、INTERNAL 消息 |
| ADMIN | 全部 PUBLIC、INTERNAL 消息 | PUBLIC、INTERNAL 消息 |

客服只能处理当前分配给自己的工单；越权访问统一返回 `404 TICKET_NOT_FOUND`，避免泄露工单是否存在。
普通用户提交 INTERNAL 消息返回 `403 FORBIDDEN`。已关闭工单不能新增消息，返回 `409 TICKET_CLOSED`。

## 查询消息

```http
GET /api/tickets/{ticketId}/messages
Authorization: Bearer <accessToken>
```

响应中的 `data` 是按创建时间正序排列的消息数组：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "ticketId": 103,
      "senderId": 3,
      "visibility": "PUBLIC",
      "content": "已收到问题，开始排查。",
      "createdAt": "2026-10-27T09:30:00",
      "updatedAt": "2026-10-27T09:30:00"
    }
  ],
  "timestamp": "2026-10-27T09:30:00Z"
}
```

## 新增消息

```http
POST /api/tickets/{ticketId}/messages
Authorization: Bearer <accessToken>
Content-Type: application/json
```

公开回复：

```json
{
  "visibility": "PUBLIC",
  "content": "请补充支付时间和订单编号。"
}
```

内部备注：

```json
{
  "visibility": "INTERNAL",
  "content": "已检查支付回调日志，等待上游确认。"
}
```

`content` 必须是 1～10000 个字符。创建成功返回 HTTP 201 和完整消息对象，同时追加一条
`PUBLIC_REPLY` 或 `INTERNAL_NOTE` 操作历史。消息接口不会自动修改工单状态；状态流转仍使用
`PUT /api/tickets/{id}/status`。
