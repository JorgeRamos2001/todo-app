package com.todo.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.todo.auth.dto.AuthResponse;
import com.todo.auth.dto.RegisterRequest;
import com.todo.auth.repository.RefreshTokenRepository;
import com.todo.boards.domain.BoardActivityAction;
import com.todo.boards.domain.BoardRole;
import com.todo.boards.domain.BoardType;
import com.todo.boards.dto.BoardActivityResponse;
import com.todo.boards.dto.BoardDetailResponse;
import com.todo.boards.dto.CreateBoardRequest;
import com.todo.boards.repository.BoardActivityRepository;
import com.todo.boards.repository.BoardMemberRepository;
import com.todo.boards.repository.BoardRepository;
import com.todo.columns.dto.ColumnResponse;
import com.todo.columns.dto.CreateColumnRequest;
import com.todo.columns.repository.BoardColumnRepository;
import com.todo.invitations.domain.InvitationStatus;
import com.todo.invitations.dto.CreateInvitationRequest;
import com.todo.invitations.dto.InvitationResponse;
import com.todo.invitations.repository.InvitationRepository;
import com.todo.mail.MailMessage;
import com.todo.mail.MailSender;
import com.todo.realtime.dto.RealtimeEvent;
import com.todo.shared.dto.PageResponse;
import com.todo.tasks.dto.AssignTaskRequest;
import com.todo.tasks.dto.CommentResponse;
import com.todo.tasks.dto.CreateCommentRequest;
import com.todo.tasks.dto.CreateTaskRequest;
import com.todo.tasks.dto.TaskResponse;
import com.todo.tasks.dto.UpdateTaskRequest;
import com.todo.tasks.repository.CommentRepository;
import com.todo.tasks.repository.SubtaskRepository;
import com.todo.tasks.repository.TaskRepository;
import com.todo.users.dto.UserResponse;
import com.todo.users.repository.UserRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CriticalFlowE2ETests {

	private static final long SUBSCRIPTION_SETTLE_MILLIS = 500;

	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@LocalServerPort
	int port;

	@Autowired
	ObjectMapper objectMapper;

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

	@MockitoBean
	MailSender mailSender;

	@BeforeEach
	void cleanDatabase() {
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
	}

	@Test
	void completeCollaborationFlow() throws Exception {
		AuthResponse alice = register("Alice", "alice@example.com");
		AuthResponse bob = register("Bob", "bob@example.com");

		BoardDetailResponse board = parse(send(HttpMethod.POST, "/api/v1/boards", alice, 
				new CreateBoardRequest("Roadmap", null, BoardType.COLLABORATIVE)), BoardDetailResponse.class);
		ColumnResponse todo = parse(send(HttpMethod.POST, "/api/v1/boards/" + board.id() + "/columns", alice,
				new CreateColumnRequest("Todo")), ColumnResponse.class);
		ColumnResponse doing = parse(send(HttpMethod.POST, "/api/v1/boards/" + board.id() + "/columns", alice,
				new CreateColumnRequest("Doing")), ColumnResponse.class);
		TaskResponse task = parse(send(HttpMethod.POST, "/api/v1/columns/" + todo.id() + "/tasks", alice,
				new CreateTaskRequest("Ship v1", null)), TaskResponse.class);

		InvitationResponse invitation = parse(send(HttpMethod.POST, "/api/v1/boards/" + board.id() + "/invitations",
				alice, new CreateInvitationRequest("bob@example.com", BoardRole.MEMBER)), InvitationResponse.class);
		verify(mailSender).send(any(MailMessage.class));

		InvitationResponse[] received = parse(send(HttpMethod.GET, "/api/v1/invitations", bob, null),
				InvitationResponse[].class);
		assertThat(received).hasSize(1);

		InvitationResponse accepted = parse(
				send(HttpMethod.POST, "/api/v1/invitations/" + invitation.token() + "/accept", bob, null),
				InvitationResponse.class);
		assertThat(accepted.status()).isEqualTo(InvitationStatus.ACCEPTED);

		HttpResponse<String> forbiddenDelete = send(HttpMethod.DELETE, "/api/v1/boards/" + board.id(), bob, null);
		assertThat(forbiddenDelete.statusCode()).isEqualTo(403);

		CommentResponse comment = parse(send(HttpMethod.POST, "/api/v1/tasks/" + task.id() + "/comments", bob,
				new CreateCommentRequest("On it")), CommentResponse.class);
		assertThat(comment.authorName()).isEqualTo("Bob");

		UserResponse[] found = parse(send(HttpMethod.GET, "/api/v1/users?email=bob@example.com", alice, null),
				UserResponse[].class);
		assertThat(found).hasSize(1);
		Long bobId = found[0].id();

		TaskResponse assigned = parse(send(HttpMethod.POST, "/api/v1/tasks/" + task.id() + "/assignee", alice,
				new AssignTaskRequest(bobId)), TaskResponse.class);
		assertThat(assigned.assigneeId()).isEqualTo(bobId);

		TaskResponse edited = parse(send(HttpMethod.PATCH, "/api/v1/tasks/" + task.id(), bob,
				new UpdateTaskRequest("Ship v1 (edited)", null, null, null)), TaskResponse.class);
		assertThat(edited.title()).isEqualTo("Ship v1 (edited)");

		WebSocketStompClient stompClient = stompClient();
		BlockingQueue<RealtimeEvent> events = new LinkedBlockingQueue<>();
		StompSession session = connect(stompClient, bob.accessToken());
		session.subscribe("/topic/boards/" + board.id(), eventFrameHandler(events));
		Thread.sleep(SUBSCRIPTION_SETTLE_MILLIS);

		send(HttpMethod.PATCH, "/api/v1/tasks/" + task.id(), alice,
				new UpdateTaskRequest(null, null, doing.id(), 0));

		RealtimeEvent event = events.poll(5, TimeUnit.SECONDS);
		assertThat(event).isNotNull();
		assertThat(event.type().name()).isEqualTo("TASK_MOVED");
		assertThat(event.boardId()).isEqualTo(board.id());
		assertThat(event.payload().get("title")).isEqualTo("Ship v1 (edited)");

		if (session.isConnected()) {
			session.disconnect();
		}
		stompClient.stop();

		PageResponse<BoardActivityResponse> activities = parse(
				send(HttpMethod.GET, "/api/v1/boards/" + board.id() + "/activities", alice, null),
				new TypeReference<>() {
				});
		assertThat(activities.content()).extracting(BoardActivityResponse::action)
			.contains(BoardActivityAction.TASK_MOVED, BoardActivityAction.MEMBER_JOINED,
					BoardActivityAction.INVITATION_ACCEPTED, BoardActivityAction.COMMENT_CREATED);

		HttpResponse<String> forbiddenActivities = send(HttpMethod.GET,
				"/api/v1/boards/" + board.id() + "/activities", bob, null);
		assertThat(forbiddenActivities.statusCode()).isEqualTo(403);
	}

	private AuthResponse register(String name, String email) throws Exception {
		return parse(send(HttpMethod.POST, "/api/v1/auth/register", null,
				new RegisterRequest(name, email, "password123")), AuthResponse.class);
	}

	private HttpResponse<String> send(HttpMethod method, String path, AuthResponse auth, Object body)
			throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		if (body == null) {
			builder.method(method.name(), HttpRequest.BodyPublishers.noBody());
		}
		else {
			builder.header(HttpHeaders.CONTENT_TYPE, "application/json");
			builder.method(method.name(), HttpRequest.BodyPublishers.ofString(json(body)));
		}
		if (auth != null) {
			builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.accessToken());
		}
		return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	private <T> T parse(HttpResponse<String> response, Class<T> type) {
		assertThat(response.statusCode()).isBetween(200, 299);
		return objectMapper.readValue(response.body(), type);
	}

	private <T> T parse(HttpResponse<String> response, TypeReference<T> type) {
		assertThat(response.statusCode()).isBetween(200, 299);
		return objectMapper.readValue(response.body(), type);
	}

	private String json(Object value) {
		return objectMapper.writeValueAsString(value);
	}

	private WebSocketStompClient stompClient() {
		SockJsClient sockJsClient = new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())));
		WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
		stompClient.setMessageConverter(
				new MappingJackson2MessageConverter(Jackson2ObjectMapperBuilder.json().build()));
		return stompClient;
	}

	private StompSession connect(WebSocketStompClient stompClient, String accessToken) throws Exception {
		StompHeaders connectHeaders = new StompHeaders();
		connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		return stompClient
			.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
					new StompSessionHandlerAdapter() {
					})
			.get(5, TimeUnit.SECONDS);
	}

	private StompFrameHandler eventFrameHandler(BlockingQueue<RealtimeEvent> events) {
		return new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return RealtimeEvent.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				events.add((RealtimeEvent) payload);
			}
		};
	}

}
