package com.todo.realtime.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.todo.boards.event.BoardActivityEvent;
import com.todo.realtime.dto.RealtimeEvent;

@Component
public class RealtimePublisher {

	public static final String BOARD_TOPIC_PREFIX = "/topic/boards/";

	private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

	private final SimpMessagingTemplate messagingTemplate;

	public RealtimePublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onBoardActivity(BoardActivityEvent event) {
		RealtimeEvent realtimeEvent = new RealtimeEvent(event.action(), event.boardId(), event.entityType(),
				event.entityId(), event.actorId(), event.details(), Instant.now());
		try {
			messagingTemplate.convertAndSend(BOARD_TOPIC_PREFIX + event.boardId(), realtimeEvent);
		}
		catch (RuntimeException exception) {
			log.warn("Could not broadcast realtime event {} for board {}", event.action(), event.boardId(), exception);
		}
	}

}
