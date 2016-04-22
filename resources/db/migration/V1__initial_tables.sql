-- Initial tables. See https://drive.google.com/a/placeavote.com/file/d/0Byv1njCQKnxYVkNvTnVFdVRjXzg/view?usp=sharing 
-- for starting schema.

CREATE TABLE user_info (
  user_id bigint unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
  -- email length is up to 254 chars, according to RFC5321
  email varchar(255) NOT NULL UNIQUE,
  first_name varchar(255),
  last_name varchar(255),
  img_url text,
  gender varchar(6),
  dob int,
  address text,
  zipcode varchar(5),
  state text,
  -- for lat/long we are going to use DECIMAL to skip rounding imposed by FLOAT/DOUBLE.
  -- See: http://stackoverflow.com/questions/12504208/what-mysql-data-type-should-be-used-for-latitude-longitude-with-8-decimal-place
  latitude decimal(10, 8),
  longtitude decimal(11, 8),
  public_profile bool,
  created_at int,
  updated_at int,
  country_code varchar(3));

CREATE TABLE user_creds_fb (
  -- about fb id length: http://stackoverflow.com/questions/7566672/whats-the-max-length-of-a-facebook-uid
  facebook_id varchar(128) NOT NULL PRIMARY KEY,
  user_id bigint unsigned,
  FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE);

CREATE TABLE user_creds_pav (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id bigint unsigned,
  password text,
  FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE);

CREATE TABLE topic (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name text);
 
CREATE TABLE user_topic (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id bigint unsigned,
  topic_id int,
  FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE,
  FOREIGN KEY(topic_id) REFERENCES topic(id) ON DELETE SET NULL);
  
CREATE TABLE user_following_rel (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id bigint unsigned,
  following_id bigint unsigned,
  FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE,
  FOREIGN KEY(following_id) REFERENCES user_info(user_id) ON DELETE CASCADE);
  
CREATE TABLE user_votes (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id bigint unsigned,
  bill_id varchar(10),
  vote bool,
  created_at int,
  FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE);

CREATE TABLE user_issues (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  short_issue_id varchar(32) UNIQUE,
  user_id bigint unsigned,
  updated_at int,
  created_at int,
  comment_body text,
  negative_responses int unsigned,
  neutral_responses int unsigned,
  positive_responses int unsigned,
  bill_id varchar(10),
  article_link text,
  article_title text,
  article_img text,
  FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE);
  
CREATE TABLE user_issue_comments (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  issue_id int,
  FOREIGN KEY(issue_id) REFERENCES user_issues(id) ON DELETE CASCADE);

CREATE TABLE user_issue_responses (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id bigint unsigned,
  issue_id int,
  response int,
  FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE,
  FOREIGN KEY(issue_id) REFERENCES user_issues(id) ON DELETE CASCADE);

CREATE TABLE comments (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id bigint unsigned,
  parent_id int,
  body text,
  has_children bool,
  score int,
  created_at int,
  updated_at int,		
  deleted bool,
  FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE,
  FOREIGN KEY(parent_id) REFERENCES comments(id) ON DELETE CASCADE);

CREATE TABLE user_bill_comments (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  comment_id int,
  bill_id varchar(10),
  FOREIGN KEY(comment_id) REFERENCES comments(id) ON DELETE CASCADE);

CREATE TABLE user_comment_scores (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id bigint unsigned,
  comment_id int,
  liked bool,
  created_at int,
  updated_at int,
  bill_id varchar(10),
  FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE,
  FOREIGN KEY(comment_id) REFERENCES comments(id) ON DELETE CASCADE);
