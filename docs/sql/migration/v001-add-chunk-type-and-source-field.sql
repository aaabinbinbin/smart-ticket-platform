-- Migration V001: 给 ticket_knowledge_embedding 补充 chunk_type 和 source_field 列
-- 适用场景：已有旧表缺少这两个列，导致 EmbeddingService 写入失败
-- 错误信息：Unknown column 'chunk_type' in 'field list'
-- 执行方法：mysql -h127.0.0.1 -P3306 -uroot -p smart_ticket_platform < 此文件

ALTER TABLE ticket_knowledge_embedding
    ADD COLUMN chunk_type   VARCHAR(64) NOT NULL DEFAULT 'FULL_TEXT' COMMENT '切片类型' AFTER chunk_index,
    ADD COLUMN source_field VARCHAR(64) NOT NULL DEFAULT 'content'   COMMENT '来源字段' AFTER chunk_type;
