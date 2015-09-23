ALTER TABLE user_token MODIFY COLUMN token VARCHAR(767);

CREATE TABLE user_social_token (
	id MEDIUMINT NOT NULL UNIQUE PRIMARY KEY AUTO_INCREMENT,
    user_id MEDIUMINT NOT NULL UNIQUE,
    token VARCHAR(767) NOT NULL UNIQUE KEY,
    FOREIGN KEY (user_id) REFERENCES user_info(id) ON DELETE CASCADE
);