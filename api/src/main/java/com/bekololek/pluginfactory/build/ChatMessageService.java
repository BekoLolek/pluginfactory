package com.bekololek.pluginfactory.build;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatMessage addMessage(UUID sessionId, String role, String content, String modelUsed, int tokensConsumed) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setModelUsed(modelUsed);
        message.setTokensConsumed(tokensConsumed);
        return chatMessageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessages(UUID sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}
