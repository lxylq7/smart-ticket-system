package com.example.ticket.ai;

import com.example.ticket.common.exception.ApiException;
import com.example.ticket.security.AuthenticatedUser;
import com.example.ticket.security.CurrentUser;
import com.example.ticket.security.UserRole;
import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.enums.ClassificationStatus;
import com.example.ticket.ticket.vo.TicketView;
import com.example.ticket.ticket.mapper.TicketMapper;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketClassificationService {

    private final TicketMapper ticketMapper;
    private final TicketClassifier classifier;
    private final TicketClassificationPersistence persistence;
    private final AiProperties properties;
    private final Executor executor;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher eventPublisher;

    public TicketClassificationService(TicketMapper ticketMapper, TicketClassifier classifier,
            TicketClassificationPersistence persistence, AiProperties properties,
            @org.springframework.beans.factory.annotation.Qualifier("ticketAiExecutor") Executor executor,
            CurrentUser currentUser, ApplicationEventPublisher eventPublisher) {
        this.ticketMapper = ticketMapper;
        this.classifier = classifier;
        this.persistence = persistence;
        this.properties = properties;
        this.executor = executor;
        this.currentUser = currentUser;
        this.eventPublisher = eventPublisher;
    }

    public void schedule(long ticketId) {
        if (!properties.enabled() || !classifier.available()) {
            return;
        }
        executor.execute(() -> classifyNow(ticketId));
    }

    /** Synchronous entry point used by tests and by the asynchronous worker. */
    public void classifyNow(long ticketId) {
        if (!properties.enabled() || !classifier.available()) {
            return;
        }
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null || ticket.getCategory() != null
                || ticket.getAiStatus() != ClassificationStatus.PENDING) {
            return;
        }
        int expectedVersion = ticket.getVersion();
        if (ticketMapper.claimClassification(ticketId, expectedVersion) != 1) {
            return;
        }
        int claimedVersion = expectedVersion + 1;
        try {
            TicketClassificationResult result = callWithTimeout(ticket.getTitle(), ticket.getDescription()).normalized();
            persistence.saveSuccess(ticketId, claimedVersion, result.category(), result.reason());
        } catch (Exception exception) {
            String message = safeError(exception);
            persistence.saveFailure(ticketId, claimedVersion, message);
        }
    }

    @Transactional
    public TicketView retry(long ticketId) {
        AuthenticatedUser user = currentUser.require();
        Ticket ticket = ticketMapper.selectById(ticketId);
        require(ticket != null, HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "工单不存在或无权访问");
        require(canAccess(ticket, user), HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "工单不存在或无权访问");
        require(ticket.getCategory() == null, HttpStatus.CONFLICT, "CLASSIFICATION_ALREADY_SET",
                "工单已有分类，不能由 AI 覆盖");
        require(ticket.getAiStatus() == ClassificationStatus.FAILED, HttpStatus.CONFLICT,
                "CLASSIFICATION_NOT_FAILED", "只有分类失败的工单可以重试");
        if (ticketMapper.resetClassification(ticketId, ticket.getVersion()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_CONFLICT", "工单已发生变化，请刷新后重试");
        }
        eventPublisher.publishEvent(new TicketClassificationRequested(ticketId));
        return TicketView.from(ticketMapper.selectById(ticketId));
    }

    private TicketClassificationResult callWithTimeout(String title, String description) throws Exception {
        Duration timeout = properties.timeout() == null ? Duration.ofSeconds(15) : properties.timeout();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return classifier.classify(title, description);
            } catch (Exception exception) {
                throw new ClassificationCallException(exception);
            }
        }).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
    }

    private String safeError(Exception exception) {
        Throwable cause = exception;
        if (exception instanceof java.util.concurrent.CompletionException completion && completion.getCause() != null) {
            cause = completion.getCause();
        }
        if (cause instanceof ClassificationCallException wrapped && wrapped.getCause() != null) {
            cause = wrapped.getCause();
        }
        String message = cause.getClass().getSimpleName();
        if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
            message += ": " + cause.getMessage().strip();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private boolean canAccess(Ticket ticket, AuthenticatedUser user) {
        return user.hasRole(UserRole.ADMIN)
                || (user.hasRole(UserRole.USER) && Objects.equals(ticket.getCreatorId(), user.id()))
                || (user.hasRole(UserRole.AGENT) && Objects.equals(ticket.getAssigneeId(), user.id()));
    }

    private void require(boolean condition, HttpStatus status, String code, String message) {
        if (!condition) throw new ApiException(status, code, message);
    }

    private static class ClassificationCallException extends RuntimeException {
        ClassificationCallException(Throwable cause) { super(cause); }
    }
}
