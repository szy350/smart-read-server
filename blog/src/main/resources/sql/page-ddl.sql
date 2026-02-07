-- blog.page definition

CREATE TABLE `page` (
  `id` int NOT NULL AUTO_INCREMENT,
  `book_id` bigint NOT NULL COMMENT 'book表外键',
  `page_no` bigint DEFAULT NULL COMMENT '页码',
  `type` varchar(100) DEFAULT NULL COMMENT '文件类型',
  `ori_text` longtext,
  `annotation` longtext,
  `md_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `pdf_ocr_ori_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'PDF图片专用，存储每页图片的base64的值',
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=417 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;