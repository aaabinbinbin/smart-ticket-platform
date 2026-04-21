-- 浼佷笟鏅鸿兘宸ュ崟鍗忓悓骞冲彴鏁版嵁搴撳垵濮嬪寲鑴氭湰銆?-- 褰撳墠鑴氭湰鍙礋璐ｅ垱寤?MySQL 涓讳笟鍔″簱鍜?MVP 闃舵鏍稿績琛ㄣ€?-- RAG 鍚戦噺瀛楁鍚庣画鎺ュ叆 pgvector 鏃跺崟鐙淮鎶わ紝涓嶆斁鍦?MySQL 涓诲簱涓€?
-- 鍒涘缓涓氬姟鏁版嵁搴擄紝缁熶竴浣跨敤 utf8mb4 浠ユ敮鎸佷腑鏂囥€佽嫳鏂囧拰绗﹀彿鍐呭銆?CREATE DATABASE IF NOT EXISTS smart_ticket_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_ticket_platform;

-- 绯荤粺鐢ㄦ埛琛ㄣ€?-- 淇濆瓨鐧诲綍璐﹀彿銆佸瘑鐮佹憳瑕併€佸睍绀哄鍚嶃€侀偖绠卞拰璐﹀彿鍚仠鐘舵€併€?-- 杩欓噷鍙繚瀛樼敤鎴峰熀纭€韬唤淇℃伅锛屼笉淇濆瓨鐢ㄦ埛鍦ㄦ煇寮犲伐鍗曚腑鐨勪笟鍔′綅缃€?CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '鐢ㄦ埛涓婚敭',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '鐧诲綍鐢ㄦ埛鍚嶏紝鍏ㄥ眬鍞竴',
    password_hash VARCHAR(255) NOT NULL COMMENT '瀵嗙爜鍝堝笇鍊硷紝涓嶄繚瀛樻槑鏂囧瘑鐮?,
    real_name VARCHAR(64) NOT NULL COMMENT '鐢ㄦ埛鐪熷疄濮撳悕鎴栧睍绀哄悕',
    email VARCHAR(128) COMMENT '閭鍦板潃',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '璐﹀彿鐘舵€侊細1-鍚敤锛?-绂佺敤',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 绯荤粺瑙掕壊琛ㄣ€?-- 绗竴鐗堝浐瀹氫娇鐢?USER / STAFF / ADMIN 涓夌被瑙掕壊銆?-- 瑙掕壊鍙〃绀虹郴缁熻兘鍔涳紝涓嶈〃绀烘彁鍗曚汉銆佸鐞嗕汉杩欑被宸ュ崟鍐呬笟鍔″叧绯汇€?CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '瑙掕壊涓婚敭',
    role_code VARCHAR(64) NOT NULL UNIQUE COMMENT '瑙掕壊缂栫爜锛歎SER/STAFF/ADMIN',
    role_name VARCHAR(64) NOT NULL COMMENT '瑙掕壊鍚嶇О',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 鐢ㄦ埛瑙掕壊鍏宠仈琛ㄣ€?-- 涓€涓敤鎴峰彲浠ユ嫢鏈夊涓鑹诧紝渚嬪澶勭悊浜哄憳鍚屾椂鎷ユ湁 USER 鍜?STAFF銆?CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '鍏宠仈涓婚敭',
    user_id BIGINT NOT NULL COMMENT '鐢ㄦ埛 ID',
    role_id BIGINT NOT NULL COMMENT '瑙掕壊 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 宸ュ崟涓昏〃銆?-- 淇濆瓨宸ュ崟褰撳墠浜嬪疄鐘舵€侊細褰撳墠鐘舵€併€佸綋鍓嶈礋璐ｄ汉銆佷紭鍏堢骇銆佸垎绫荤瓑銆?-- 璇勮銆佹搷浣滄棩蹇椼€侀檮浠剁瓑杩囩▼鏁版嵁鎷嗗垎鍒扮嫭绔嬭〃銆?CREATE TABLE IF NOT EXISTS ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '宸ュ崟涓婚敭',
    ticket_no VARCHAR(32) NOT NULL UNIQUE COMMENT '涓氬姟宸ュ崟鍙凤紝渚嬪 INC202604170001',
    title VARCHAR(200) NOT NULL COMMENT '宸ュ崟鏍囬',
    description TEXT NOT NULL COMMENT '闂鎻忚堪',
    category VARCHAR(64) NOT NULL COMMENT '宸ュ崟鍒嗙被锛欰CCOUNT/SYSTEM/ENVIRONMENT/OTHER',
    priority VARCHAR(32) NOT NULL COMMENT '浼樺厛绾э細LOW/MEDIUM/HIGH/URGENT',
    status VARCHAR(32) NOT NULL COMMENT '鐘舵€侊細PENDING_ASSIGN/PROCESSING/RESOLVED/CLOSED',
    creator_id BIGINT NOT NULL COMMENT '鎻愬崟浜虹敤鎴?ID',
    assignee_id BIGINT DEFAULT NULL COMMENT '褰撳墠澶勭悊浜虹敤鎴?ID锛屽緟鍒嗛厤鏃跺彲涓虹┖',
    group_id BIGINT DEFAULT NULL COMMENT '褰撳墠缁戝畾鐨勫伐鍗曠粍 ID',
    queue_id BIGINT DEFAULT NULL COMMENT '褰撳墠缁戝畾鐨勫伐鍗曢槦鍒?ID',
    solution_summary TEXT COMMENT '瑙ｅ喅鏂规鎽樿锛岄€氬父鍦ㄨВ鍐虫垨鍏抽棴闃舵濉啓',
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '鍒涘缓鏉ユ簮锛歁ANUAL-鎵嬪伐鍒涘缓锛孉GENT-Agent 鍒涘缓',
    idempotency_key VARCHAR(128) DEFAULT NULL COMMENT '鍒涘缓骞傜瓑閿紝鐢ㄤ簬闃叉閲嶅鎻愪氦',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    INDEX idx_creator_id (creator_id),
    INDEX idx_assignee_id (assignee_id),
    INDEX idx_ticket_group_id (group_id),
    INDEX idx_ticket_queue_id (queue_id),
    INDEX idx_status (status),
    INDEX idx_category (category),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 宸ュ崟璇勮琛ㄣ€?-- 淇濆瓨鐢ㄦ埛鍥炲銆佸鐞嗚繃绋嬭褰曘€佽В鍐虫柟妗堣ˉ鍏呯瓑鍗忎綔鍐呭銆?CREATE TABLE IF NOT EXISTS ticket_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '璇勮涓婚敭',
    ticket_id BIGINT NOT NULL COMMENT '鎵€灞炲伐鍗?ID',
    commenter_id BIGINT NOT NULL COMMENT '璇勮浜虹敤鎴?ID',
    comment_type VARCHAR(32) NOT NULL COMMENT '璇勮绫诲瀷锛歎SER_REPLY/PROCESS_LOG/SOLUTION',
    content TEXT NOT NULL COMMENT '璇勮姝ｆ枃',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    INDEX idx_ticket_id (ticket_id),
    INDEX idx_commenter_id (commenter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 宸ュ崟鎿嶄綔鏃ュ織琛ㄣ€?-- 淇濆瓨鍒涘缓銆佸垎閰嶃€佽浆娲俱€佺姸鎬佸彉鏇淬€佽瘎璁恒€佸叧闂瓑鍏抽敭鎿嶄綔杞ㄨ抗銆?-- 璇ヨ〃鏈嶅姟浜庡璁″拰杩芥函锛屼笉浣滀负褰撳墠浜嬪疄鏉ユ簮銆?CREATE TABLE IF NOT EXISTS ticket_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '鏃ュ織涓婚敭',
    ticket_id BIGINT NOT NULL COMMENT '鎵€灞炲伐鍗?ID',
    operator_id BIGINT NOT NULL COMMENT '鎿嶄綔浜虹敤鎴?ID',
    operation_type VARCHAR(64) NOT NULL COMMENT '鎿嶄綔绫诲瀷锛欳REATE/ASSIGN/TRANSFER/UPDATE_STATUS/COMMENT/CLOSE',
    operation_desc VARCHAR(500) NOT NULL COMMENT '鎿嶄綔璇存槑',
    before_value TEXT COMMENT '鍙樻洿鍓嶅唴瀹癸紝鍙瓨 JSON 鎴栨枃鏈?,
    after_value TEXT COMMENT '鍙樻洿鍚庡唴瀹癸紝鍙瓨 JSON 鎴栨枃鏈?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    INDEX idx_ticket_id (ticket_id),
    INDEX idx_operator_id (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 宸ュ崟闄勪欢琛ㄣ€?-- MVP 闃舵鍙繚瀛樻枃浠?URL锛屼笉鍦ㄦ暟鎹簱涓繚瀛樻枃浠朵簩杩涘埗鍐呭銆?CREATE TABLE IF NOT EXISTS ticket_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '闄勪欢涓婚敭',
    ticket_id BIGINT NOT NULL COMMENT '鎵€灞炲伐鍗?ID',
    file_name VARCHAR(255) NOT NULL COMMENT '鍘熷鏂囦欢鍚?,
    file_url VARCHAR(500) NOT NULL COMMENT '鏂囦欢璁块棶鍦板潃鎴栧璞″瓨鍌ㄥ湴鍧€',
    file_type VARCHAR(64) COMMENT '鏂囦欢绫诲瀷鎴栨墿灞曞悕',
    file_size BIGINT COMMENT '鏂囦欢澶у皬锛屽崟浣嶅瓧鑺?,
    uploader_id BIGINT NOT NULL COMMENT '涓婁紶浜虹敤鎴?ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    INDEX idx_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 宸ュ崟鐭ヨ瘑琛ㄣ€?-- 淇濆瓨宸插叧闂伐鍗曟矇娣€鍑虹殑鐭ヨ瘑鏂囨湰锛屾槸鍚庣画 RAG 鍒囩墖鍜屾绱㈢殑鏉ユ簮銆?-- 璇ヨ〃灞炰簬鐭ヨ瘑鏁版嵁锛屼笉鍙備笌宸ュ崟涓讳簨鍔＄姸鎬佸垽鏂€?CREATE TABLE IF NOT EXISTS ticket_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '鐭ヨ瘑涓婚敭',
    ticket_id BIGINT NOT NULL COMMENT '鏉ユ簮宸ュ崟 ID',
    content TEXT NOT NULL COMMENT '鐢ㄤ簬鍒囩墖鍜屽悜閲忓寲鐨勭煡璇嗘鏂?,
    content_summary VARCHAR(1000) COMMENT '鐭ヨ瘑鎽樿锛屼究浜庡垪琛ㄥ睍绀哄拰鍙洖缁撴灉灞曠ず',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '鐭ヨ瘑鐘舵€侊細ACTIVE-鍙敤',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY uk_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 宸ュ崟鐭ヨ瘑鍒囩墖琛ㄣ€?-- 淇濆瓨鐭ヨ瘑鏂囨湰鍒囩墖鍜岀涓€鐗堝悜閲?JSON銆傚悗缁帴鍏?pgvector 鏃跺彲杩佺Щ鍒颁笓鐢ㄥ悜閲忓瓧娈点€?CREATE TABLE IF NOT EXISTS ticket_knowledge_embedding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '鐭ヨ瘑鍒囩墖涓婚敭',
    knowledge_id BIGINT NOT NULL COMMENT '鎵€灞炵煡璇?ID',
    chunk_index INT NOT NULL COMMENT '鍒囩墖搴忓彿锛屼粠 0 鎴?1 寮€濮嬬敱 RAG 妯″潡绾﹀畾',
    chunk_text TEXT NOT NULL COMMENT '鍒囩墖鏂囨湰鍐呭',
    embedding_vector TEXT COMMENT '鍚戦噺 JSON 鏂囨湰锛岀涓€鐗堢敤浜庢墦閫氱煡璇嗗悜閲忓寲鍏ュ簱閾捐矾',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    INDEX idx_knowledge_id (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: ticket group.
-- 宸ュ崟缁勬槸闃熷垪銆丼LA 鍜岃嚜鍔ㄥ垎娲剧殑鍩虹閰嶇疆锛涘綋鍓嶉樁娈靛彧鍋氶厤缃鐞嗭紝涓嶆敼鍙樺伐鍗曚富娴佺▼銆?CREATE TABLE IF NOT EXISTS ticket_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '宸ュ崟缁勪富閿?,
    group_name VARCHAR(128) NOT NULL COMMENT '宸ュ崟缁勫悕绉?,
    group_code VARCHAR(64) NOT NULL COMMENT '宸ュ崟缁勭紪鐮?,
    owner_user_id BIGINT DEFAULT NULL COMMENT '缁勮礋璐ｄ汉鐢ㄦ埛 ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '鏄惁鍚敤锛?-鍚敤锛?-鍋滅敤',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY uk_ticket_group_code (group_code),
    INDEX idx_ticket_group_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: ticket queue.
-- 宸ュ崟闃熷垪闅跺睘浜庡伐鍗曠粍锛屽悗缁敤浜庨槦鍒楄鍥俱€丼LA 鍖归厤鍜岃嚜鍔ㄥ垎娲俱€?CREATE TABLE IF NOT EXISTS ticket_queue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '闃熷垪涓婚敭',
    queue_name VARCHAR(128) NOT NULL COMMENT '闃熷垪鍚嶇О',
    queue_code VARCHAR(64) NOT NULL COMMENT '闃熷垪缂栫爜',
    group_id BIGINT NOT NULL COMMENT '鎵€灞炲伐鍗曠粍 ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '鏄惁鍚敤锛?-鍚敤锛?-鍋滅敤',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY uk_ticket_queue_code (queue_code),
    INDEX idx_ticket_queue_group_id (group_id),
    INDEX idx_ticket_queue_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: SLA policy.
-- SLA 绛栫暐鎸夊伐鍗曞垎绫诲拰浼樺厛绾у尮閰嶏紱category/priority 涓虹┖琛ㄧず閫氶厤銆?CREATE TABLE IF NOT EXISTS ticket_sla_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'SLA 绛栫暐涓婚敭',
    policy_name VARCHAR(128) NOT NULL COMMENT 'SLA 绛栫暐鍚嶇О',
    category VARCHAR(64) DEFAULT NULL COMMENT '閫傜敤宸ュ崟鍒嗙被锛岀┖鍊艰〃绀洪€氶厤',
    priority VARCHAR(64) DEFAULT NULL COMMENT '閫傜敤宸ュ崟浼樺厛绾э紝绌哄€艰〃绀洪€氶厤',
    first_response_minutes INT NOT NULL COMMENT '棣栨鍝嶅簲鏃堕檺锛屽崟浣嶅垎閽?,
    resolve_minutes INT NOT NULL COMMENT '瑙ｅ喅鏃堕檺锛屽崟浣嶅垎閽?,
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '鏄惁鍚敤锛?-鍚敤锛?-鍋滅敤',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    INDEX idx_ticket_sla_policy_match (category, priority, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: ticket SLA instance.
-- SLA 瀹炰緥璁板綍鏌愬紶宸ュ崟鍛戒腑鐨勭瓥鐣ュ拰鎴鏃堕棿锛涘綋鍓嶉樁娈典笉鍋氬畾鏃惰繚绾︽壂鎻忋€?CREATE TABLE IF NOT EXISTS ticket_sla_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'SLA 瀹炰緥涓婚敭',
    ticket_id BIGINT NOT NULL COMMENT '宸ュ崟 ID',
    policy_id BIGINT NOT NULL COMMENT 'SLA 绛栫暐 ID',
    first_response_deadline DATETIME NOT NULL COMMENT '棣栨鍝嶅簲鎴鏃堕棿',
    resolve_deadline DATETIME NOT NULL COMMENT '瑙ｅ喅鎴鏃堕棿',
    breached TINYINT NOT NULL DEFAULT 0 COMMENT '鏄惁宸茶繚绾︼細1-宸茶繚绾︼紝0-鏈繚绾?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY uk_ticket_sla_ticket_id (ticket_id),
    INDEX idx_ticket_sla_policy_id (policy_id),
    INDEX idx_ticket_sla_deadline (resolve_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: assignment rule.
-- 鑷姩鍒嗘淳瑙勫垯褰撳墠鍙敤浜?preview 鎺ㄨ崘锛屼笉鐩存帴鏇存柊宸ュ崟澶勭悊浜烘垨鐘舵€併€?CREATE TABLE IF NOT EXISTS ticket_assignment_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '鑷姩鍒嗘淳瑙勫垯涓婚敭',
    rule_name VARCHAR(128) NOT NULL COMMENT '瑙勫垯鍚嶇О',
    category VARCHAR(64) DEFAULT NULL COMMENT '閫傜敤宸ュ崟鍒嗙被锛岀┖鍊艰〃绀洪€氶厤',
    priority VARCHAR(64) DEFAULT NULL COMMENT '閫傜敤宸ュ崟浼樺厛绾э紝绌哄€艰〃绀洪€氶厤',
    target_group_id BIGINT DEFAULT NULL COMMENT '鐩爣宸ュ崟缁?ID',
    target_queue_id BIGINT DEFAULT NULL COMMENT '鐩爣闃熷垪 ID',
    target_user_id BIGINT DEFAULT NULL COMMENT '鐩爣澶勭悊浜?ID',
    weight INT NOT NULL DEFAULT 0 COMMENT '瑙勫垯鏉冮噸锛岃秺澶ц秺浼樺厛',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '鏄惁鍚敤锛?-鍚敤锛?-鍋滅敤',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    INDEX idx_ticket_assignment_rule_match (category, priority, enabled, weight),
    INDEX idx_ticket_assignment_rule_target_group (target_group_id),
    INDEX idx_ticket_assignment_rule_target_queue (target_queue_id),
    INDEX idx_ticket_assignment_rule_target_user (target_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: ticket queue member.
-- 队列成员用于真实自动分派和后续认领能力；同一用户可加入多个队列。
CREATE TABLE IF NOT EXISTS ticket_queue_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '队列成员主键',
    queue_id BIGINT NOT NULL COMMENT '所属队列 ID',
    user_id BIGINT NOT NULL COMMENT '成员用户 ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
    last_assigned_at DATETIME DEFAULT NULL COMMENT '最近一次被自动分派时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_queue_member (queue_id, user_id),
    INDEX idx_ticket_queue_member_queue_id (queue_id),
    INDEX idx_ticket_queue_member_user_id (user_id),
    INDEX idx_ticket_queue_member_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;