package com.todo.boards.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.todo.boards.event.BoardActivityEvent;

@Component
public class BoardActivityListener {

	private final BoardActivityService boardActivityService;

	public BoardActivityListener(BoardActivityService boardActivityService) {
		this.boardActivityService = boardActivityService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onBoardActivity(BoardActivityEvent event) {
		boardActivityService.record(event);
	}

}
