CREATE TABLE user_info (
	user_id CHAR(36) NOT NULL UNIQUE PRIMARY KEY,
	email VARCHAR(50) NOT NULL UNIQUE,
	password VARCHAR(255),
	first_name VARCHAR(50) NOT NULL,
	last_name VARCHAR(50) NOT NULL,
	gender VARCHAR(10) NOT NULL,
	dob DATE NOT NULL,
	country_code VARCHAR(3) NOT NULL,
	state VARCHAR(2),
	district INT,
	created_at TIMESTAMP NOT NULL,
	topics VARCHAR(255) NOT NULL,
	origin VARCHAR(20) NOT NULL,
    img_url VARCHAR(255),
    public boolean,
    token MEDIUMBLOB NOT NULL
);
