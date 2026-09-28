package com.todo.shared.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.todo.boards.domain.Board;
import com.todo.boards.domain.BoardMember;
import com.todo.boards.domain.BoardRole;
import com.todo.boards.domain.BoardType;
import com.todo.boards.repository.BoardMemberRepository;
import com.todo.boards.repository.BoardRepository;
import com.todo.columns.domain.BoardColumn;
import com.todo.columns.repository.BoardColumnRepository;
import com.todo.tasks.domain.Comment;
import com.todo.tasks.domain.Subtask;
import com.todo.tasks.domain.Task;
import com.todo.tasks.repository.CommentRepository;
import com.todo.tasks.repository.SubtaskRepository;
import com.todo.tasks.repository.TaskRepository;
import com.todo.users.domain.User;
import com.todo.users.repository.UserRepository;

@Component
@Profile("dev")
public class DataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

	private final UserRepository userRepository;

	private final BoardRepository boardRepository;

	private final BoardMemberRepository boardMemberRepository;

	private final BoardColumnRepository boardColumnRepository;

	private final TaskRepository taskRepository;

	private final SubtaskRepository subtaskRepository;

	private final CommentRepository commentRepository;

	private final PasswordEncoder passwordEncoder;

	public DataSeeder(UserRepository userRepository, BoardRepository boardRepository,
			BoardMemberRepository boardMemberRepository, BoardColumnRepository boardColumnRepository,
			TaskRepository taskRepository, SubtaskRepository subtaskRepository, CommentRepository commentRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.boardRepository = boardRepository;
		this.boardMemberRepository = boardMemberRepository;
		this.boardColumnRepository = boardColumnRepository;
		this.taskRepository = taskRepository;
		this.subtaskRepository = subtaskRepository;
		this.commentRepository = commentRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.count() > 0) {
			log.info("Seed skipped: there is already data in the database");
			return;
		}
		User demo = userRepository
			.save(User.local("Demo User", "demo@example.com", passwordEncoder.encode("password123")));

		Board board = boardRepository
			.save(new Board("Demo Board", "Tablero colaborativo de ejemplo", BoardType.COLLABORATIVE, demo));
		boardMemberRepository.save(new BoardMember(board, demo, BoardRole.OWNER));
		BoardColumn todo = boardColumnRepository.save(new BoardColumn(board, "Todo", 0));
		BoardColumn doing = boardColumnRepository.save(new BoardColumn(board, "Doing", 1));
		boardColumnRepository.save(new BoardColumn(board, "Done", 2));

		Task design = taskRepository
			.save(new Task(todo, "Disenar el modelo de datos", "Entidades, relaciones y migraciones", 0, demo));
		taskRepository.save(new Task(todo, "Configurar CI", null, 1, demo));
		Task auth = taskRepository
			.save(new Task(doing, "Implementar autenticacion", "JWT + refresh tokens + OAuth2", 0, demo));
		auth.assign(demo);
		subtaskRepository.save(new Subtask(auth, "Login local", 0));
		subtaskRepository.save(new Subtask(auth, "OAuth Google", 1));
		commentRepository.save(new Comment(design, demo, "Primer comentario de ejemplo"));

		Board personal = boardRepository
			.save(new Board("Notas personales", "Tablero personal de ejemplo", BoardType.PERSONAL, demo));
		boardMemberRepository.save(new BoardMember(personal, demo, BoardRole.OWNER));
		taskRepository.save(new Task(boardColumnRepository.save(new BoardColumn(personal, "Ideas", 0)),
				"Probar el tiempo real con STOMP", null, 0, demo));

		log.info("Seeded demo data: demo@example.com / password123");
	}

}
