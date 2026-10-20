-- ============================================================================
-- GERENCIA ACADEMIA — Migração de unicidade de TELEFONE (SQL Server / local)
-- ============================================================================
-- Objetivo : impedir telefone duplicado em dbo.aluno, dbo.funcionario e
--            dbo.usuario, comparando pelo número normalizado (só dígitos;
--            remove formatação e o código do país 55 quando sobra um número
--            nacional válido de 10/11 dígitos — mesma regra de
--            com.belval.academia.util.Normalizacao.telefone e do script
--            irmão V5 deste mesmo diretório no PostgreSQL).
-- Complementa V2 (e-mail/CPF); não altera nenhuma outra coluna ou restrição.
--
-- ESCOPO E LEITURA DO MODELO:
--   * O índice único é POR TABELA (banco relacional não cria trava entre
--     tabelas). A unicidade ENTRE cadastros (aluno x funcionario x usuario)
--     é garantida na aplicação (UnicidadeCadastroService) para valores
--     novos/alterados.
--   * O par funcionario <-> usuario vinculado (funcionario.usuario_id) é a
--     MESMA identidade: a conta de acesso espelha o telefone do funcionário
--     por design e por isso PODE repetir — no relatório, ignore os grupos
--     formados apenas por esse par.
--   * aluno.contato_emergencia_telefone NÃO entra: é o telefone de terceiros
--     e pode legitimamente coincidir com o número de outro cadastro.
--
-- COMO APLICAR (SSMS / sqlcmd):
--   1) Execute o BLOCO 1 (somente leitura; telefones aparecem mascarados,
--      só os 4 últimos dígitos) e resolva com os responsáveis as linhas
--      marcadas [BLOQUEANTE] e os resíduos não numéricos. NÃO apague nem
--      combine cadastros automaticamente.
--   2) Execute o BLOCO 2 (normalização idempotente: só dígitos; vazio -> NULL).
--   3) Execute o BLOCO 3 (índices únicos). Se falhar por duplicidade
--      remanescente, o próprio erro lista o valor conflitante: corrija e
--      rode novamente.
--   As linhas [INFORMATIVO] (telefone igual entre tabelas DIFERENTES) NÃO
--   bloqueiam esta migração — o índice é por tabela —, mas são cadastros que
--   a regra da aplicação passa a recusar em alterações novas.
-- ============================================================================

-- ============================ BLOCO 1 — RELATÓRIO ===========================
IF OBJECT_ID('tempdb..#telefones') IS NOT NULL DROP TABLE #telefones;

SELECT  'aluno' AS tabela,
        id,
        usuario_id = CAST(NULL AS BIGINT),
        telefone,
        digitos = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            LTRIM(RTRIM(telefone)), ' ', ''), '(', ''), ')', ''), '-', ''),
            '+', ''), '.', ''), '/', ''), CHAR(9), '')
INTO #telefones
FROM dbo.aluno
WHERE telefone IS NOT NULL;

INSERT INTO #telefones (tabela, id, usuario_id, telefone, digitos)
SELECT 'funcionario', id, usuario_id, telefone,
       REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            LTRIM(RTRIM(telefone)), ' ', ''), '(', ''), ')', ''), '-', ''),
            '+', ''), '.', ''), '/', ''), CHAR(9), '')
FROM dbo.funcionario
WHERE telefone IS NOT NULL;

INSERT INTO #telefones (tabela, id, usuario_id, telefone, digitos)
SELECT 'usuario', id, NULL, telefone,
       REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            LTRIM(RTRIM(telefone)), ' ', ''), '(', ''), ')', ''), '-', ''),
            '+', ''), '.', ''), '/', ''), CHAR(9), '')
FROM dbo.usuario
WHERE telefone IS NOT NULL;

ALTER TABLE #telefones ADD normalizado VARCHAR(64);
UPDATE #telefones
SET normalizado = CASE WHEN digitos = '' THEN NULL
                       WHEN LEN(digitos) IN (12, 13) AND LEFT(digitos, 2) = '55'
                            THEN SUBSTRING(digitos, 3, LEN(digitos))
                       ELSE digitos END;

-- [BLOQUEANTE] Telefones duplicados DENTRO da mesma tabela (impedem o índice):
-- resolva cada grupo junto aos responsáveis ANTES do Bloco 3.
SELECT tipo = 'DENTRO_DA_TABELA',
       t.tabela,
       t.id,
       telefone_mascarado = '****' + RIGHT(t.normalizado, 4)
FROM #telefones t
WHERE t.normalizado IS NOT NULL
  AND EXISTS (SELECT 1 FROM #telefones x
              WHERE x.tabela = t.tabela AND x.id <> t.id
                AND x.normalizado = t.normalizado)
ORDER BY t.tabela, t.normalizado, t.id;

-- [BLOQUEANTE] Valores que NÃO se reduzem a dígitos (resíduo manual):
SELECT tipo = 'NAO_NUMERICO', tabela, id, telefone
FROM #telefones
WHERE normalizado IS NOT NULL AND normalizado LIKE '%[^0-9]%';

-- [INFORMATIVO] Telefone sem nenhum dígito (virá NULL no Bloco 2):
SELECT tipo = 'SEM_DIGITOS', tabela, id, telefone
FROM #telefones
WHERE normalizado IS NULL AND LTRIM(RTRIM(telefone)) <> '';
-- [INFORMATIVO] Mesmo telefone entre tabelas DIFERENTES: não impede o índice
-- (é por tabela), mas a aplicação passa a recusar em novas alterações.
-- Grupos formados só por funcionario X + usuario Y (funcionario.usuario_id =
-- usuario.id) são o PAR VINCULADO — a MESMA identidade — e são esperados.
SELECT tipo = 'ENTRE_TABELAS',
       telefone_mascarado = '****' + RIGHT(t.normalizado, 4),
       t.tabela,
       t.id,
       t.usuario_id
FROM #telefones t
WHERE t.normalizado IS NOT NULL
  AND EXISTS (SELECT 1 FROM #telefones x
              WHERE x.normalizado = t.normalizado AND x.tabela <> t.tabela)
ORDER BY t.normalizado, t.tabela, t.id;

DROP TABLE #telefones;

-- ====================== BLOCO 2 — NORMALIZAÇÃO (idempotente) ================
-- Grava somente dígitos (remove ' ( ) - + . / e tabulação); remove o código
-- do país 55 quando sobra número nacional (10/11 dígitos); vazio -> NULL.
-- Reexecutar não altera nada (valores já canônicos permanecem iguais).
-- OBS.: valores com letras NÃO são alterados aqui — o Bloco 1 os lista para
-- correção manual antes desta etapa.

UPDATE a
SET telefone = CASE WHEN s.d = '' THEN NULL
                    WHEN LEN(s.d) IN (12, 13) AND LEFT(s.d, 2) = '55'
                         THEN SUBSTRING(s.d, 3, LEN(s.d))
                    ELSE s.d END
FROM dbo.aluno a
CROSS APPLY (SELECT d = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        LTRIM(RTRIM(a.telefone)), ' ', ''), '(', ''), ')', ''), '-', ''),
        '+', ''), '.', ''), '/', ''), CHAR(9), '')) AS s
WHERE a.telefone IS NOT NULL;

UPDATE f
SET telefone = CASE WHEN s.d = '' THEN NULL
                    WHEN LEN(s.d) IN (12, 13) AND LEFT(s.d, 2) = '55'
                         THEN SUBSTRING(s.d, 3, LEN(s.d))
                    ELSE s.d END
FROM dbo.funcionario f
CROSS APPLY (SELECT d = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        LTRIM(RTRIM(f.telefone)), ' ', ''), '(', ''), ')', ''), '-', ''),
        '+', ''), '.', ''), '/', ''), CHAR(9), '')) AS s
WHERE f.telefone IS NOT NULL;

UPDATE u
SET telefone = CASE WHEN s.d = '' THEN NULL
                    WHEN LEN(s.d) IN (12, 13) AND LEFT(s.d, 2) = '55'
                         THEN SUBSTRING(s.d, 3, LEN(s.d))
                    ELSE s.d END
FROM dbo.usuario u
CROSS APPLY (SELECT d = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        LTRIM(RTRIM(u.telefone)), ' ', ''), '(', ''), ')', ''), '-', ''),
        '+', ''), '.', ''), '/', ''), CHAR(9), '')) AS s
WHERE u.telefone IS NOT NULL;

-- ================= BLOCO 3 — ÍNDICES ÚNICOS (idempotente) ====================
-- Índice FILTRADO (WHERE telefone IS NOT NULL): no SQL Server um índice único
-- sem filtro aceita UM único NULL por coluna; o filtro permite vários cadastros
-- sem telefone. A aplicação nunca grava "" (normaliza para NULL), então não há
-- risco de vários vazios colidirem.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_aluno_telefone' AND object_id = OBJECT_ID('dbo.aluno'))
    CREATE UNIQUE INDEX UX_aluno_telefone ON dbo.aluno(telefone) WHERE telefone IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_funcionario_telefone' AND object_id = OBJECT_ID('dbo.funcionario'))
    CREATE UNIQUE INDEX UX_funcionario_telefone ON dbo.funcionario(telefone) WHERE telefone IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_usuario_telefone' AND object_id = OBJECT_ID('dbo.usuario'))
    CREATE UNIQUE INDEX UX_usuario_telefone ON dbo.usuario(telefone) WHERE telefone IS NOT NULL;

