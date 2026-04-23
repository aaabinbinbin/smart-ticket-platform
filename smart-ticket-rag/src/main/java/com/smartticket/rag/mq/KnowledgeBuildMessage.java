package com.smartticket.rag.mq;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBuildMessage implements Serializable {
    private Long taskId;
    private Long ticketId;
}
