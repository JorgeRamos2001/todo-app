package com.todo.boards;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import com.todo.columns.domain.BoardColumn;
import com.todo.columns.dto.CreateColumnRequest;
import com.todo.columns.repository.BoardColumnRepository;
import com.todo.invitations.dto.CreateInvitationRequest;
import com.todo.invitations.dto.InvitationResponse;
import com.todo.invitations.repository.InvitationRepository;
import com.todo.tasks.dto.AssignTaskRequest;
import com.todo.tasks.dto.CreateCommentRequest;
import com.todo.tasks.dto.CreateTaskRequest;
import com.todo.tasks.dto.TaskResponse;
import com.todo.tasks.dto.UpdateTaskRequest;
import com.todo.tasks.repository.CommentRepository;
import com.todo.tasks.repository.SubtaskRepository;
import com.todo.tasks.repository.TaskRepository;
import com.todo.users.domain.User;
import com.todo.users.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class BoardActivityFlowIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mockMvc;

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

	@Autowired
	JwtService jwtService;

	private User alice;

	private User bob;

	private User carol;

	private Board board;

	private BoardColumn todoColumn;

	private BoardColumn doingColumn;

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
		bob = userRepository.save(User.local("Bob", "bob@example.com", "hash"));
		carol = userRepository.save(User.local("Carol", "carol@example.com", "hash"));
		board = boardRepository.save(new Board("Board", null, BoardType.COLLABORATIVE, alice));
		boardMemberRepository.save(new BoardMember(board, alice, BoardRole.OWNER));
		boardMemberRepository.save(new BoardMember(board, bob, BoardRole.MEMBER));
		todoColumn = boardColumnRepository.save(new BoardColumn(board, "Todo", 0));
		doingColumn = boardColumnRepository.save(new BoardColumn(board, "Doing", 1));
	}

	@Test
	void mutationsAreRecorded() throws Exception {
		createColumn(alice, "Review");
		TaskResponse task = createTask(alice, todoColumn.getId(), "Task 1");
		assign(alice, task.id(), bob.getId());
		comment(bob, task.id(), "Looks good");

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/activities", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(4))
			.andExpect(jsonPath("$.content[0].action").value("COMMENT_CREATED"))
			.andExpect(jsonPath("$.content[0].actorName").value("Bob"))
			.andExpect(jsonPath("$.content[1].action").value("TASK_ASSIGNED"))
			.andExpect(jsonPath("$.content[2].action").value("TASK_CREATED"))
			.andExpect(jsonPath("$.content[3].action").value("COLUMN_CREATED"))
			.andExpect(jsonPath("$.content[3].details.name").value("Review"));
	}

	@Test
	void activitiesAreVisibleOnlyToOwner() throws Exception {
		createColumn(alice, "Review");

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/activities", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/activities", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(carol)))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/activities", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk());
	}

	@Test
	void activitiesArePaginated() throws Exception {
		createColumn(alice, "One");
		createColumn(alice, "Two");
		createColumn(alice, "Three");

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/activities", board.getId()).param("size", "2")
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.content.length()").value(2));

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/activities", board.getId()).param("page", "1").param("size", "2")
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	void taskMovedDetailsIncludeSourceAndTargetColumns() throws Exception {
		TaskResponse task = createTask(alice, todoColumn.getId(), "Task");

		mockMvc
			.perform(patch("/api/v1/tasks/{taskId}", task.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new UpdateTaskRequest(null, null, doingColumn.getId(), 0))))
			.andExpect(status().isOk());

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/activities", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].action").value("TASK_MOVED"))
			.andExpect(jsonPath("$.content[0].entityId").value(task.id().intValue()))
			.andExpect(jsonPath("$.content[0].details.fromColumnId").value(todoColumn.getId().intValue()))
			.andExpect(jsonPath("$.content[0].details.toColumnId").value(doingColumn.getId().intValue()));
	}

	@Test
	void invitationAcceptanceRecordsInvitationAndMemberEvents() throws Exception {
		InvitationResponse invitation = invite(alice, carol.getEmail(), BoardRole.MEMBER);

		mockMvc
			.perform(post("/api/v1/invitations/{token}/accept", invitation.token())
				.header(HttpHeaders.AUTHORIZATION, authorization(carol)))
			.andExpect(status().isOk());

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/activities", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[0].action").value("MEMBER_JOINED"))
			.andExpect(jsonPath("$.content[1].action").value("INVITATION_ACCEPTED"))
			.andExpect(jsonPath("$.content[2].action").value("INVITATION_CREATED"));
	}

	@Test
	void rejectedMutationsAreNotRecorded() throws Exception {
		mockMvc
			.perform(post("/api/v1/boards/{boardId}/columns", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateColumnRequest("Nope"))))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/activities", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	private void createColumn(User actor, String name) throws Exception {
		mockMvc
			.perform(post("/api/v1/boards/{boardId}/columns", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateColumnRequest(name))))
			.andExpect(status().isCreated());
	}

	private TaskResponse createTask(User actor, Long columnId, String title) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/columns/{columnId}/tasks", columnId)
				.header(HttpHeaders.AUTHORIZATION, authorization(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateTaskRequest(title, null))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), TaskResponse.class);
	}

	private void assign(User actor, Long taskId, Long assigneeId) throws Exception {
		mockMvc
			.perform(post("/api/v1/tasks/{taskId}/assignee", taskId)
				.header(HttpHeaders.AUTHORIZATION, authorization(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new AssignTaskRequest(assigneeId))))
			.andExpect(status().isOk());
	}

	private void comment(User actor, Long taskId, String content) throws Exception {
		mockMvc
			.perform(post("/api/v1/tasks/{taskId}/comments", taskId)
				.header(HttpHeaders.AUTHORIZATION, authorization(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateCommentRequest(content))))
			.andExpect(status().isCreated());
	}

	private InvitationResponse invite(User actor, String email, BoardRole role) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/boards/{boardId}/invitations", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateInvitationRequest(email, role))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), InvitationResponse.class);
	}

	private String authorization(User user) {
		return "Bearer " + jwtService.generateAccessToken(user);
	}

	private String json(Object value) {
		return objectMapper.writeValueAsString(value);
	}

}
