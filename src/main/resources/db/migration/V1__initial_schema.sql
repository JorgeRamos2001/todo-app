CREATE TABLE users (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255),
    provider   VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_provider CHECK (provider IN ('LOCAL', 'GOOGLE'))
);

CREATE TABLE boards (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    type        VARCHAR(20) NOT NULL,
    owner_id    BIGINT NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_boards_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_boards_type CHECK (type IN ('PERSONAL', 'COLLABORATIVE'))
);

CREATE TABLE board_members (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    board_id BIGINT NOT NULL,
    user_id  BIGINT NOT NULL,
    role     VARCHAR(20) NOT NULL,
    CONSTRAINT fk_board_members_board FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE,
    CONSTRAINT fk_board_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_board_members_board_user UNIQUE (board_id, user_id),
    CONSTRAINT ck_board_members_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE TABLE board_columns (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    board_id BIGINT NOT NULL,
    name     VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_board_columns_board FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE
);

CREATE TABLE tasks (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    column_id   BIGINT NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    position    INTEGER NOT NULL DEFAULT 0,
    assignee_id BIGINT,
    created_by  BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_tasks_column FOREIGN KEY (column_id) REFERENCES board_columns (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE subtasks (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id  BIGINT NOT NULL,
    title    VARCHAR(255) NOT NULL,
    done     BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_subtasks_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE
);

CREATE TABLE comments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id    BIGINT NOT NULL,
    author_id  BIGINT NOT NULL,
    content    TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_comments_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE TABLE board_activities (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    board_id    BIGINT NOT NULL,
    actor_id    BIGINT NOT NULL,
    action      VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id   BIGINT,
    details     JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_board_activities_board FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE,
    CONSTRAINT fk_board_activities_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);

CREATE TABLE invitations (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    board_id      BIGINT NOT NULL,
    inviter_id    BIGINT NOT NULL,
    invitee_email VARCHAR(255) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    token         VARCHAR(255) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_invitations_board FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitations_inviter FOREIGN KEY (inviter_id) REFERENCES users (id),
    CONSTRAINT uk_invitations_token UNIQUE (token),
    CONSTRAINT ck_invitations_role CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT ck_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED'))
);

CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_boards_owner_id ON boards (owner_id);
CREATE INDEX idx_board_members_user_id ON board_members (user_id);
CREATE INDEX idx_board_columns_board_position ON board_columns (board_id, position);
CREATE INDEX idx_tasks_column_position ON tasks (column_id, position);
CREATE INDEX idx_tasks_assignee_id ON tasks (assignee_id);
CREATE INDEX idx_subtasks_task_position ON subtasks (task_id, position);
CREATE INDEX idx_comments_task_created_at ON comments (task_id, created_at);
CREATE INDEX idx_board_activities_board_created_at ON board_activities (board_id, created_at DESC);
CREATE INDEX idx_invitations_board_id ON invitations (board_id);
CREATE INDEX idx_invitations_invitee_email ON invitations (invitee_email);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
