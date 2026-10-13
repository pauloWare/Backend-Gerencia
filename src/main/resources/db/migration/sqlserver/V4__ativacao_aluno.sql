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
-- COMO APLICAR (SSMS / sqlcmd): execute o arquivo inteiro. E idempotente.
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'ativacao_aluno')
BEGIN
    CREATE TABLE dbo.ativacao_aluno (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        aluno_id BIGINT NOT NULL REFERENCES dbo.aluno (id),
        codigo_hash VARCHAR(255) NOT NULL,
        expira_em DATETIME2 NOT NULL,
        tentativas INT NOT NULL DEFAULT 0,
        consumido_em DATETIME2 NULL,
        versao BIGINT NOT NULL DEFAULT 0,
        criado_em DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_ativacao_aluno_aluno')
    CREATE UNIQUE INDEX UX_ativacao_aluno_aluno ON dbo.ativacao_aluno (aluno_id);
GO

-- ============================================================================
-- ROLLBACK (somente se necessario; remove apenas o fluxo de ativacao por
-- codigo — o login por e-mail/senha continua funcionando):
--   DROP TABLE IF EXISTS dbo.ativacao_aluno;
-- ============================================================================
