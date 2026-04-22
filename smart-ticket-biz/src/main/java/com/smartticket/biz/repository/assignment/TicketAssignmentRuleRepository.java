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
    private final TicketAssignmentRuleMapper mapper;

    public TicketAssignmentRuleRepository(TicketAssignmentRuleMapper mapper) {
        this.mapper = mapper;
    }

    public int insert(TicketAssignmentRule rule) {
        return mapper.insert(rule);
    }

    public TicketAssignmentRule findById(Long id) {
        return mapper.findById(id);
    }

    public List<TicketAssignmentRule> page(String category, String priority, Integer enabled, int offset, int limit) {
        return mapper.page(category, priority, enabled, offset, limit);
    }

    public long count(String category, String priority, Integer enabled) {
        return mapper.count(category, priority, enabled);
    }

    public int update(TicketAssignmentRule rule) {
        return mapper.update(rule);
    }

    public int updateEnabled(Long id, Integer enabled) {
        return mapper.updateEnabled(id, enabled);
    }

    public TicketAssignmentRule findBestMatch(String category, String priority) {
        return mapper.findBestMatch(category, priority);
    }
}

