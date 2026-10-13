-- ============================================================================
-- GERENCIA ACADEMIA — V4: codigo de ativacao da conta mobile do aluno
-- ============================================================================
-- Objetivo : permitir que o aluno defina a propria senha no app a partir de
--            um codigo de uso unico gerado no cadastro pela recepcao.
--
-- O QUE ESTE SCRIPT FAZ (aditivo, idempotente, sem perda de dados):
--   1) cria a tabela ativacao_aluno (hash BCrypt do codigo; NUNCA texto puro)
--   2) cria indice unico por aluno (um codigo pendente por aluno)
--   3) NAO apaga, nao recria e nao altera nenhuma tabela ou registro.
--
-- COMO APLICAR (psql / console do provedor): execute o arquivo inteiro.
-- ============================================================================

CREATE TABLE IF NOT EXISTS ativacao_aluno (
    id BIGSERIAL PRIMARY KEY,
    aluno_id BIGINT NOT NULL REFERENCES aluno (id),
    codigo_hash VARCHAR(255) NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0,
    consumido_em TIMESTAMPTZ NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS "UX_ativacao_aluno_aluno" ON ativacao_aluno (aluno_id);

-- ============================================================================
-- ROLLBACK (somente se necessario; remove apenas o fluxo de ativacao por
-- codigo — o login por e-mail/senha continua funcionando):
--   DROP TABLE IF EXISTS ativacao_aluno;
-- ============================================================================
