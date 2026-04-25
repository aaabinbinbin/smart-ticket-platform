package com.smartticket.biz.service.ticket;

import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 工单创建字段自动补全服务。
 *
 * <p>普通用户创建工单时只需提供 title + description，系统根据规则自动推断
 * type、category、priority、typeProfile 等结构化字段。</p>
 *
 * <p>如果用户显式传了某个字段，优先尊重用户输入，不会覆盖。</p>
 *
 * <p>当前采用规则实现，后续可在此处预留 LLM enrichment 扩展点，
 * LLM 分支必须有超时和降级设计，降级后回退到规则结果。</p>
 */
@Service
public class TicketCreateEnrichmentService {

    /**
     * 补全工单创建命令中缺失的结构化字段。
     *
     * @param command 原始命令（用户可能只传了 title + description）
     * @return 补全后的命令（缺失字段由规则推断填充）
     */
    public TicketCreateCommandDTO enrich(TicketCreateCommandDTO command) {
        TicketTypeEnum type = command.getType() != null ? command.getType() : inferType(command);
        TicketCategoryEnum category = command.getCategory() != null
                ? command.getCategory() : inferCategory(command, type);
        TicketPriorityEnum priority = command.getPriority() != null
                ? command.getPriority() : inferPriority(command, type);
        // typeProfile 只在用户未传且当前没有显式 typeProfile 时生成
        Map<String, Object> typeProfile = command.getTypeProfile() != null
                ? command.getTypeProfile() : generateTypeProfile(type, command);

        return TicketCreateCommandDTO.builder()
                .title(command.getTitle())
                .description(command.getDescription())
                .type(type)
                .category(category)
                .priority(priority)
                .typeProfile(typeProfile)
                .idempotencyKey(command.getIdempotencyKey())
                .build();
    }

    // ========== type 推断 ==========

    /**
     * 根据 title + description 推断工单类型。
     */
    private TicketTypeEnum inferType(TicketCreateCommandDTO command) {
        String text = getSearchText(command);
        if (containsAny(text, "权限", "账号", "开通", "角色", "登录账号", "访问不了某资源", "access", "permission")) {
            return TicketTypeEnum.ACCESS_REQUEST;
        }
        if (containsAny(text, "变更", "发布", "上线", "回滚", "配置修改", "数据库变更", "change", "deploy", "release")) {
            return TicketTypeEnum.CHANGE_REQUEST;
        }
        if (containsAny(text, "环境", "测试环境", "生产环境", "容器", "部署", "env")) {
            return TicketTypeEnum.ENVIRONMENT_REQUEST;
        }
        // 默认：故障事件
        return TicketTypeEnum.INCIDENT;
    }

    // ========== category 推断 ==========

    /**
     * 根据 title + description 和已确定的 type 推断分类。
     */
    private TicketCategoryEnum inferCategory(TicketCreateCommandDTO command, TicketTypeEnum type) {
        String text = getSearchText(command);
        if (containsAny(text, "登录", "账号", "密码", "权限", "角色", "account", "login")) {
            return TicketCategoryEnum.ACCOUNT;
        }
        if (containsAny(text, "系统", "服务", "接口", "500", "报错", "异常", "故障", "超时", "error", "exception")) {
            return TicketCategoryEnum.SYSTEM;
        }
        if (containsAny(text, "测试环境", "生产环境", "环境", "部署", "容器", "env", "environment")) {
            return TicketCategoryEnum.ENVIRONMENT;
        }
        // 按 type 兜底
        return switch (type) {
            case ACCESS_REQUEST -> TicketCategoryEnum.ACCOUNT;
            case ENVIRONMENT_REQUEST -> TicketCategoryEnum.ENVIRONMENT;
            case CHANGE_REQUEST, INCIDENT -> TicketCategoryEnum.SYSTEM;
            case CONSULTATION -> TicketCategoryEnum.OTHER;
        };
    }

    // ========== priority 推断 ==========

    /**
     * 根据 title + description 和 type 推断优先级。
     */
    private TicketPriorityEnum inferPriority(TicketCreateCommandDTO command, TicketTypeEnum type) {
        String text = getSearchText(command);
        if (containsAny(text, "生产", "全部用户", "大面积", "阻塞", "紧急", "无法使用", "严重",
                "urgent", "critical", "down", "outage")) {
            return TicketPriorityEnum.URGENT;
        }
        if (containsAny(text, "影响测试", "影响部分用户", "较急", "high", "blocking")) {
            return TicketPriorityEnum.HIGH;
        }
        // 变更类默认 HIGH
        if (type == TicketTypeEnum.CHANGE_REQUEST) {
            return TicketPriorityEnum.HIGH;
        }
        return TicketPriorityEnum.MEDIUM;
    }

    // ========== typeProfile 生成 ==========

    /**
     * 根据 type 和用户输入自动生成 typeProfile。
     */
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

    /**
     * 从描述中提取影响范围。
     */
    private String extractImpactScope(String text) {
        // 尝试匹配"影响XXX"模式
        int idx = text.indexOf("影响");
        if (idx >= 0 && idx + 2 < text.length()) {
            int end = Math.min(idx + 20, text.length());
            return text.substring(idx, end);
        }
        return "待确认";
    }

    /**
     * 从描述中提取目标资源。
     */
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

    /**
     * 从描述中提取变更目标。
     */
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

    /**
     * 从描述中提取环境名称。
     */
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

    // ========== 通用辅助方法 ==========

    /**
     * 拼接 title 和 description 用于关键词匹配。
     */
    private String getSearchText(TicketCreateCommandDTO command) {
        String title = command.getTitle() != null ? command.getTitle() : "";
        String description = command.getDescription() != null ? command.getDescription() : "";
        return (title + " " + description).toLowerCase();
    }

    /**
     * 检查文本是否包含任意关键词。
     */
    private boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * LLM enrichment 扩展点（预留）。
     *
     * <p>后续接入 LLM 时在此处实现：调用 LLM 从 title+description 提取结构化字段。
     * 要求：
     * - 必须设置超时（推荐 3-5 秒）
     * - LLM 失败或超时时降级返回 null，由调用方回退到规则结果
     * - LLM 返回结果需要校验格式合法性
     * </p>
     *
     * @param command 原始命令
     * @return LLM 补全后的命令，或 null（降级）
     */
    public TicketCreateCommandDTO enrichWithLlm(TicketCreateCommandDTO command) {
        // TODO: LLM enrichment 扩展点
        // 1. 调用 LLM 提取 type/category/priority/typeProfile
        // 2. 设置超时（3-5 秒）
        // 3. 校验返回结果
        // 4. 失败/超时时返回 null
        return null;
    }
}
