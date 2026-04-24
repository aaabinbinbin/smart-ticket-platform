package com.smartticket.biz.repository.assignment;

import com.smartticket.domain.entity.TicketAssignmentRule;
import com.smartticket.domain.mapper.TicketAssignmentRuleMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 自动分派规则仓储封装，只负责数据访问。
 */
@Repository
public class TicketAssignmentRuleRepository {
    // 映射接口
    private final TicketAssignmentRuleMapper mapper;

    /**
     * 构造工单分派规则仓储。
     */
    public TicketAssignmentRuleRepository(TicketAssignmentRuleMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketAssignmentRule rule) {
        return mapper.insert(rule);
    }

    /**
     * 查询按ID。
     */
    public TicketAssignmentRule findById(Long id) {
        return mapper.findById(id);
    }

    /**
     * 分页查询。
     */
    public List<TicketAssignmentRule> page(String category, String priority, Integer enabled, int offset, int limit) {
        return mapper.page(category, priority, enabled, offset, limit);
    }

    /**
     * 获取统计信息。
     */
    public long count(String category, String priority, Integer enabled) {
        return mapper.count(category, priority, enabled);
    }

    /**
     * 更新。
     */
    public int update(TicketAssignmentRule rule) {
        return mapper.update(rule);
    }

    /**
     * 更新启用。
     */
    public int updateEnabled(Long id, Integer enabled) {
        return mapper.updateEnabled(id, enabled);
    }

    /**
     * 查询最佳匹配结果。
     */
    public TicketAssignmentRule findBestMatch(String category, String priority) {
        return mapper.findBestMatch(category, priority);
    }
}

