package com.todo.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.todo.boards.repository.BoardMemberRepository;
import com.todo.boards.repository.BoardRepository;
import com.todo.columns.domain.BoardColumn;
import com.todo.columns.dto.CreateColumnRequest;
import com.todo.columns.dto.UpdateColumnRequest;
import com.todo.columns.repository.BoardColumnRepository;
import com.todo.tasks.dto.AssignTaskRequest;
import com.todo.tasks.dto.CommentResponse;
import com.todo.tasks.dto.CreateCommentRequest;
import com.todo.tasks.dto.CreateSubtaskRequest;
import com.todo.tasks.dto.CreateTaskRequest;
import com.todo.tasks.dto.SubtaskResponse;
import com.todo.tasks.dto.TaskResponse;
import com.todo.tasks.dto.UpdateSubtaskRequest;
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
class TaskFlowIntegrationTests {

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
		commentRepository.deleteAll();
		subtaskRepository.deleteAll();
		taskRepository.deleteAll();
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
	void createColumnAppendsAtEnd() throws Exception {
		mockMvc
			.perform(post("/api/v1/boards/{boardId}/columns", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateColumnRequest("Done"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Done"))
			.andExpect(jsonPath("$.position").value(2));
	}

	@Test
	void memberCannotManageColumns() throws Exception {
		mockMvc
			.perform(post("/api/v1/boards/{boardId}/columns", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateColumnRequest("Nope"))))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(patch("/api/v1/boards/{boardId}/columns/{columnId}", board.getId(), todoColumn.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new UpdateColumnRequest("Renamed", null))))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(delete("/api/v1/boards/{boardId}/columns/{columnId}", board.getId(), todoColumn.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isForbidden());
	}

	@Test
	void reorderColumns() throws Exception {
		mockMvc
			.perform(patch("/api/v1/boards/{boardId}/columns/{columnId}", board.getId(), doingColumn.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new UpdateColumnRequest(null, 0))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.position").value(0));

		mockMvc
			.perform(get("/api/v1/boards/{boardId}/columns", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("Doing"))
			.andExpect(jsonPath("$[0].position").value(0))
			.andExpect(jsonPath("$[1].name").value("Todo"))
			.andExpect(jsonPath("$[1].position").value(1));
	}

	@Test
	void createTaskAppendsAndLists() throws Exception {
		TaskResponse first = createTask(todoColumn.getId(), alice, "First");
		TaskResponse second = createTask(todoColumn.getId(), alice, "Second");

		assertThat(first.position()).isZero();
		assertThat(second.position()).isEqualTo(1);

		mockMvc
			.perform(get("/api/v1/columns/{columnId}/tasks", todoColumn.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].title").value("First"))
			.andExpect(jsonPath("$[1].title").value("Second"));
	}

	@Test
	void moveTaskBetweenColumns() throws Exception {
		TaskResponse first = createTask(todoColumn.getId(), alice, "First");
		TaskResponse second = createTask(todoColumn.getId(), alice, "Second");

		mockMvc
			.perform(patch("/api/v1/tasks/{taskId}", second.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new UpdateTaskRequest(null, null, doingColumn.getId(), 0))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.columnId").value(doingColumn.getId()))
			.andExpect(jsonPath("$.position").value(0));

		mockMvc
			.perform(get("/api/v1/columns/{columnId}/tasks", todoColumn.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(first.id()))
			.andExpect(jsonPath("$[0].position").value(0));

		mockMvc
			.perform(get("/api/v1/columns/{columnId}/tasks", doingColumn.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(second.id()));
	}

	@Test
	void taskCannotMoveToAnotherBoard() throws Exception {
		TaskResponse task = createTask(todoColumn.getId(), alice, "Task");
		Board otherBoard = boardRepository.save(new Board("Other", null, BoardType.COLLABORATIVE, alice));
		BoardColumn otherColumn = boardColumnRepository.save(new BoardColumn(otherBoard, "Other", 0));

		mockMvc
			.perform(patch("/api/v1/tasks/{taskId}", task.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new UpdateTaskRequest(null, null, otherColumn.getId(), 0))))
			.andExpect(status().isBadRequest());
	}

	@Test
	void assigneeMustBeBoardMember() throws Exception {
		TaskResponse task = createTask(todoColumn.getId(), alice, "Task");

		mockMvc
			.perform(post("/api/v1/tasks/{taskId}/assignee", task.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new AssignTaskRequest(carol.getId()))))
			.andExpect(status().isBadRequest());

		mockMvc
			.perform(post("/api/v1/tasks/{taskId}/assignee", task.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new AssignTaskRequest(bob.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assigneeId").value(bob.getId()))
			.andExpect(jsonPath("$.assigneeName").value("Bob"));
	}

	@Test
	void memberCanOnlyEditAndMoveAssignedTask() throws Exception {
		TaskResponse task = createTask(todoColumn.getId(), alice, "Task");

		mockMvc
			.perform(patch("/api/v1/tasks/{taskId}", task.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new UpdateTaskRequest("Hacked", null, null, null))))
			.andExpect(status().isForbidden());

		assign(task.id(), alice, bob);

		mockMvc
			.perform(patch("/api/v1/tasks/{taskId}", task.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new UpdateTaskRequest("Edited by member", "desc", null, null))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Edited by member"));

		mockMvc
			.perform(patch("/api/v1/tasks/{taskId}", task.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new UpdateTaskRequest(null, null, doingColumn.getId(), 0))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.columnId").value(doingColumn.getId()));
	}

	@Test
	void memberCannotCreateOrDeleteTasks() throws Exception {
		mockMvc
			.perform(post("/api/v1/columns/{columnId}/tasks", todoColumn.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateTaskRequest("Member task", null))))
			.andExpect(status().isForbidden());

		TaskResponse task = createTask(todoColumn.getId(), alice, "Task");

		mockMvc
			.perform(delete("/api/v1/tasks/{taskId}", task.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isForbidden());
	}

	@Test
	void subtasksFollowTaskAssignment() throws Exception {
		TaskResponse task = createTask(todoColumn.getId(), alice, "Task");

		mockMvc
			.perform(post("/api/v1/tasks/{taskId}/subtasks", task.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateSubtaskRequest("Step 1"))))
			.andExpect(status().isForbidden());

		assign(task.id(), alice, bob);

		SubtaskResponse first = createSubtask(task.id(), bob, "Step 1");
		SubtaskResponse second = createSubtask(task.id(), bob, "Step 2");
		assertThat(first.position()).isZero();
		assertThat(second.position()).isEqualTo(1);

		mockMvc
			.perform(patch("/api/v1/tasks/{taskId}/subtasks/{subtaskId}", task.id(), second.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new UpdateSubtaskRequest(null, true, 0))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.done").value(true))
			.andExpect(jsonPath("$.position").value(0));

		mockMvc
			.perform(get("/api/v1/tasks/{taskId}/subtasks", task.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(second.id()))
			.andExpect(jsonPath("$[1].id").value(first.id()));

		mockMvc
			.perform(delete("/api/v1/tasks/{taskId}/subtasks/{subtaskId}", task.id(), first.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isNoContent());

		assertThat(subtaskRepository.findByTaskIdOrderByPosition(task.id())).hasSize(1);
	}

	@Test
	void commentsPermissions() throws Exception {
		TaskResponse task = createTask(todoColumn.getId(), alice, "Task");

		CommentResponse bobComment = createComment(task.id(), bob, "Bob comment");
		CommentResponse aliceComment = createComment(task.id(), alice, "Alice comment");

		mockMvc
			.perform(delete("/api/v1/tasks/{taskId}/comments/{commentId}", task.id(), aliceComment.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(delete("/api/v1/tasks/{taskId}/comments/{commentId}", task.id(), bobComment.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isNoContent());

		mockMvc
			.perform(delete("/api/v1/tasks/{taskId}/comments/{commentId}", task.id(), aliceComment.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isNoContent());

		mockMvc
			.perform(get("/api/v1/tasks/{taskId}/comments", task.id())
				.header(HttpHeaders.AUTHORIZATION, authorization(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void boardDetailIncludesColumnsAndTasks() throws Exception {
		createTask(todoColumn.getId(), alice, "Task in todo");

		mockMvc
			.perform(get("/api/v1/boards/{boardId}", board.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.columns.length()").value(2))
			.andExpect(jsonPath("$.columns[0].name").value("Todo"))
			.andExpect(jsonPath("$.columns[0].tasks.length()").value(1))
			.andExpect(jsonPath("$.columns[0].tasks[0].title").value("Task in todo"))
			.andExpect(jsonPath("$.columns[1].name").value("Doing"))
			.andExpect(jsonPath("$.columns[1].tasks.length()").value(0));
	}

	@Test
	void nonMemberCannotAccessTasks() throws Exception {
		mockMvc
			.perform(get("/api/v1/columns/{columnId}/tasks", todoColumn.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization(carol)))
			.andExpect(status().isForbidden());
	}

	private TaskResponse createTask(Long columnId, User actor, String title) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/columns/{columnId}/tasks", columnId)
				.header(HttpHeaders.AUTHORIZATION, authorization(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateTaskRequest(title, null))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), TaskResponse.class);
	}

	private void assign(Long taskId, User actor, User assignee) throws Exception {
		mockMvc
			.perform(post("/api/v1/tasks/{taskId}/assignee", taskId)
				.header(HttpHeaders.AUTHORIZATION, authorization(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new AssignTaskRequest(assignee.getId()))))
			.andExpect(status().isOk());
	}

	private SubtaskResponse createSubtask(Long taskId, User actor, String title) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/tasks/{taskId}/subtasks", taskId)
				.header(HttpHeaders.AUTHORIZATION, authorization(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateSubtaskRequest(title))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), SubtaskResponse.class);
	}

	private CommentResponse createComment(Long taskId, User actor, String content) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/tasks/{taskId}/comments", taskId)
				.header(HttpHeaders.AUTHORIZATION, authorization(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(new CreateCommentRequest(content))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), CommentResponse.class);
	}

	private String authorization(User user) {
		return "Bearer " + jwtService.generateAccessToken(user);
	}

	private String json(Object value) {
		return objectMapper.writeValueAsString(value);
	}

}
