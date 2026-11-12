-- 云枢智学建表与种子数据
-- 由 MySQL 官方镜像在数据目录首次初始化时执行（/docker-entrypoint-initdb.d）。
-- 库 yszx 已由环境变量 MYSQL_DATABASE 创建，本脚本不建库、不建物理外键。
-- 时间戳按 UTC 写入（容器 default-time-zone=+00:00）。

USE yszx;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 学员
-- ---------------------------------------------------------------------------

CREATE TABLE student (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  mobile               VARCHAR(11)     NOT NULL COMMENT '手机号，登录账号',
  password_hash        VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希',
  nickname             VARCHAR(50)     NOT NULL COMMENT '用户名',
  avatar               VARCHAR(500)    NULL COMMENT '头像 URL',
  email                VARCHAR(100)    NULL COMMENT '邮箱，全局唯一，允许多行 NULL',
  gender               TINYINT         NOT NULL DEFAULT 0 COMMENT '0 保密 / 1 男 / 2 女',
  preferred_subject_id BIGINT UNSIGNED NULL COMMENT '技术方向，subject.id',
  guide_finished       TINYINT         NOT NULL DEFAULT 0 COMMENT '新手引导是否看过：0 否 / 1 是',
  status               TINYINT         NOT NULL DEFAULT 1 COMMENT '1 已启用 / 0 已禁用',
  last_login_at        DATETIME        NULL COMMENT '最后登录（UTC）',
  created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_mobile (mobile),
  UNIQUE KEY uk_student_email (email),
  KEY idx_student_created_at (created_at),
  KEY idx_student_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员';

-- ---------------------------------------------------------------------------
-- 内容：学科、阶段、课程、题库、面试题、学习路线
-- ---------------------------------------------------------------------------

CREATE TABLE subject (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(50)     NOT NULL COMMENT '学科名称',
  icon        VARCHAR(50)     NOT NULL COMMENT '图标 Emoji',
  description VARCHAR(255)    NOT NULL COMMENT '描述',
  theme_color VARCHAR(20)     NOT NULL DEFAULT '#000000' COMMENT '主题色',
  sort_order  INT             NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_subject_name (name),
  KEY idx_subject_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学科';

CREATE TABLE stage (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  subject_id  BIGINT UNSIGNED NOT NULL COMMENT '学科 subject.id',
  name        VARCHAR(50)     NOT NULL COMMENT '阶段名称',
  icon        VARCHAR(50)     NULL COMMENT '图标，留空则用默认',
  sort_order  INT             NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stage_subject_name (subject_id, name),
  KEY idx_stage_subject (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶段';

CREATE TABLE course (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name             VARCHAR(100)    NOT NULL COMMENT '课程名称',
  cover_url        VARCHAR(500)    NULL COMMENT '封面 URL',
  duration_minutes INT             NOT NULL DEFAULT 0 COMMENT '时长（分钟）',
  source           TINYINT         NOT NULL DEFAULT 1 COMMENT '1 B站 / 2 其他',
  link             VARCHAR(500)    NULL COMMENT '课程链接',
  bvid             VARCHAR(20)     NULL COMMENT 'BV 号，非 B 站来源为空',
  subject_id       BIGINT UNSIGNED NULL COMMENT '主学科 subject.id，NULL 表示其他学科',
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_bvid (bvid),
  KEY idx_course_subject (subject_id),
  KEY idx_course_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='B站课程库';

CREATE TABLE course_subject (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  course_id   BIGINT UNSIGNED NOT NULL COMMENT '课程 course.id',
  subject_id  BIGINT UNSIGNED NOT NULL COMMENT '额外学科 subject.id',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_subject (course_id, subject_id),
  KEY idx_course_subject_subject (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程与额外学科';

CREATE TABLE question (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  stage_id      BIGINT UNSIGNED NOT NULL COMMENT '阶段 stage.id',
  type          TINYINT         NOT NULL COMMENT '1 单选 / 2 多选 / 3 判断 / 4 简答',
  difficulty    TINYINT         NOT NULL DEFAULT 2 COMMENT '1 简单 / 2 中等 / 3 困难',
  content       TEXT            NOT NULL COMMENT '题干',
  answer        TEXT            NULL COMMENT '判断题答案或简答参考答案；选择题为空',
  analysis      TEXT            NULL COMMENT '解析',
  tags          VARCHAR(255)    NULL COMMENT '标签，逗号分隔',
  status        TINYINT         NOT NULL DEFAULT 3 COMMENT '1 已上架 / 2 已下架 / 3 待审核 / 4 草稿（预留不启用）/ 5 已驳回',
  reject_reason VARCHAR(255)    NULL COMMENT '驳回原因，通过或重新提交时清空',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag   TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  KEY idx_question_stage (stage_id),
  KEY idx_question_status (status),
  KEY idx_question_type_difficulty (type, difficulty),
  KEY idx_question_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题库题目';

CREATE TABLE question_option (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  question_id BIGINT UNSIGNED NOT NULL COMMENT '题目 question.id',
  option_key  VARCHAR(4)      NOT NULL COMMENT '选项键 A/B/C/D',
  option_text VARCHAR(500)    NOT NULL COMMENT '选项内容',
  is_correct  TINYINT         NOT NULL DEFAULT 0 COMMENT '1 正确答案 / 0 否',
  sort_order  INT             NOT NULL DEFAULT 0 COMMENT '选项排序',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_question_option (question_id, option_key),
  KEY idx_question_option_qid (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目选项';

CREATE TABLE interview_question (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  subject_id       BIGINT UNSIGNED NOT NULL COMMENT '方向，等于学科 subject.id',
  content          TEXT            NOT NULL COMMENT '题干',
  reference_answer TEXT            NULL COMMENT '参考答案',
  topic            VARCHAR(50)     NULL COMMENT '主题，NULL 表示未分类',
  difficulty       TINYINT         NOT NULL DEFAULT 2 COMMENT '1 简单 / 2 中等 / 3 困难',
  tags             VARCHAR(255)    NULL COMMENT '标签，逗号分隔',
  source_type      TINYINT         NOT NULL DEFAULT 1 COMMENT '1 文档解析 / 2 手工录入 / 3 手动导入',
  source_doc_name  VARCHAR(255)    NULL COMMENT '来源文档名，文件本身不入库',
  source_seq       INT             NULL COMMENT '文档内题序',
  status           TINYINT         NOT NULL DEFAULT 1 COMMENT '1 已上架 / 2 已下架',
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  KEY idx_interview_question_subject_status (subject_id, status),
  KEY idx_interview_question_topic (topic),
  KEY idx_interview_question_difficulty (difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试题';

CREATE TABLE interview_topic (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  subject_id  BIGINT UNSIGNED NULL COMMENT '学科 subject.id，NULL 表示全局主题',
  name        VARCHAR(50)     NOT NULL COMMENT '主题名称',
  sort_order  INT             NOT NULL DEFAULT 0 COMMENT '排序',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  KEY idx_interview_topic_subject (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试题主题词表';

CREATE TABLE learning_route (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name              VARCHAR(100)    NOT NULL COMMENT '路线名称',
  subject_id        BIGINT UNSIGNED NOT NULL COMMENT '归属学科 subject.id',
  status            TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
  icon              VARCHAR(50)     NULL COMMENT '路线图标',
  target_education  VARCHAR(100)    NULL COMMENT '适合学历',
  description       TEXT            NULL COMMENT '路线描述',
  created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  KEY idx_learning_route_subject (subject_id),
  KEY idx_learning_route_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习路线';

CREATE TABLE route_course (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  route_id    BIGINT UNSIGNED NOT NULL COMMENT '路线 learning_route.id',
  course_id   BIGINT UNSIGNED NOT NULL COMMENT '课程 course.id',
  sort_order  INT             NOT NULL DEFAULT 0 COMMENT '学习顺序',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_route_course (route_id, course_id),
  KEY idx_route_course_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='路线课程';

CREATE TABLE route_course_completion (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id      BIGINT UNSIGNED NOT NULL COMMENT '学员 student.id',
  route_course_id BIGINT UNSIGNED NOT NULL COMMENT '路线课程 route_course.id',
  completed_at    DATETIME        NOT NULL COMMENT '完成时间（UTC）',
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_route_course_completion (student_id, route_course_id),
  KEY idx_route_course_completion_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员课程完成记录';

-- ---------------------------------------------------------------------------
-- 刷题与错题
-- ---------------------------------------------------------------------------

CREATE TABLE practice_record (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id       BIGINT UNSIGNED NOT NULL COMMENT '学员 student.id',
  question_id      BIGINT UNSIGNED NOT NULL COMMENT '题目 question.id',
  mode             TINYINT         NOT NULL DEFAULT 1 COMMENT '1 顺序 / 2 随机',
  is_correct       TINYINT         NOT NULL DEFAULT 0 COMMENT '1 正确 / 0 错误',
  duration_seconds INT             NULL COMMENT '本题用时（秒）',
  practiced_at     DATETIME        NOT NULL COMMENT '作答时间（UTC）',
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  KEY idx_practice_record_student_time (student_id, practiced_at),
  KEY idx_practice_record_student_question (student_id, question_id),
  KEY idx_practice_record_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='刷题作答明细';

CREATE TABLE wrong_question (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id       BIGINT UNSIGNED NOT NULL COMMENT '学员 student.id',
  question_id      BIGINT UNSIGNED NOT NULL COMMENT '题目 question.id',
  error_count      INT             NOT NULL DEFAULT 1 COMMENT '错误次数',
  corrected_count  INT             NOT NULL DEFAULT 0 COMMENT '连续答对次数，满 3 次视为已掌握',
  status           TINYINT         NOT NULL DEFAULT 1 COMMENT '1 待复习 / 2 已掌握',
  last_wrong_at    DATETIME        NULL COMMENT '最近答错时间（UTC）',
  last_answer      TEXT            NULL COMMENT '最近一次错误作答',
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_wrong_question (student_id, question_id),
  KEY idx_wrong_question_student_status (student_id, status),
  KEY idx_wrong_question_last_wrong (last_wrong_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='错题本';

-- ---------------------------------------------------------------------------
-- AI 面试
-- ---------------------------------------------------------------------------

CREATE TABLE interview_record (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id     BIGINT UNSIGNED NOT NULL COMMENT '学员 student.id',
  subject_id     BIGINT UNSIGNED NOT NULL COMMENT '面试方向 subject.id',
  difficulty     TINYINT         NOT NULL COMMENT '1 简单 / 2 中等 / 3 困难',
  question_count INT             NOT NULL COMMENT '题数',
  overall_score  DECIMAL(3,1)    NULL COMMENT '综合评分 0.0-10.0',
  total_duration INT             NULL COMMENT '累计时长（秒）',
  started_at     DATETIME        NOT NULL COMMENT '开始时间（UTC）',
  completed_at   DATETIME        NOT NULL COMMENT '完成时间（UTC）',
  created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  KEY idx_interview_record_student_time (student_id, created_at),
  KEY idx_interview_record_subject (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试记录';

CREATE TABLE interview_question_score (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  interview_record_id BIGINT UNSIGNED NOT NULL COMMENT '面试记录 interview_record.id',
  question_order      INT             NOT NULL COMMENT '题序，从 1 开始',
  question_id         BIGINT UNSIGNED NULL COMMENT '面试题 interview_question.id，空表示补生成快照题',
  question_text       TEXT            NOT NULL COMMENT '题干快照',
  student_answer      TEXT            NULL COMMENT '学员作答',
  score               DECIMAL(3,1)    NOT NULL COMMENT '本题得分 0.0-10.0',
  ai_feedback         TEXT            NULL COMMENT 'AI 评语',
  created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  KEY idx_interview_score_record (interview_record_id, question_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试逐题评分';

CREATE TABLE interview_report (
  id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  interview_record_id    BIGINT UNSIGNED NOT NULL COMMENT '面试记录 interview_record.id',
  radar_json             TEXT            NULL COMMENT '六维能力分析 JSON',
  strengths              TEXT            NULL COMMENT '优点',
  weaknesses             TEXT            NULL COMMENT '待提升',
  suggestions            TEXT            NULL COMMENT '改进建议',
  improvement_cards_json TEXT            NULL COMMENT '能力提升建议 JSON',
  created_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag            TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_interview_report_record (interview_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试报告';

-- ---------------------------------------------------------------------------
-- 系统管理
-- ---------------------------------------------------------------------------

CREATE TABLE role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(50)     NOT NULL COMMENT '角色名称',
  code        VARCHAR(50)     NOT NULL COMMENT '角色编码',
  description VARCHAR(255)    NULL COMMENT '描述',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

CREATE TABLE admin_user (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  username      VARCHAR(50)     NOT NULL COMMENT '用户名',
  password_hash VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希',
  mobile        VARCHAR(11)     NULL COMMENT '手机号',
  email         VARCHAR(100)    NULL COMMENT '邮箱',
  role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色 role.id',
  status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 已启用 / 0 已禁用',
  is_builtin    TINYINT         NOT NULL DEFAULT 0 COMMENT '1 内置超级管理员 / 0 否',
  last_login_at DATETIME        NULL COMMENT '最后登录（UTC）',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag   TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_user_username (username),
  UNIQUE KEY uk_admin_user_mobile (mobile),
  KEY idx_admin_user_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台用户';

CREATE TABLE permission (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  parent_id   BIGINT UNSIGNED NULL COMMENT '父节点，NULL 表示权限组',
  name        VARCHAR(50)     NOT NULL COMMENT '名称',
  code        VARCHAR(100)    NOT NULL COMMENT '权限编码',
  type        TINYINT         NOT NULL COMMENT '1 组 / 2 操作点',
  sort_order  INT             NOT NULL DEFAULT 0 COMMENT '排序',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_permission_code (code),
  KEY idx_permission_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限';

CREATE TABLE role_permission (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色 role.id',
  permission_id BIGINT UNSIGNED NOT NULL COMMENT '权限 permission.id',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_permission (role_id, permission_id),
  KEY idx_role_permission_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限';

CREATE TABLE operation_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '后台用户 admin_user.id',
  username    VARCHAR(50)     NOT NULL COMMENT '用户名快照',
  action      VARCHAR(50)     NOT NULL COMMENT '登录/登出/新增/修改/删除/禁用启用/导出/其他',
  target_type VARCHAR(50)     NOT NULL COMMENT '操作对象类型',
  target_name TEXT            NOT NULL COMMENT '操作对象描述，修改操作会写明变更字段',
  ip          VARCHAR(64)     NOT NULL COMMENT 'IP 地址',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间（UTC）',
  PRIMARY KEY (id),
  KEY idx_operation_log_user (user_id),
  KEY idx_operation_log_time (created_at),
  KEY idx_operation_log_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';

CREATE TABLE system_setting (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  config_key   VARCHAR(50)     NOT NULL COMMENT '配置键',
  config_value TEXT            NULL COMMENT '配置值',
  remark       VARCHAR(255)    NULL COMMENT '说明',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  delete_flag  TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_setting_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统设置';

-- ---------------------------------------------------------------------------
-- 种子数据
-- ---------------------------------------------------------------------------

INSERT INTO subject (id, name, icon, description, theme_color, sort_order) VALUES
  (1, 'AI应用与智能体开发', '🤖', '智能体应用与工程化实践', '#4F46E5', 1),
  (2, 'AI大模型开发', '🧠', '大模型训练、微调与应用开发', '#7C3AED', 2),
  (3, 'AI具身智能机器人开发', '🦾', '具身智能与机器人系统开发', '#0891B2', 3),
  (4, 'AI嵌入式_机器人开发', '📟', '嵌入式与机器人端侧开发', '#0F766E', 4),
  (5, 'AI测试', '🧪', 'AI 系统测试与质量保障', '#D97706', 5),
  (6, 'AI运维', '🛠️', 'AI 平台运维与稳定性保障', '#DC2626', 6),
  (7, 'AI视觉设计', '🎨', 'AI 视觉设计与图像创作', '#DB2777', 7),
  (8, 'AI视频与短剧创作', '🎬', 'AI 视频与短剧内容创作', '#EA580C', 8);

INSERT INTO role (id, name, code, description) VALUES
  (1, '超级管理员', 'super_admin', '全部权限，内置唯一'),
  (2, '内容审核', 'content_reviewer', '查看全部内容并审核题目，无新增、编辑、删除、导入导出权限'),
  (3, '运营人员', 'operator', '除系统管理外的全部操作，不含题库审核');

-- 初始密码 Admin@123456。上线后请立即修改。
INSERT INTO admin_user (id, username, password_hash, mobile, email, role_id, status, is_builtin) VALUES
  (1, 'admin', '$2b$10$1ATAka7g86RIjDAtj4Vwh.ucG2eZNBuwJZ/.lsHv/DiEtrEXwu1v.', NULL, NULL, 1, 1, 1);

INSERT INTO permission (id, parent_id, name, code, type, sort_order) VALUES
  (90, NULL, '数据看板', 'dashboard', 1, 1),
  (70, NULL, '学科管理', 'subject', 1, 2),
  (80, NULL, 'B站课程库', 'course', 1, 3),
  (10, NULL, '题库管理', 'question', 1, 4),
  (20, NULL, '面试题库管理', 'interviewbank', 1, 5),
  (40, NULL, '学习路线', 'route', 1, 6),
  (50, NULL, '学员管理', 'student', 1, 7),
  (30, NULL, '用户管理', 'user', 1, 8),
  (60, NULL, '系统管理', 'system', 1, 9),
  (79, 90, '查看', 'dashboard:view', 2, 1),
  (71, 70, '查看', 'subject:view', 2, 1),
  (72, 70, '新增', 'subject:add', 2, 2),
  (73, 70, '编辑', 'subject:edit', 2, 3),
  (74, 70, '删除', 'subject:delete', 2, 4),
  (75, 80, '查看', 'course:view', 2, 1),
  (76, 80, '新增', 'course:add', 2, 2),
  (77, 80, '编辑', 'course:edit', 2, 3),
  (78, 80, '删除', 'course:delete', 2, 4),
  (11, 10, '查看', 'question:view', 2, 1),
  (12, 10, '新增', 'question:add', 2, 2),
  (13, 10, '编辑', 'question:edit', 2, 3),
  (14, 10, '删除', 'question:delete', 2, 4),
  (15, 10, '导入', 'question:import', 2, 5),
  (16, 10, '导出', 'question:export', 2, 6),
  (17, 10, '审核', 'question:audit', 2, 7),
  (21, 20, '查看', 'interviewbank:view', 2, 1),
  (22, 20, '上传', 'interviewbank:upload', 2, 2),
  (23, 20, '编辑', 'interviewbank:edit', 2, 3),
  (24, 20, '删除', 'interviewbank:delete', 2, 4),
  (25, 20, '检索测试', 'interviewbank:search_test', 2, 5),
  (41, 40, '查看', 'route:view', 2, 1),
  (42, 40, '新增', 'route:add', 2, 2),
  (43, 40, '编辑', 'route:edit', 2, 3),
  (44, 40, '删除', 'route:delete', 2, 4),
  (51, 50, '查看', 'student:view', 2, 1),
  (52, 50, '编辑', 'student:edit', 2, 2),
  (53, 50, '删除', 'student:delete', 2, 3),
  (31, 30, '查看', 'user:view', 2, 1),
  (32, 30, '新增', 'user:add', 2, 2),
  (33, 30, '编辑', 'user:edit', 2, 3),
  (34, 30, '禁用', 'user:disable', 2, 4),
  (61, 60, '日志查看', 'system:log_view', 2, 1),
  (62, 60, '系统设置', 'system:setting', 2, 2);

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, id FROM permission;

INSERT INTO role_permission (role_id, permission_id)
SELECT 2, id FROM permission
WHERE code IN (
  'dashboard', 'subject', 'course', 'question', 'interviewbank', 'route',
  'dashboard:view', 'subject:view', 'course:view',
  'question:view', 'question:audit', 'interviewbank:view', 'route:view'
);

INSERT INTO role_permission (role_id, permission_id)
SELECT 3, id FROM permission
WHERE code NOT IN ('system', 'system:log_view', 'system:setting', 'question:audit');

INSERT INTO system_setting (id, config_key, config_value, remark) VALUES
  (1, 'site_name', '云枢智学', '平台名称'),
  (2, 'site_description', NULL, '平台描述'),
  (3, 'logo_url', NULL, '平台 Logo'),
  (4, 'default_avatar', NULL, '默认头像');
