package com.example.ticket.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ticket.security.AuthenticatedUser;
import com.example.ticket.security.CurrentUser;
import com.example.ticket.security.UserRole;
import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.enums.ClassificationStatus;
import com.example.ticket.ticket.enums.TicketCategory;
import com.example.ticket.ticket.mapper.TicketMapper;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class TicketClassificationServiceTest {

    private final TicketMapper mapper = org.mockito.Mockito.mock(TicketMapper.class);
    private final TicketClassifier classifier = org.mockito.Mockito.mock(TicketClassifier.class);
    private final TicketClassificationPersistence persistence = org.mockito.Mockito.mock(TicketClassificationPersistence.class);
    private final CurrentUser currentUser = org.mockito.Mockito.mock(CurrentUser.class);
    private final ApplicationEventPublisher events = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private TicketClassificationService service;

    @BeforeEach
    void setUp() {
        when(classifier.available()).thenReturn(true);
        service = new TicketClassificationService(mapper, classifier, persistence,
                new AiProperties(true, Duration.ofSeconds(1)), executor, currentUser, events);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void successfulStructuredResultIsValidatedAndPersisted() throws Exception {
        Ticket ticket = ticket(42, 3, null, ClassificationStatus.PENDING);
        when(mapper.selectById(42L)).thenReturn(ticket);
        when(mapper.claimClassification(42L, 3)).thenReturn(1);
        when(classifier.classify("支付失败", "订单已扣款")).thenReturn(
                new TicketClassificationResult(TicketCategory.ORDER, "标题和描述都指向订单支付问题"));

        service.classifyNow(42);

        verify(persistence).saveSuccess(42, 4, TicketCategory.ORDER, "标题和描述都指向订单支付问题");
        verify(persistence, never()).saveFailure(anyInt(), anyInt(), anyString());
    }

    @Test
    void modelExceptionBecomesFailedWithoutThrowing() throws Exception {
        Ticket ticket = ticket(42, 0, null, ClassificationStatus.PENDING);
        when(mapper.selectById(42L)).thenReturn(ticket);
        when(mapper.claimClassification(42L, 0)).thenReturn(1);
        when(classifier.classify(anyString(), anyString())).thenThrow(new IllegalStateException("provider unavailable"));

        service.classifyNow(42);

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(persistence).saveFailure(eq(42L), eq(1), error.capture());
        assertThat(error.getValue()).contains("IllegalStateException", "provider unavailable");
    }

    @Test
    void invalidStructuredResultBecomesFailed() throws Exception {
        Ticket ticket = ticket(42, 0, null, ClassificationStatus.PENDING);
        when(mapper.selectById(42L)).thenReturn(ticket);
        when(mapper.claimClassification(42L, 0)).thenReturn(1);
        when(classifier.classify(anyString(), anyString())).thenReturn(new TicketClassificationResult(null, "理由"));

        service.classifyNow(42);

        verify(persistence).saveFailure(eq(42L), eq(1), anyString());
    }

    @Test
    void categoryAlreadyPresentIsNeverSentToModelOrOverwritten() throws Exception {
        Ticket ticket = ticket(42, 2, TicketCategory.ACCOUNT, ClassificationStatus.SUCCESS);
        when(mapper.selectById(42L)).thenReturn(ticket);

        service.classifyNow(42);

        verify(classifier, never()).classify(anyString(), anyString());
        verify(mapper, never()).claimClassification(42L, 2);
    }

    @Test
    void optimisticClaimPreventsDuplicateModelCalls() throws Exception {
        Ticket ticket = ticket(42, 2, null, ClassificationStatus.PENDING);
        when(mapper.selectById(42L)).thenReturn(ticket);
        when(mapper.claimClassification(42L, 2)).thenReturn(0);

        service.classifyNow(42);

        verify(classifier, never()).classify(anyString(), anyString());
    }

    @Test
    void retryResetsOnlyFailedAccessibleTicketAndPublishesAfterTransactionEvent() {
        Ticket ticket = ticket(42, 5, null, ClassificationStatus.FAILED);
        when(mapper.selectById(42L)).thenReturn(ticket);
        when(mapper.resetClassification(42L, 5)).thenReturn(1);
        when(currentUser.require()).thenReturn(new AuthenticatedUser(9, "owner", Set.of(UserRole.USER)));
        ticket.setCreatorId(9L);

        var view = service.retry(42);

        assertThat(view.id()).isEqualTo(42L);
        verify(events).publishEvent(new TicketClassificationRequested(42));
        verify(mapper).resetClassification(42L, 5);
    }

    @Test
    void retryCannotOverwriteManualCategory() {
        Ticket ticket = ticket(42, 5, TicketCategory.TECHNICAL, ClassificationStatus.FAILED);
        ticket.setCreatorId(9L);
        when(mapper.selectById(42L)).thenReturn(ticket);
        when(currentUser.require()).thenReturn(new AuthenticatedUser(9, "owner", Set.of(UserRole.USER)));

        assertThatThrownBy(() -> service.retry(42))
                .isInstanceOf(com.example.ticket.common.exception.ApiException.class)
                .hasMessageContaining("已有分类");
        verify(mapper, never()).resetClassification(42L, 5);
    }

    private Ticket ticket(long id, int version, TicketCategory category, ClassificationStatus status) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setVersion(version);
        ticket.setTitle("支付失败");
        ticket.setDescription("订单已扣款");
        ticket.setCategory(category);
        ticket.setAiStatus(status);
        return ticket;
    }
}
