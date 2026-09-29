-- ============================================================================
-- GERENCIA ACADEMIA — Migração de unicidade (PostgreSQL / Render)
-- ============================================================================
-- Espelha V2 de SQL Server usando recursos do PostgreSQL. A aplicação também
-- cria os mesmos índices via JPA (TesteBase/schema de teste), então este
-- script é o caminho oficial para o banco hospedado.
--
-- COMO APLICAR:
--   1) Rode o BLOCO 1 (relatório), corrija duplicidades com os responsáveis.
--   2) Rode BLOCOS 2 e 3. CREATE UNIQUE INDEX CONCURRENTLY não roda dentro
--      de transação — execute cada índice separadamente se o banco estiver
--      em produção com tráfego.
-- ============================================================================

-- ============================ BLOCO 1 — RELATÓRIO ===========================
-- NOTA: a conta de acesso do funcionário vive em usuario (via
-- funcionario.usuario_id); o e-mail de CONTATO (aluno.email, funcionario.email)
-- é só informativo e pode se repetir, por isso não recebe índice único.
SELECT LOWER(TRIM(email)) AS email_normalizado, COUNT(*) AS qtd
FROM usuario WHERE email IS NOT NULL
GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1;

SELECT cpf, COUNT(*) AS qtd FROM aluno
WHERE cpf IS NOT NULL AND TRIM(cpf) <> ''
GROUP BY cpf HAVING COUNT(*) > 1;

SELECT cpf, COUNT(*) AS qtd FROM funcionario
WHERE cpf IS NOT NULL AND TRIM(cpf) <> ''
GROUP BY cpf HAVING COUNT(*) > 1;

-- ====================== BLOCO 2 — NORMALIZAÇÃO (idempotente) ================
-- A aplicação grava login sempre em minúsculas; padroniza os existentes.
UPDATE usuario SET email = LOWER(TRIM(email)) WHERE email IS NOT NULL;
UPDATE aluno   SET email = LOWER(TRIM(email)) WHERE email IS NOT NULL;

UPDATE aluno SET cpf = REGEXP_REPLACE(cpf, '\\D', '', 'g') WHERE cpf IS NOT NULL;
UPDATE funcionario SET cpf = REGEXP_REPLACE(cpf, '\\D', '', 'g') WHERE cpf IS NOT NULL;

-- ================= BLOCO 3 — ÍNDICES ÚNICOS (idempotente) ====================
-- LOWER(...) garante unicidade case-insensitive independente da collation.
-- Só a identidade de login (usuario.email) recebe trava: e-mails de CONTATO
-- (aluno.email, funcionario.email) podem se repetir legitimamente.
CREATE UNIQUE INDEX IF NOT EXISTS \"UX_usuario_email\" ON usuario (LOWER(email));
CREATE UNIQUE INDEX IF NOT EXISTS \"UX_aluno_cpf\" ON aluno (cpf) WHERE cpf IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS \"UX_funcionario_cpf\" ON funcionario (cpf) WHERE cpf IS NOT NULL;
