-- 娴间椒绗熼弲楦垮厴瀹搞儱宕熼崡蹇撴倱楠炲啿褰撮弫鐗堝祦鎼存挸鍨垫慨瀣閼存碍婀伴妴?-- 瑜版挸澧犻懘姘拱閸欘亣绀嬬拹锝呭灡瀵?MySQL 娑撹绗熼崝鈥崇氨閸?MVP 闂冭埖顔岄弽绋跨妇鐞涖劊鈧?-- RAG 閸氭垿鍣虹€涙顔岄崥搴ｇ敾閹恒儱鍙?pgvector 閺冭泛宕熼悪顒傛樊閹躲倧绱濇稉宥嗘杹閸?MySQL 娑撹绨辨稉顓溾偓?
-- 閸掓稑缂撴稉姘閺佺増宓佹惔鎿勭礉缂佺喍绔存担璺ㄦ暏 utf8mb4 娴犮儲鏁幐浣疯厬閺傚洢鈧浇瀚抽弬鍥ф嫲缁楋箑褰块崘鍛啇閵?CREATE DATABASE IF NOT EXISTS smart_ticket_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_ticket_platform;

-- 缁崵绮洪悽銊﹀煕鐞涖劊鈧?-- 娣囨繂鐡ㄩ惂璇茬秿鐠愶箑褰块妴浣哥槕閻焦鎲崇憰浣碘偓浣哥潔缁€鍝勵潣閸氬秲鈧線鍋栫粻鍗炴嫲鐠愶箑褰块崥顖氫粻閻樿埖鈧降鈧?-- 鏉╂瑩鍣烽崣顏冪箽鐎涙鏁ら幋宄扮唨绾偓闊偂鍞ゆ穱鈩冧紖閿涘奔绗夋穱婵嗙摠閻劍鍩涢崷銊︾厙瀵姴浼愰崡鏇氳厬閻ㄥ嫪绗熼崝鈥茬秴缂冾喓鈧?CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '閻劍鍩涙稉濠氭暛',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '閻ц缍嶉悽銊﹀煕閸氬稄绱濋崗銊ョ湰閸烆垯绔?,
    password_hash VARCHAR(255) NOT NULL COMMENT '鐎靛棛鐖滈崫鍫濈瑖閸婄》绱濇稉宥勭箽鐎涙ɑ妲戦弬鍥х槕閻?,
    real_name VARCHAR(64) NOT NULL COMMENT '閻劍鍩涢惇鐔风杽婵挸鎮曢幋鏍х潔缁€鍝勬倳',
    email VARCHAR(128) COMMENT '闁喚顔堥崷鏉挎絻',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '鐠愶箑褰块悩鑸碘偓渚婄窗1-閸氼垳鏁ら敍?-缁備胶鏁?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '閺囧瓨鏌婇弮鍫曟？'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 缁崵绮虹憴鎺曞鐞涖劊鈧?-- 缁楊兛绔撮悧鍫濇祼鐎规矮濞囬悽?USER / STAFF / ADMIN 娑撳琚憴鎺曞閵?-- 鐟欐帟澹婇崣顏囥€冪粈铏归兇缂佺喕鍏橀崝娑崇礉娑撳秷銆冪粈鐑樺絹閸楁洑姹夐妴浣割槱閻炲棔姹夋潻娆戣瀹搞儱宕熼崘鍛瑹閸斺€冲彠缁眹鈧?CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '鐟欐帟澹婃稉濠氭暛',
    role_code VARCHAR(64) NOT NULL UNIQUE COMMENT '鐟欐帟澹婄紓鏍垳閿涙瓗SER/STAFF/ADMIN',
    role_name VARCHAR(64) NOT NULL COMMENT '鐟欐帟澹婇崥宥囆?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 閻劍鍩涚憴鎺曞閸忓疇浠堢悰銊ｂ偓?-- 娑撯偓娑擃亞鏁ら幋宄板讲娴犮儲瀚㈤張澶婎樋娑擃亣顫楅懝璇х礉娓氬顩ф径鍕倞娴滃搫鎲抽崥灞炬閹枫儲婀?USER 閸?STAFF閵?CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '閸忓疇浠堟稉濠氭暛',
    user_id BIGINT NOT NULL COMMENT '閻劍鍩?ID',
    role_id BIGINT NOT NULL COMMENT '鐟欐帟澹?ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 瀹搞儱宕熸稉鏄忋€冮妴?-- 娣囨繂鐡ㄥ銉ュ礋瑜版挸澧犳禍瀣杽閻樿埖鈧緤绱拌ぐ鎾冲閻樿埖鈧降鈧礁缍嬮崜宥堢鐠愶絼姹夐妴浣风喘閸忓牏楠囬妴浣稿瀻缁崵鐡戦妴?-- 鐠囧嫯顔戦妴浣规惙娴ｆ粍妫╄箛妞尖偓渚€妾禒鍓佺搼鏉╁洨鈻奸弫鐗堝祦閹峰棗鍨庨崚鎵缁斿銆冮妴?CREATE TABLE IF NOT EXISTS ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '瀹搞儱宕熸稉濠氭暛',
    ticket_no VARCHAR(32) NOT NULL UNIQUE COMMENT '娑撴艾濮熷銉ュ礋閸欏嚖绱濇笟瀣洤 INC202604170001',
    title VARCHAR(200) NOT NULL COMMENT '工单标题',
    description TEXT NOT NULL COMMENT '问题描述',
    ticket_type VARCHAR(64) NOT NULL COMMENT '工单类型：INCIDENT/ACCESS_REQUEST/ENVIRONMENT_REQUEST/CONSULTATION/CHANGE_REQUEST',
    category VARCHAR(64) NOT NULL COMMENT '工单分类：ACCOUNT/SYSTEM/ENVIRONMENT/OTHER',
    priority VARCHAR(32) NOT NULL COMMENT '娴兼ê鍘涚痪褝绱癓OW/MEDIUM/HIGH/URGENT',
    status VARCHAR(32) NOT NULL COMMENT '閻樿埖鈧緤绱癙ENDING_ASSIGN/PROCESSING/RESOLVED/CLOSED',
    creator_id BIGINT NOT NULL COMMENT '閹绘劕宕熸禍铏规暏閹?ID',
    assignee_id BIGINT DEFAULT NULL COMMENT '瑜版挸澧犳径鍕倞娴滆櫣鏁ら幋?ID閿涘苯绶熼崚鍡涘帳閺冭泛褰叉稉铏光敄',
    group_id BIGINT DEFAULT NULL COMMENT '瑜版挸澧犵紒鎴濈暰閻ㄥ嫬浼愰崡鏇犵矋 ID',
    queue_id BIGINT DEFAULT NULL COMMENT '瑜版挸澧犵紒鎴濈暰閻ㄥ嫬浼愰崡鏇㈡Е閸?ID',
    solution_summary TEXT COMMENT '鐟欙絽鍠呴弬瑙勵攳閹芥顩﹂敍宀勨偓姘埗閸︺劏袙閸愯櫕鍨ㄩ崗鎶芥４闂冭埖顔屾繅顐㈠晸',
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '閸掓稑缂撻弶銉︾爱閿涙瓉ANUAL-閹靛浼愰崚娑樼紦閿涘瓑GENT-Agent 閸掓稑缂?,
    idempotency_key VARCHAR(128) DEFAULT NULL COMMENT '閸掓稑缂撻獮鍌滅搼闁款噯绱濋悽銊ょ艾闂冨弶顒涢柌宥咁槻閹绘劒姘?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '閺囧瓨鏌婇弮鍫曟？',
    INDEX idx_creator_id (creator_id),
    INDEX idx_assignee_id (assignee_id),
    INDEX idx_ticket_group_id (group_id),
    INDEX idx_ticket_queue_id (queue_id),
    INDEX idx_status (status),
    INDEX idx_ticket_type (ticket_type),
    INDEX idx_category (category),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 瀹搞儱宕熺拠鍕啈鐞涖劊鈧?-- 娣囨繂鐡ㄩ悽銊﹀煕閸ョ偛顦查妴浣割槱閻炲棜绻冪粙瀣唶瑜版洏鈧浇袙閸愯櫕鏌熷鍫Ｋ夐崗鍛搼閸楀繋缍旈崘鍛啇閵?CREATE TABLE IF NOT EXISTS ticket_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '鐠囧嫯顔戞稉濠氭暛',
    ticket_id BIGINT NOT NULL COMMENT '閹碘偓鐏炵偛浼愰崡?ID',
    commenter_id BIGINT NOT NULL COMMENT '鐠囧嫯顔戞禍铏规暏閹?ID',
    comment_type VARCHAR(32) NOT NULL COMMENT '鐠囧嫯顔戠猾璇茬€烽敍姝嶴ER_REPLY/PROCESS_LOG/SOLUTION',
    content TEXT NOT NULL COMMENT '鐠囧嫯顔戝锝嗘瀮',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    INDEX idx_ticket_id (ticket_id),
    INDEX idx_commenter_id (commenter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 瀹搞儱宕熼幙宥勭稊閺冦儱绻旂悰銊ｂ偓?-- 娣囨繂鐡ㄩ崚娑樼紦閵嗕礁鍨庨柊宥冣偓浣芥祮濞蹭勘鈧胶濮搁幀浣稿綁閺囨番鈧浇鐦庣拋鎭掆偓浣稿彠闂傤厾鐡戦崗鎶芥暛閹垮秳缍旀潪銊ㄦ姉閵?-- 鐠囥儴銆冮張宥呭娴滃骸顓哥拋鈥虫嫲鏉╄姤鍑介敍灞肩瑝娴ｆ粈璐熻ぐ鎾冲娴滃鐤勯弶銉︾爱閵?CREATE TABLE IF NOT EXISTS ticket_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '閺冦儱绻旀稉濠氭暛',
    ticket_id BIGINT NOT NULL COMMENT '閹碘偓鐏炵偛浼愰崡?ID',
    operator_id BIGINT NOT NULL COMMENT '閹垮秳缍旀禍铏规暏閹?ID',
    operation_type VARCHAR(64) NOT NULL COMMENT '閹垮秳缍旂猾璇茬€烽敍娆砇EATE/ASSIGN/TRANSFER/UPDATE_STATUS/COMMENT/CLOSE',
    operation_desc VARCHAR(500) NOT NULL COMMENT '閹垮秳缍旂拠瀛樻',
    before_value TEXT COMMENT '閸欐ɑ娲块崜宥呭敶鐎圭櫢绱濋崣顖氱摠 JSON 閹存牗鏋冮張?,
    after_value TEXT COMMENT '閸欐ɑ娲块崥搴″敶鐎圭櫢绱濋崣顖氱摠 JSON 閹存牗鏋冮張?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    INDEX idx_ticket_id (ticket_id),
    INDEX idx_operator_id (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 瀹搞儱宕熼梽鍕鐞涖劊鈧?-- MVP 闂冭埖顔岄崣顏冪箽鐎涙ɑ鏋冩禒?URL閿涘奔绗夐崷銊︽殶閹诡喖绨辨稉顓濈箽鐎涙ɑ鏋冩禒鏈电癌鏉╂稑鍩楅崘鍛啇閵?CREATE TABLE IF NOT EXISTS ticket_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '闂勫嫪娆㈡稉濠氭暛',
    ticket_id BIGINT NOT NULL COMMENT '閹碘偓鐏炵偛浼愰崡?ID',
    file_name VARCHAR(255) NOT NULL COMMENT '閸樼喎顫愰弬鍥︽閸?,
    file_url VARCHAR(500) NOT NULL COMMENT '閺傚洣娆㈢拋鍧楁６閸︽澘娼冮幋鏍ь嚠鐠炩€崇摠閸屻劌婀撮崸鈧?,
    file_type VARCHAR(64) COMMENT '閺傚洣娆㈢猾璇茬€烽幋鏍ㄥ⒖鐏炴洖鎮?,
    file_size BIGINT COMMENT '閺傚洣娆㈡径褍鐨敍灞藉礋娴ｅ秴鐡ч懞?,
    uploader_id BIGINT NOT NULL COMMENT '娑撳﹣绱舵禍铏规暏閹?ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    INDEX idx_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 瀹搞儱宕熼惌銉ㄧ槕鐞涖劊鈧?-- 娣囨繂鐡ㄥ鎻掑彠闂傤厼浼愰崡鏇熺焽濞ｂ偓閸戣櫣娈戦惌銉ㄧ槕閺傚洦婀伴敍灞炬Ц閸氬海鐢?RAG 閸掑洨澧栭崪灞绢梾缁便垻娈戦弶銉︾爱閵?-- 鐠囥儴銆冪仦鐐扮艾閻儴鐦戦弫鐗堝祦閿涘奔绗夐崣鍌欑瑢瀹搞儱宕熸稉璁崇皑閸旓紕濮搁幀浣稿灲閺傤厹鈧?CREATE TABLE IF NOT EXISTS ticket_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '閻儴鐦戞稉濠氭暛',
    ticket_id BIGINT NOT NULL COMMENT '閺夈儲绨銉ュ礋 ID',
    content TEXT NOT NULL COMMENT '閻劋绨崚鍥╁閸滃苯鎮滈柌蹇撳閻ㄥ嫮鐓＄拠鍡橆劀閺?,
    content_summary VARCHAR(1000) COMMENT '閻儴鐦戦幗妯款洣閿涘奔绌舵禍搴″灙鐞涖劌鐫嶇粈鍝勬嫲閸欘剙娲栫紒鎾寸亯鐏炴洜銇?,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '閻儴鐦戦悩鑸碘偓渚婄窗ACTIVE-閸欘垳鏁?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '閺囧瓨鏌婇弮鍫曟？',
    UNIQUE KEY uk_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 瀹搞儱宕熼惌銉ㄧ槕閸掑洨澧栫悰銊ｂ偓?-- 娣囨繂鐡ㄩ惌銉ㄧ槕閺傚洦婀伴崚鍥╁閸滃瞼顑囨稉鈧悧鍫濇倻闁?JSON閵嗗倸鎮楃紒顓熷复閸?pgvector 閺冭泛褰叉潻浣盒╅崚棰佺瑩閻劌鎮滈柌蹇撶摟濞堢偣鈧?CREATE TABLE IF NOT EXISTS ticket_knowledge_embedding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '閻儴鐦戦崚鍥╁娑撳鏁?,
    knowledge_id BIGINT NOT NULL COMMENT '閹碘偓鐏炵偟鐓＄拠?ID',
    chunk_index INT NOT NULL COMMENT '閸掑洨澧栨惔蹇撳娇閿涘奔绮?0 閹?1 瀵偓婵鏁?RAG 濡€虫健缁撅箑鐣?,
    chunk_text TEXT NOT NULL COMMENT '閸掑洨澧栭弬鍥ㄦ拱閸愬懎顔?,
    embedding_vector TEXT COMMENT '閸氭垿鍣?JSON 閺傚洦婀伴敍宀€顑囨稉鈧悧鍫㈡暏娴滃孩澧﹂柅姘辩叀鐠囧棗鎮滈柌蹇撳閸忋儱绨遍柧鎹愮熅',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    INDEX idx_knowledge_id (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: ticket group.
-- 瀹搞儱宕熺紒鍕Ц闂冪喎鍨妴涓糒A 閸滃矁鍤滈崝銊ュ瀻濞插墽娈戦崺铏诡攨闁板秶鐤嗛敍娑樼秼閸撳秹妯佸▓闈涘涧閸嬫岸鍘ょ純顔绢吀閻炲棴绱濇稉宥嗘暭閸欐ê浼愰崡鏇氬瘜濞翠胶鈻奸妴?CREATE TABLE IF NOT EXISTS ticket_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '瀹搞儱宕熺紒鍕瘜闁?,
    group_name VARCHAR(128) NOT NULL COMMENT '瀹搞儱宕熺紒鍕倳缁?,
    group_code VARCHAR(64) NOT NULL COMMENT '瀹搞儱宕熺紒鍕椽閻?,
    owner_user_id BIGINT DEFAULT NULL COMMENT '缂佸嫯绀嬬拹锝勬眽閻劍鍩?ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '閺勵垰鎯侀崥顖滄暏閿?-閸氼垳鏁ら敍?-閸嬫粎鏁?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '閺囧瓨鏌婇弮鍫曟？',
    UNIQUE KEY uk_ticket_group_code (group_code),
    INDEX idx_ticket_group_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: ticket queue.
-- 瀹搞儱宕熼梼鐔峰灙闂呰泛鐫樻禍搴′紣閸楁洜绮嶉敍灞芥倵缂侇厾鏁ゆ禍搴ㄦЕ閸掓顫嬮崶淇扁偓涓糒A 閸栧綊鍘ら崪宀冨殰閸斻劌鍨庡ú淇扁偓?CREATE TABLE IF NOT EXISTS ticket_queue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '闂冪喎鍨稉濠氭暛',
    queue_name VARCHAR(128) NOT NULL COMMENT '闂冪喎鍨崥宥囆?,
    queue_code VARCHAR(64) NOT NULL COMMENT '闂冪喎鍨紓鏍垳',
    group_id BIGINT NOT NULL COMMENT '閹碘偓鐏炵偛浼愰崡鏇犵矋 ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '閺勵垰鎯侀崥顖滄暏閿?-閸氼垳鏁ら敍?-閸嬫粎鏁?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '閺囧瓨鏌婇弮鍫曟？',
    UNIQUE KEY uk_ticket_queue_code (queue_code),
    INDEX idx_ticket_queue_group_id (group_id),
    INDEX idx_ticket_queue_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: SLA policy.
-- SLA 缁涙牜鏆愰幐澶婁紣閸楁洖鍨庣猾璇叉嫲娴兼ê鍘涚痪褍灏柊宥忕幢category/priority 娑撹櫣鈹栫悰銊с仛闁岸鍘ら妴?CREATE TABLE IF NOT EXISTS ticket_sla_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'SLA 缁涙牜鏆愭稉濠氭暛',
    policy_name VARCHAR(128) NOT NULL COMMENT 'SLA 缁涙牜鏆愰崥宥囆?,
    category VARCHAR(64) DEFAULT NULL COMMENT '闁倻鏁ゅ銉ュ礋閸掑棛琚敍宀€鈹栭崐鑹般€冪粈娲偓姘跺帳',
    priority VARCHAR(64) DEFAULT NULL COMMENT '闁倻鏁ゅ銉ュ礋娴兼ê鍘涚痪褝绱濈粚鍝勨偓鑹般€冪粈娲偓姘跺帳',
    first_response_minutes INT NOT NULL COMMENT '妫ｆ牗顐奸崫宥呯安閺冨爼妾洪敍灞藉礋娴ｅ秴鍨庨柦?,
    resolve_minutes INT NOT NULL COMMENT '鐟欙絽鍠呴弮鍫曟閿涘苯宕熸担宥呭瀻闁?,
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '閺勵垰鎯侀崥顖滄暏閿?-閸氼垳鏁ら敍?-閸嬫粎鏁?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '閺囧瓨鏌婇弮鍫曟？',
    INDEX idx_ticket_sla_policy_match (category, priority, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: ticket SLA instance.
-- SLA 鐎圭偘绶ョ拋鏉跨秿閺屾劕绱跺銉ュ礋閸涙垝鑵戦惃鍕摜閻ｃ儱鎷伴幋顏咁剾閺冨爼妫块敍娑樼秼閸撳秹妯佸▓鍏哥瑝閸嬫艾鐣鹃弮鎯扮箽缁撅附澹傞幓蹇嬧偓?CREATE TABLE IF NOT EXISTS ticket_sla_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'SLA 鐎圭偘绶ユ稉濠氭暛',
    ticket_id BIGINT NOT NULL COMMENT '瀹搞儱宕?ID',
    policy_id BIGINT NOT NULL COMMENT 'SLA 缁涙牜鏆?ID',
    first_response_deadline DATETIME NOT NULL COMMENT '妫ｆ牗顐奸崫宥呯安閹搭亝顒涢弮鍫曟？',
    resolve_deadline DATETIME NOT NULL COMMENT '鐟欙絽鍠呴幋顏咁剾閺冨爼妫?,
    breached TINYINT NOT NULL DEFAULT 0 COMMENT '閺勵垰鎯佸鑼剁箽缁撅讣绱?-瀹歌尪绻氱痪锔肩礉0-閺堫亣绻氱痪?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '閺囧瓨鏌婇弮鍫曟？',
    UNIQUE KEY uk_ticket_sla_ticket_id (ticket_id),
    INDEX idx_ticket_sla_policy_id (policy_id),
    INDEX idx_ticket_sla_deadline (resolve_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: assignment rule.
-- 閼奉亜濮╅崚鍡樻烦鐟欏嫬鍨ぐ鎾冲閸欘亞鏁ゆ禍?preview 閹恒劏宕橀敍灞肩瑝閻╁瓨甯撮弴瀛樻煀瀹搞儱宕熸径鍕倞娴滅儤鍨ㄩ悩鑸碘偓浣碘偓?CREATE TABLE IF NOT EXISTS ticket_assignment_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '閼奉亜濮╅崚鍡樻烦鐟欏嫬鍨稉濠氭暛',
    rule_name VARCHAR(128) NOT NULL COMMENT '鐟欏嫬鍨崥宥囆?,
    category VARCHAR(64) DEFAULT NULL COMMENT '闁倻鏁ゅ銉ュ礋閸掑棛琚敍宀€鈹栭崐鑹般€冪粈娲偓姘跺帳',
    priority VARCHAR(64) DEFAULT NULL COMMENT '闁倻鏁ゅ銉ュ礋娴兼ê鍘涚痪褝绱濈粚鍝勨偓鑹般€冪粈娲偓姘跺帳',
    target_group_id BIGINT DEFAULT NULL COMMENT '閻╊喗鐖ｅ銉ュ礋缂?ID',
    target_queue_id BIGINT DEFAULT NULL COMMENT '閻╊喗鐖ｉ梼鐔峰灙 ID',
    target_user_id BIGINT DEFAULT NULL COMMENT '閻╊喗鐖ｆ径鍕倞娴?ID',
    weight INT NOT NULL DEFAULT 0 COMMENT '鐟欏嫬鍨弶鍐櫢閿涘矁绉烘径褑绉烘导妯哄帥',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '閺勵垰鎯侀崥顖滄暏閿?-閸氼垳鏁ら敍?-閸嬫粎鏁?,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '閸掓稑缂撻弮鍫曟？',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '閺囧瓨鏌婇弮鍫曟？',
    INDEX idx_ticket_assignment_rule_match (category, priority, enabled, weight),
    INDEX idx_ticket_assignment_rule_target_group (target_group_id),
    INDEX idx_ticket_assignment_rule_target_queue (target_queue_id),
    INDEX idx_ticket_assignment_rule_target_user (target_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- P1: ticket queue member.
-- 闃熷垪鎴愬憳鐢ㄤ簬鐪熷疄鑷姩鍒嗘淳鍜屽悗缁棰嗚兘鍔涳紱鍚屼竴鐢ㄦ埛鍙姞鍏ュ涓槦鍒椼€?CREATE TABLE IF NOT EXISTS ticket_queue_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '闃熷垪鎴愬憳涓婚敭',
    queue_id BIGINT NOT NULL COMMENT '鎵€灞為槦鍒?ID',
    user_id BIGINT NOT NULL COMMENT '鎴愬憳鐢ㄦ埛 ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '鏄惁鍚敤锛?-鍚敤锛?-鍋滅敤',
    last_assigned_at DATETIME DEFAULT NULL COMMENT '鏈€杩戜竴娆¤鑷姩鍒嗘淳鏃堕棿',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY uk_ticket_queue_member (queue_id, user_id),
    INDEX idx_ticket_queue_member_queue_id (queue_id),
    INDEX idx_ticket_queue_member_user_id (user_id),
    INDEX idx_ticket_queue_member_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- C4: 最小审批流。当前仅用于权限申请工单。
CREATE TABLE IF NOT EXISTS ticket_approval (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审批主键',
    ticket_id BIGINT NOT NULL COMMENT '工单 ID',
    approval_status VARCHAR(32) NOT NULL COMMENT '审批状态：PENDING/APPROVED/REJECTED',
    approver_id BIGINT NOT NULL COMMENT '审批人用户 ID',
    requested_by BIGINT NOT NULL COMMENT '提交审批人用户 ID',
    submit_comment VARCHAR(500) DEFAULT NULL COMMENT '提交审批说明',
    decision_comment VARCHAR(500) DEFAULT NULL COMMENT '审批说明',
    submitted_at DATETIME NOT NULL COMMENT '提交审批时间',
    decided_at DATETIME DEFAULT NULL COMMENT '审批完成时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_approval_ticket_id (ticket_id),
    INDEX idx_ticket_approval_status (approval_status),
    INDEX idx_ticket_approval_approver_id (approver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================
-- 2026-04-21 C3/C4 �����ű�
-- ��ǰ��ȫ���ű���ִ�У��ɰ�������������
-- =========================

-- C3�������͹�����չ����
CREATE TABLE IF NOT EXISTS ticket_type_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '������������',
    ticket_id BIGINT NOT NULL COMMENT '���� ID',
    profile_schema VARCHAR(64) NOT NULL COMMENT '����ģ�ͱ��룬ͨ���� ticket_type һ��',
    profile_data JSON NOT NULL COMMENT '�������� JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '����ʱ��',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '����ʱ��',
    UNIQUE KEY uk_ticket_type_profile_ticket_id (ticket_id),
    INDEX idx_ticket_type_profile_schema (profile_schema)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- C4������ģ��
CREATE TABLE IF NOT EXISTS ticket_approval_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '����ģ������',
    template_name VARCHAR(128) NOT NULL COMMENT 'ģ������',
    ticket_type VARCHAR(64) NOT NULL COMMENT '���ù�������',
    description VARCHAR(500) DEFAULT NULL COMMENT 'ģ��˵��',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '�Ƿ����ã�1 ���ã�0 ͣ��',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '����ʱ��',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '����ʱ��',
    INDEX idx_ticket_approval_template_type (ticket_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_approval_template_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '����ģ�岽������',
    template_id BIGINT NOT NULL COMMENT '����ģ�� ID',
    step_order INT NOT NULL COMMENT '����˳�򣬴� 1 ��ʼ',
    step_name VARCHAR(128) NOT NULL COMMENT '��������',
    approver_id BIGINT NOT NULL COMMENT '�������û� ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '����ʱ��',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '����ʱ��',
    UNIQUE KEY uk_ticket_approval_template_step (template_id, step_order),
    INDEX idx_ticket_approval_template_step_template_id (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_approval_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '����ʵ����������',
    ticket_id BIGINT NOT NULL COMMENT '���� ID',
    approval_id BIGINT NOT NULL COMMENT '������¼ ID',
    step_order INT NOT NULL COMMENT '����˳�򣬴� 1 ��ʼ',
    step_name VARCHAR(128) NOT NULL COMMENT '��������',
    approver_id BIGINT NOT NULL COMMENT '�������û� ID',
    step_status VARCHAR(32) NOT NULL COMMENT '����״̬��WAITING/PENDING/APPROVED/REJECTED',
    decision_comment VARCHAR(500) DEFAULT NULL COMMENT '�������',
    decided_at DATETIME DEFAULT NULL COMMENT '�������ʱ��',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '����ʱ��',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '����ʱ��',
    UNIQUE KEY uk_ticket_approval_step (approval_id, step_order),
    INDEX idx_ticket_approval_step_ticket_id (ticket_id),
    INDEX idx_ticket_approval_step_status (step_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- �����ʷ���� ticket_approval ���ǵ��������ṹ���벹�����ֶ�
-- MySQL 8 �ɰ���ִ�У����ֶ��Ѵ���������
-- ALTER TABLE ticket_approval ADD COLUMN template_id BIGINT DEFAULT NULL COMMENT '����ģ�� ID' AFTER ticket_id;
-- ALTER TABLE ticket_approval ADD COLUMN current_step_order INT DEFAULT NULL COMMENT '��ǰ��������˳��' AFTER template_id;

