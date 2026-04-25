package com.smartticket.biz.service.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 工单创建字段自动补全服务。
 *
 * <p>普通用户创建工单时只需提供 title + description，系统自动补全
 * type、category、priority、typeProfile 等结构化字段。</p>
 *
 * <p>补全策略优先级（高到低）：
 * <ol>
 *   <li>用户显式传入的字段（不覆盖）</li>
 *   <li>LLM 抽取结果（enabled 时，需校验通过且置信度达标）</li>
 *   <li>规则推断结果（关键词匹配）</li>
 *   <li>默认兜底值（"待确认"）</li>
 * </ol>
 * </p>
 *
 * <p>LLM enrichment 默认关闭（{@code smart-ticket.ticket.enrichment.llm-enabled=false}），
 * 避免本地没有 API Key 时影响启动。开启后调用失败自动降级到规则。</p>
 */
@Service
public class TicketCreateEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(TicketCreateEnrichmentService.class);

    private final TicketCreateEnrichmentProperties properties;
    private final TicketCreateLlmEnricher llmEnricher;

    /**
     * 构造工单创建字段补全服务。
     *
     * <p>LLM enricher 只在 llm-enabled=true 且 ChatModel 可用时创建。</p>
     */
    @Autowired
    public TicketCreateEnrichmentService(
            TicketCreateEnrichmentProperties properties,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (properties.isLlmEnabled() && chatModel != null) {
            ChatClient chatClient = ChatClient.builder(chatModel).build();
            this.llmEnricher = new TicketCreateLlmEnricher(chatClient, properties, objectMapper);
            log.info("LLM enrichment 已启用，超时={}ms，最低置信度={}", properties.getLlmTimeoutMs(), properties.getLlmMinConfidence());
        } else {
            this.llmEnricher = null;
            if (properties.isLlmEnabled()) {
                log.warn("LLM enrichment 已配置启用但 ChatModel 不可用，降级为规则 enrichment");
            }
        }
    }

    /**
     * 包级私有构造，用于测试注入 mock LLM enricher。
     */
    TicketCreateEnrichmentService(
            TicketCreateEnrichmentProperties properties,
            TicketCreateLlmEnricher llmEnricher
    ) {
        this.properties = properties;
        this.llmEnricher = llmEnricher;
    }

    /**
     * 补全工单创建命令中缺失的结构化字段。
     *
     * <p>合并策略：用户显式输入 > LLM 抽取结果 > 规则推断 > 默认兜底。</p>
     */
    public TicketCreateCommandDTO enrich(TicketCreateCommandDTO command) {
        // Step 1: 提取用户显式传入的字段
        boolean userHasType = command.getType() != null;
        boolean userHasCategory = command.getCategory() != null;
        boolean userHasPriority = command.getPriority() != null;
        boolean userHasTypeProfile = command.getTypeProfile() != null;
        Map<String, Object> userTypeProfile = command.getTypeProfile();

        // Step 2: 尝试 LLM 抽取
        TicketCreateLlmEnricher.LlmEnrichmentResult llmResult = tryLlmEnrich(command);

        // Step 3: 确定最终字段值
        TicketTypeEnum type = resolveType(command.getType(), llmResult, command);
        TicketCategoryEnum category = resolveCategory(command.getCategory(), llmResult, command, type);
        TicketPriorityEnum priority = resolvePriority(command.getPriority(), llmResult, command, type);

        // Step 4: 生成 typeProfile（逐字段合并）
        Map<String, Object> typeProfile = resolveTypeProfile(userTypeProfile, llmResult, type, command);

        // Step 5: 构建最终命令
        TicketCreateCommandDTO result = TicketCreateCommandDTO.builder()
                .title(command.getTitle())
                .description(command.getDescription())
                .type(type)
                .category(category)
                .priority(priority)
                .typeProfile(typeProfile)
                .idempotencyKey(command.getIdempotencyKey())
                .build();

        // 记录 enrichment 来源日志
        if (llmResult != null) {
            log.debug("ticket_enrichment_mode=LLM_WITH_RULE_FALLBACK, llmConfidence={}, type={}, category={}, priority={}",
                    llmResult.confidence(), type, category, priority);
        }
        return result;
    }

    // ========== 合并策略 ==========

    /**
     * 尝试 LLM enrichment，失败时返回 null。
     */
    private TicketCreateLlmEnricher.LlmEnrichmentResult tryLlmEnrich(TicketCreateCommandDTO command) {
        if (llmEnricher == null) {
            return null;
        }
        try {
            TicketCreateLlmEnricher.LlmEnrichmentResult result = llmEnricher.enrich(
                    command.getTitle(), command.getDescription()
            );
            if (result == null) {
                log.warn("ticket_enrichment_mode=LLM_FAILED_RULE_FALLBACK, fallback_reason=LLM_RETURNED_NULL");
                return null;
            }
            if (!result.isUsable(properties.getLlmMinConfidence())) {
                log.warn("ticket_enrichment_mode=LLM_FAILED_RULE_FALLBACK, fallback_reason=LOW_CONFIDENCE, confidence={}",
                        result.confidence());
                return null;
            }
            return result;
        } catch (Exception ex) {
            log.warn("ticket_enrichment_mode=LLM_FAILED_RULE_FALLBACK, fallback_reason=API_ERROR");
            return null;
        }
    }

    /**
     * 解析 type：用户显式 > LLM > 规则 > 默认。
     */
    private TicketTypeEnum resolveType(TicketTypeEnum userExplicit,
                                        TicketCreateLlmEnricher.LlmEnrichmentResult llmResult,
                                        TicketCreateCommandDTO command) {
        if (userExplicit != null) {
            return userExplicit;
        }
        if (llmResult != null && llmResult.type() != null) {
            return llmResult.type();
        }
        return inferType(command);
    }

    /**
     * 解析 category：用户显式 > LLM > 规则 > 默认。
     */
    private TicketCategoryEnum resolveCategory(TicketCategoryEnum userExplicit,
                                                TicketCreateLlmEnricher.LlmEnrichmentResult llmResult,
                                                TicketCreateCommandDTO command,
                                                TicketTypeEnum resolvedType) {
        if (userExplicit != null) {
            return userExplicit;
        }
        if (llmResult != null && llmResult.category() != null) {
            return llmResult.category();
        }
        return inferCategory(command, resolvedType);
    }

    /**
     * 解析 priority：用户显式 > LLM > 规则 > 默认。
     */
    private TicketPriorityEnum resolvePriority(TicketPriorityEnum userExplicit,
                                                TicketCreateLlmEnricher.LlmEnrichmentResult llmResult,
                                                TicketCreateCommandDTO command,
                                                TicketTypeEnum resolvedType) {
        if (userExplicit != null) {
            return userExplicit;
        }
        if (llmResult != null && llmResult.priority() != null) {
            return llmResult.priority();
        }
        return inferPriority(command, resolvedType);
    }

    /**
     * 解析 typeProfile：用户显式字段 > LLM 抽取字段 > 规则生成字段 > 默认兜底。
     *
     * <p>用户传了 typeProfile 时保留用户已有字段，仅用 LLM/规则补齐缺失字段。</p>
     */
    private Map<String, Object> resolveTypeProfile(Map<String, Object> userProfile,
                                                    TicketCreateLlmEnricher.LlmEnrichmentResult llmResult,
                                                    TicketTypeEnum resolvedType,
                                                    TicketCreateCommandDTO command) {
        // 先生成规则兜底的完整 typeProfile
        Map<String, Object> ruleProfile = generateTypeProfile(resolvedType, command);

        if (userProfile == null && llmResult == null) {
            return ruleProfile;
        }

        // 从规则兜底 profile 开始，逐层覆盖
        Map<String, Object> merged = new HashMap<>(ruleProfile);

        // 第一层覆盖：LLM 结果（优先级高于规则）
        if (llmResult != null && llmResult.typeProfile() != null) {
            merged.putAll(llmResult.typeProfile());
        }

        // 第二层覆盖：用户显式字段（最高优先级）
        if (userProfile != null) {
            // 只覆盖用户显式传入的 key，不删除用户没传的 key
            for (Map.Entry<String, Object> entry : userProfile.entrySet()) {
                if (entry.getValue() != null) {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return merged;
    }

    // ========== 规则推断（与之前保持一致） ==========

    /** 根据 title + description 推断工单类型。 */
    private TicketTypeEnum inferType(TicketCreateCommandDTO command) {
        String text = getSearchText(command);
        if (containsAny(text, "权限", "账号", "开通", "角色", "登录账号", "访问不了某资源", "access", "permission")) {
            return TicketTypeEnum.ACCESS_REQUEST;
        }
        if (containsAny(text, "变更", "发布", "上线", "回滚", "配置修改", "数据库变更", "change", "deploy", "release")) {
            return TicketTypeEnum.CHANGE_REQUEST;
        }
        if (containsAny(text, "报错", "异常", "500", "超时", "故障", "崩溃", "不可用", "无法访问", "error", "exception", "crash")) {
            return TicketTypeEnum.INCIDENT;
        }
        if (containsAny(text, "测试环境", "生产环境", "容器", "部署", "env")) {
            return TicketTypeEnum.ENVIRONMENT_REQUEST;
        }
        return TicketTypeEnum.INCIDENT;
    }

    /** 根据 title + description 和已确定的 type 推断分类。 */
    private TicketCategoryEnum inferCategory(TicketCreateCommandDTO command, TicketTypeEnum type) {
        String text = getSearchText(command);
        if (containsAny(text, "系统", "服务", "接口", "500", "报错", "异常", "故障", "超时", "error", "exception")) {
            return TicketCategoryEnum.SYSTEM;
        }
        if (containsAny(text, "登录", "账号", "密码", "权限", "角色", "account", "login")) {
            return TicketCategoryEnum.ACCOUNT;
        }
        if (type != TicketTypeEnum.ENVIRONMENT_REQUEST) {
            return switch (type) {
                case ACCESS_REQUEST -> TicketCategoryEnum.ACCOUNT;
                case CHANGE_REQUEST, INCIDENT -> TicketCategoryEnum.SYSTEM;
                case CONSULTATION -> TicketCategoryEnum.OTHER;
                default -> TicketCategoryEnum.SYSTEM;
            };
        }
        if (containsAny(text, "测试环境", "生产环境", "容器", "env")) {
            return TicketCategoryEnum.ENVIRONMENT;
        }
        return TicketCategoryEnum.ENVIRONMENT;
    }

    /** 根据 title + description 和 type 推断优先级。 */
    private TicketPriorityEnum inferPriority(TicketCreateCommandDTO command, TicketTypeEnum type) {
        String text = getSearchText(command);
        if (containsAny(text, "生产", "全部用户", "大面积", "阻塞", "紧急", "无法使用", "严重",
                "urgent", "critical", "down", "outage")) {
            return TicketPriorityEnum.URGENT;
        }
        if (containsAny(text, "影响测试", "影响部分用户", "较急", "high", "blocking")) {
            return TicketPriorityEnum.HIGH;
        }
        if (type == TicketTypeEnum.CHANGE_REQUEST) {
            return TicketPriorityEnum.HIGH;
        }
        return TicketPriorityEnum.MEDIUM;
    }

    /** 根据 type 和用户输入自动生成 typeProfile。 */
    private Map<String, Object> generateTypeProfile(TicketTypeEnum type, TicketCreateCommandDTO command) {
        String title = command.getTitle() != null ? command.getTitle() : "";
        String description = command.getDescription() != null ? command.getDescription() : "";
        String desc = description.isEmpty() ? title : description;

        return switch (type) {
            case INCIDENT -> {
                Map<String, Object> profile = new HashMap<>();
                profile.put("symptom", desc);
                profile.put("impactScope", extractImpactScope(desc));
                yield profile;
            }
            case ACCESS_REQUEST -> {
                Map<String, Object> profile = new HashMap<>();
                profile.put("accountId", "待确认");
                profile.put("targetResource", extractTargetResource(desc));
                profile.put("requestedRole", "待确认");
                profile.put("justification", desc);
                yield profile;
            }
            case CHANGE_REQUEST -> {
                Map<String, Object> profile = new HashMap<>();
                profile.put("changeTarget", extractChangeTarget(desc));
                profile.put("changeWindow", "待确认");
                profile.put("rollbackPlan", "待确认");
                profile.put("impactScope", extractImpactScope(desc));
                yield profile;
            }
            case ENVIRONMENT_REQUEST -> {
                Map<String, Object> profile = new HashMap<>();
                profile.put("environmentName", extractEnvironmentName(desc));
                profile.put("resourceSpec", "待确认");
                profile.put("purpose", desc);
                yield profile;
            }
            case CONSULTATION -> {
                Map<String, Object> profile = new HashMap<>();
                profile.put("questionTopic", title);
                profile.put("expectedOutcome", desc);
                yield profile;
            }
        };
    }

    // ========== 字段提取辅助方法 ==========

    private String extractImpactScope(String text) {
        int idx = text.indexOf("影响");
        if (idx >= 0 && idx + 2 < text.length()) {
            int end = Math.min(idx + 20, text.length());
            return text.substring(idx, end);
        }
        return "待确认";
    }

    private String extractTargetResource(String text) {
        if (containsAny(text, "数据库", "服务器", "集群", "网络", "存储", "系统", "环境", "资源")) {
            String[] keywords = {"数据库", "服务器", "集群", "网络", "存储", "系统", "环境", "资源"};
            for (String kw : keywords) {
                int idx = text.indexOf(kw);
                if (idx >= 0) {
                    int start = Math.max(0, idx - 10);
                    int end = Math.min(text.length(), idx + kw.length() + 15);
                    return text.substring(start, end).trim();
                }
            }
        }
        return "待确认";
    }

    private String extractChangeTarget(String text) {
        int idx = text.indexOf("变更");
        if (idx >= 0 && idx + 2 < text.length()) {
            int end = Math.min(idx + 25, text.length());
            return text.substring(idx, end);
        }
        idx = text.indexOf("发布");
        if (idx >= 0 && idx + 2 < text.length()) {
            int end = Math.min(idx + 25, text.length());
            return text.substring(idx, end);
        }
        if (text.length() > 10) {
            return text.substring(0, Math.min(30, text.length()));
        }
        return "待确认";
    }

    private String extractEnvironmentName(String text) {
        int idx = text.indexOf("环境");
        if (idx >= 3) {
            int start = Math.max(0, idx - 8);
            return text.substring(start, idx + 2).trim();
        }
        if (containsAny(text, "测试", "生产", "预发", "灰度")) {
            String[] envs = {"测试", "生产", "预发", "灰度"};
            for (String env : envs) {
                if (text.contains(env)) {
                    return env + "环境";
                }
            }
        }
        return "待确认";
    }

    private String getSearchText(TicketCreateCommandDTO command) {
        String title = command.getTitle() != null ? command.getTitle() : "";
        String description = command.getDescription() != null ? command.getDescription() : "";
        return (title + " " + description).toLowerCase();
    }

    private boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
