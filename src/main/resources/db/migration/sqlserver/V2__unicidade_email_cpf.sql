-- ============================================================================
-- GERENCIA ACADEMIA — Migração de unicidade (SQL Server / ambiente local)
-- ============================================================================
-- Objetivo : impedir e-mail e CPF duplicados (case-insensitive p/ e-mail,
--            somente dígitos p/ CPF) em Usuario, Aluno e Funcionario.
--
-- COMO APLICAR (SSMS / sqlcmd):
--   1) Execute primeiro o BLOCO 1 (relatório) e corrija as duplicidades
--      apontadas junto aos responsáveis (NÃO apague cadastros sem validação).
--   2) Execute o BLOCO 2 (normalização) e depois o BLOCO 3 (índices).
--   3) Se algum CREATE INDEX falhar por duplicidade remanescente, o próprio
--      erro lista o valor conflitante — corrija e rode novamente.
-- ============================================================================

-- ============================ BLOCO 1 — RELATÓRIO ===========================
-- E-mails duplicados (ignora maiúsculas/minúsculas e espaços).
-- NOTA: a conta de acesso do funcionário vive em dbo.usuario (via
-- funcionario.usuario_id); aqui o e-mail de CONTATO (dbo.funcionario.email)
-- é só informativo e pode se repetir, por isso não recebe índice único.
SELECT LOWER(LTRIM(RTRIM(email))) AS email_normalizado, COUNT(*) AS qtd
FROM dbo.usuario WHERE email IS NOT NULL
GROUP BY LOWER(LTRIM(RTRIM(email))) HAVING COUNT(*) > 1;

-- CPFs duplicados (aluno.cpf e funcionario.cpf estão em formatos diversos:
-- com e sem máscara — conferir dígito a dígito após remover pontuação):
SELECT cpf, COUNT(*) AS qtd FROM dbo.aluno
WHERE cpf IS NOT NULL AND LTRIM(RTRIM(cpf)) <> ''
GROUP BY cpf HAVING COUNT(*) > 1;

SELECT cpf, COUNT(*) AS qtd FROM dbo.funcionario
WHERE cpf IS NOT NULL AND LTRIM(RTRIM(cpf)) <> ''
GROUP BY cpf HAVING COUNT(*) > 1;

-- ====================== BLOCO 2 — NORMALIZAÇÃO (idempotente) =================
-- E-mail: remove espaços das bordas. A comparação case-insensitive é feita
-- pela collation do banco nos índices do Bloco 3.
UPDATE dbo.usuario SET email = LTRIM(RTRIM(email)) WHERE email IS NOT NULL;
UPDATE dbo.aluno   SET email = LTRIM(RTRIM(email)) WHERE email IS NOT NULL;
-- A aplicação grava login sempre em minúsculas; padroniza os existentes.
UPDATE dbo.usuario SET email = LOWER(email) WHERE email IS NOT NULL;
UPDATE dbo.aluno   SET email = LOWER(email) WHERE email IS NOT NULL;

-- CPF: remove pontos, traços, barras e espaços, guardando só os dígitos.
UPDATE dbo.aluno SET cpf = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
    LTRIM(RTRIM(cpf)), '.', ''), '-', ''), '/', ''), ' ', ''), CHAR(9), '')
WHERE cpf IS NOT NULL;
UPDATE dbo.funcionario SET cpf = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
    LTRIM(RTRIM(cpf)), '.', ''), '-', ''), '/', ''), ' ', ''), CHAR(9), '')
WHERE cpf IS NOT NULL;

-- ================= BLOCO 3 — ÍNDICES ÚNICOS (idempotente) ====================
-- E-mail de login: unicidade em dbo.usuario (a conta de acesso do funcionário
-- vive nessa tabela via funcionario.usuario_id). E-mails de CONTATO
-- (aluno.email, funcionario.email) podem se repetir legitimamente e por isso
-- NÃO recebem índice único — a trava é só na identidade de login.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_usuario_email' AND object_id = OBJECT_ID('dbo.usuario'))
    CREATE UNIQUE INDEX UX_usuario_email ON dbo.usuario(email);

-- CPF: único dentro de cada tabela (aluno x funcionário podem coexistir;
-- a API rejeita formato inválido e normaliza antes de comparar).
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_aluno_cpf' AND object_id = OBJECT_ID('dbo.aluno'))
    CREATE UNIQUE INDEX UX_aluno_cpf ON dbo.aluno(cpf) WHERE cpf IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_funcionario_cpf' AND object_id = OBJECT_ID('dbo.funcionario'))
    CREATE UNIQUE INDEX UX_funcionario_cpf ON dbo.funcionario(cpf) WHERE cpf IS NOT NULL;
