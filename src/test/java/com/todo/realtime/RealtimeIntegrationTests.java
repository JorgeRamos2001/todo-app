package com.todo.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.todo.auth.repository.RefreshTokenRepository;
import com.todo.auth.security.JwtService;
import com.todo.boards.domain.Board;
import com.todo.boards.domain.BoardMember;
import com.todo.boards.domain.BoardRole;
import com.todo.boards.domain.BoardType;
import com.todo.boards.repository.BoardActivityRepository;
import com.todo.boards.repository.BoardMemberRepository;
import com.todo.boards.repository.BoardRepository;
import com.todo.columns.dto.CreateColumnRequest;
import com.todo.columns.repository.BoardColumnRepository;
import com.todo.columns.service.ColumnService;
import com.todo.invitations.repository.InvitationRepository;
import com.todo.realtime.dto.RealtimeEvent;
import com.todo.tasks.repository.CommentRepository;
import com.todo.tasks.repository.SubtaskRepository;
import com.todo.tasks.repository.TaskRepository;
import com.todo.users.domain.User;
import com.todo.users.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class RealtimeIntegrationTests {

	private static final long SUBSCRIPTION_SETTLE_MILLIS = 500;

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@LocalServerPort
	int port;

	@Autowired
	UserRepository userRepository;

	@Autowired
	BoardRepository boardRepository;

	@Autowired
	BoardMemberRepository boardMemberRepository;

	@Autowired
	BoardColumnRepository boardColumnRepository;

	@Autowired
	TaskRepository taskRepository;

	@Autowired
	SubtaskRepository subtaskRepository;

	@Autowired
	CommentRepository commentRepository;

	@Autowired
	InvitationRepository invitationRepository;

	@Autowired
	BoardActivityRepository boardActivityRepository;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	JwtService jwtService;

	@Autowired
	ColumnService columnService;

	private User alice;

	private User carol;

	private Board board;

	@BeforeEach
	void setUp() {
		boardActivityRepository.deleteAll();
		commentRepository.deleteAll();
		subtaskRepository.deleteAll();
		taskRepository.deleteAll();
		invitationRepository.deleteAll();
		boardColumnRepository.deleteAll();
		boardMemberRepository.deleteAll();
		boardRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
		alice = userRepository.save(User.local("Alice", "alice@example.com", "hash"));
		carol = userRepository.save(User.local("Carol", "carol@example.com", "hash"));
		board = boardRepository.save(new Board("Board", null, BoardType.COLLABORATIVE, alice));
		boardMemberRepository.save(new BoardMember(board, alice, BoardRole.OWNER));
	}

	@Test
	void ownerReceivesBoardEventsOverStomp() throws Exception {
		WebSocketStompClient stompClient = stompClient();
		BlockingQueue<RealtimeEvent> events = new LinkedBlockingQueue<>();

		StompSession session = connect(stompClient, authorization(alice));
		session.subscribe("/topic/boards/" + board.getId(), eventFrameHandler(events));
		Thread.sleep(SUBSCRIPTION_SETTLE_MILLIS);

		columnService.create(board.getId(), alice.getId(), new CreateColumnRequest("Todo"));

		RealtimeEvent event = events.poll(5, TimeUnit.SECONDS);
		assertThat(event).isNotNull();
		assertThat(event.type().name()).isEqualTo("COLUMN_CREATED");
		assertThat(event.boardId()).isEqualTo(board.getId());
		assertThat(event.actorId()).isEqualTo(alice.getId());
		assertThat(event.payload().get("name")).isEqualTo("Todo");

		if (session.isConnected()) {
			session.disconnect();
		}
		stompClient.stop();
	}

	@Test
	void connectWithoutJwtIsRejected() {
		WebSocketStompClient stompClient = stompClient();
		try {
			assertThatThrownBy(() -> stompClient
				.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(), new StompHeaders(),
						new StompSessionHandlerAdapter() {
						})
				.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
		}
		finally {
			stompClient.stop();
		}
	}

	@Test
	void nonMembersDoNotReceiveBoardEvents() throws Exception {
		WebSocketStompClient stompClient = stompClient();
		BlockingQueue<RealtimeEvent> events = new LinkedBlockingQueue<>();

		StompSession session = connect(stompClient, authorization(carol));
		session.subscribe("/topic/boards/" + board.getId(), eventFrameHandler(events));

		columnService.create(board.getId(), alice.getId(), new CreateColumnRequest("Todo"));

		assertThat(events.poll(2, TimeUnit.SECONDS)).isNull();

		if (session.isConnected()) {
			session.disconnect();
		}
		stompClient.stop();
	}

	private WebSocketStompClient stompClient() {
		SockJsClient sockJsClient = new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())));
		WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
		stompClient.setMessageConverter(
				new MappingJackson2MessageConverter(Jackson2ObjectMapperBuilder.json().build()));
		return stompClient;
	}

	private StompSession connect(WebSocketStompClient stompClient, String authorization) throws Exception {
		StompHeaders connectHeaders = new StompHeaders();
		connectHeaders.add(HttpHeaders.AUTHORIZATION, authorization);
		return stompClient
			.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
					new StompSessionHandlerAdapter() {
					})
			.get(5, TimeUnit.SECONDS);
	}

	private StompFrameHandler eventFrameHandler(BlockingQueue<RealtimeEvent> events) {
		return new StompFrameHandler() {
			@Override
			public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
				return RealtimeEvent.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				events.add((RealtimeEvent) payload);
			}
		};
	}

	private String authorization(User user) {
		return "Bearer " + jwtService.generateAccessToken(user);
	}

}
