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
