-- Migration: Adicionar coluna orgao na tabela tb_solicitacao_organizador
-- Etapa 3: Solicitante de Organizador com campo Órgão
-- A coluna aceita NULL para permitir solicitações antigas sem órgão
-- Novas solicitações serão obrigadas pelo DTO @NotBlank

ALTER TABLE tb_solicitacao_organizador ADD COLUMN orgao VARCHAR(100);