package com.smartticket.api.dto.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "������������")
public class CreateTicketRequestDTO {
    @NotBlank(message = "�������ⲻ��Ϊ��")
    @Size(max = 200, message = "�������ⲻ�ܳ��� 200 ���ַ�")
    @Schema(description = "��������", example = "���Ի����޷���¼")
    private String title;

    @NotBlank(message = "������������Ϊ��")
    @Schema(description = "��������", example = "���Ի�����¼ʱ�� 500��Ӱ���з��Բ�")
    private String description;

    @Schema(description = "�������� code", example = "INCIDENT")
    private String type;

    @Schema(description = "�����������ύ����չ����")
    private Map<String, Object> typeProfile;

    @Schema(description = "�������� code������ʱ�����������Զ��ƶ�", example = "SYSTEM")
    private String category;

    @Schema(description = "�������ȼ� code������ʱ����������ʹ��Ĭ��ֵ", example = "HIGH")
    private String priority;

    @Size(max = 128, message = "�ݵȼ����ܳ��� 128 ���ַ�")
    @Schema(description = "�����ݵȼ�", example = "create-ticket-001")
    private String idempotencyKey;
}
