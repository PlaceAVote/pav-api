CREATE TABLE user_info (
	user_id CHAR(36) NOT NULL UNIQUE PRIMARY KEY,
	email VARCHAR(50) NOT NULL UNIQUE,
	facebook_id BIGINT(64),
	password VARCHAR(255),
	first_name VARCHAR(50) NOT NULL,
	last_name VARCHAR(50) NOT NULL,
	gender VARCHAR(10) NOT NULL,
	dob DATE NOT NULL,
	created_at TIMESTAMP NOT NULL,
	topics VARCHAR(255) NOT NULL,
	origin VARCHAR(20) NOT NULL,
    img_url VARCHAR(255),
    public boolean,
    token MEDIUMBLOB NOT NULL,
    address TEXT NOT NULL,
    zipcode INT(5) NOT NULL,
    lat DOUBLE NOT NULL,
    lng DOUBLE NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    state VARCHAR(2),
   	district INT
);

CREATE TABLE user_following_rel (
    follower_id CHAR(36) NOT NULL,
    following_id CHAR(36) NOT NULL,
    FOREIGN KEY (follower_id) REFERENCES user_info(user_id) ON DELETE CASCADE,
    FOREIGN KEY (following_id) REFERENCES user_info(user_id) ON DELETE CASCADE
);

CREATE TABLE user_issues (
    issue_id CHAR(36) NOT NULL UNIQUE PRIMARY KEY,
    short_issue_id CHAR(32) NOT NULL,
    user_id CHAR(36) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    negative_responses INT NOT NULL DEFAULT 0,
    neutral_responses INT NOT NULL DEFAULT 0,
    positive_responses INT NOT NULL DEFAULT 0,
    comment TEXT,
    bill_id VARCHAR(10),
    bill_title TEXT,
    article_img VARCHAR(255),
    article_link VARCHAR(255),
    article_title VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES user_info(user_id) ON DELETE CASCADE
);

CREATE TABLE user_issue_responses (
    issue_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    emotional_response VARCHAR(20),
    FOREIGN KEY (issue_id) REFERENCES user_issues(issue_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES user_info(user_id) ON DELETE CASCADE,
    PRIMARY KEY (issue_id, user_id)
);
