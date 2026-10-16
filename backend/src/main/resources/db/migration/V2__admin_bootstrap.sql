-- Serializa el alta inicial incluso si se inician varias instancias a la vez.
CREATE TABLE admin_bootstrap_guard (
    id SMALLINT PRIMARY KEY CHECK (id = 1)
);
INSERT INTO admin_bootstrap_guard (id) VALUES (1);
CREATE UNIQUE INDEX uq_admin_username_ignore_case ON admin_user (lower(username));
