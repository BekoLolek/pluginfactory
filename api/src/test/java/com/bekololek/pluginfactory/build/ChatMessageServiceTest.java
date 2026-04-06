package com.bekololek.pluginfactory.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    void addMessage() {
        UUID sessionId = UUID.randomUUID();
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(UUID.randomUUID());
            msg.setCreatedAt(Instant.now());
            return msg;
        });

        ChatMessage result = chatMessageService.addMessage(sessionId, "user", "Hello", "gpt-4", 100);

        assertThat(result.getSessionId()).isEqualTo(sessionId);
        assertThat(result.getRole()).isEqualTo("user");
        assertThat(result.getContent()).isEqualTo("Hello");
        assertThat(result.getModelUsed()).isEqualTo("gpt-4");
        assertThat(result.getTokensConsumed()).isEqualTo(100);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        ChatMessage saved = captor.getValue();
        assertThat(saved.getSessionId()).isEqualTo(sessionId);
        assertThat(saved.getRole()).isEqualTo("user");
    }

    @Test
    void getMessages_orderedByCreatedAt() {
        UUID sessionId = UUID.randomUUID();

        ChatMessage msg1 = new ChatMessage();
        msg1.setId(UUID.randomUUID());
        msg1.setSessionId(sessionId);
        msg1.setRole("user");
        msg1.setContent("First");
        msg1.setCreatedAt(Instant.now().minusSeconds(10));

        ChatMessage msg2 = new ChatMessage();
        msg2.setId(UUID.randomUUID());
        msg2.setSessionId(sessionId);
        msg2.setRole("assistant");
        msg2.setContent("Second");
        msg2.setCreatedAt(Instant.now());

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(msg1, msg2));

        List<ChatMessage> result = chatMessageService.getMessages(sessionId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("First");
        assertThat(result.get(1).getContent()).isEqualTo("Second");
    }
}
