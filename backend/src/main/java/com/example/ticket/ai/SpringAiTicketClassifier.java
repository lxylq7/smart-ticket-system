package com.example.ticket.ai;

import org.springframework.ai.chat.client.ChatClient;

public class SpringAiTicketClassifier implements TicketClassifier {

    private static final String SYSTEM_PROMPT = """
            你是工单分类器。只根据工单标题和问题描述分类，不要改变工单状态、创建人、处理人或权限。
            必须返回结构化对象：category 只能是 ORDER、ACCOUNT、TECHNICAL、OTHER 之一；reason 是简洁的中文分类理由。
            ORDER 表示订单问题，ACCOUNT 表示账户问题，TECHNICAL 表示技术问题，OTHER 表示其他问题。
            """;

    private final ChatClient chatClient;

    public SpringAiTicketClassifier(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public TicketClassificationResult classify(String title, String description) {
        TicketClassificationResult result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(user -> user.text("工单标题：{title}\n问题描述：{description}")
                        .param("title", title).param("description", description))
                .call()
                .entity(TicketClassificationResult.class);
        return result.normalized();
    }
}
