-- blog.user_his_action definition

CREATE TABLE `user_his_action` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `book_id` bigint DEFAULT NULL,
  `note_id` bigint DEFAULT NULL,
  `book_name` varchar(256) DEFAULT NULL,
  `page_no` bigint DEFAULT NULL,
  `context` varchar(2000) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;