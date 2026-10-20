-- ============================================================================
-- GERENCIA ACADEMIA — Migração de unicidade de TELEFONE (PostgreSQL / Render)
-- ============================================================================
-- Objetivo : impedir telefone duplicado em aluno, funcionario e usuario,
--            comparando pelo número normalizado (só dígitos; remove formatação
--            e o código do país 55 quando sobra um número nacional válido de
--            10/11 dígitos — mesma regra de
--            com.belval.academia.util.Normalizacao.telefone e do script irmão
--            V5 no diretório sqlserver/).
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
-- COMO APLICAR:
--   1) Execute o BLOCO 1 (somente leitura; telefones aparecem mascarados,
--      só os 4 últimos dígitos) e resolva com os responsáveis as linhas
--      marcadas [BLOQUEANTE] e os resíduos não numéricos. NÃO apague nem
--      combine cadastros automaticamente.
--   2) Execute o BLOCO 2 (normalização idempotente: só dígitos; vazio -> NULL).
--   3) Execute o BLOCO 3 (índices únicos). Se falhar por duplicidade
--      remanescente, o próprio erro lista o valor conflitante: corrija e
--      rode novamente.
--   CREATE UNIQUE INDEX CONCURRENTLY não roda dentro de transação e não
--   aceita IF NOT EXISTS — se o banco estiver em produção com tráfego,
--   execute os CREATE INDEX do Bloco 3 um a um, fora de transação, após
--   conferir que ainda não existem (os nomes são UX_aluno_telefone,
--   UX_funcionario_telefone, UX_usuario_telefone).
--   As linhas [INFORMATIVO] (telefone igual entre tabelas DIFERENTES) NÃO
--   bloqueiam esta migração — o índice é por tabela.
-- ============================================================================

-- ============================ BLOCO 1 — RELATÓRIO ===========================
CREATE TEMP TABLE tmp_telefones AS
SELECT 'aluno'::text AS tabela,
       id,
       CAST(NULL AS bigint) AS usuario_id,
       telefone
FROM aluno WHERE telefone IS NOT NULL
UNION ALL
SELECT 'funcionario', id, usuario_id, telefone
FROM funcionario WHERE telefone IS NOT NULL
UNION ALL
SELECT 'usuario', id, NULL, telefone
FROM usuario WHERE telefone IS NOT NULL;

-- Mesma remoção de formatação do Bloco 2 (e do Validador da aplicação):
-- ' ( ) - + . / e qualquer espaço/em branco saem; letras e demais caracteres
-- permanecem para aparecerem no relatório [BLOQUEANTE] NAO_NUMERICO.
ALTER TABLE tmp_telefones ADD COLUMN normalizado text;
UPDATE tmp_telefones
SET normalizado = NULLIF(
        CASE
            WHEN length(d) IN (12, 13) AND left(d, 2) = '55' THEN substr(d, 3)
            ELSE d
        END, '')
FROM (SELECT id, tabela,
             regexp_replace(telefone, '[()\-\./+\s]', '', 'g') AS d
      FROM tmp_telefones) s
WHERE tmp_telefones.tabela = s.tabela AND tmp_telefones.id = s.id;

-- [BLOQUEANTE] Telefones duplicados DENTRO da mesma tabela (impedem o índice):
SELECT 'DENTRO_DA_TABELA' AS tipo,
       t.tabela,
       t.id,
       '****' || right(t.normalizado, 4) AS telefone_mascarado
FROM tmp_telefones t
WHERE t.normalizado IS NOT NULL
  AND EXISTS (SELECT 1 FROM tmp_telefones x
              WHERE x.tabela = t.tabela AND x.id <> t.id
                AND x.normalizado = t.normalizado)
ORDER BY t.tabela, t.normalizado, t.id;

-- [BLOQUEANTE] Valores que NÃO se reduzem a dígitos (resíduo manual):
SELECT 'NAO_NUMERICO' AS tipo, tabela, id, telefone
FROM tmp_telefones
WHERE normalizado IS NOT NULL AND normalizado ~ '[^0-9]';

-- [INFORMATIVO] Telefone sem nenhum dígito (virá NULL no Bloco 2):
SELECT 'SEM_DIGITOS' AS tipo, tabela, id, telefone
FROM tmp_telefones
WHERE normalizado IS NULL AND btrim(telefone) <> '';

-- [INFORMATIVO] Mesmo telefone entre tabelas DIFERENTES: não impede o índice
-- (é por tabela), mas a aplicação passa a recusar em novas alterações.
-- Grupos formados só por funcionario X + usuario Y (funcionario.usuario_id =
-- usuario.id) são o PAR VINCULADO — a MESMA identidade — e são esperados.
SELECT 'ENTRE_TABELAS' AS tipo,
       '****' || right(t.normalizado, 4) AS telefone_mascarado,
       t.tabela,
       t.id,
       t.usuario_id
FROM tmp_telefones t
WHERE t.normalizado IS NOT NULL
  AND EXISTS (SELECT 1 FROM tmp_telefones x
              WHERE x.normalizado = t.normalizado AND x.tabela <> t.tabela)
ORDER BY t.normalizado, t.tabela, t.id;

DROP TABLE tmp_telefones;

-- ====================== BLOCO 2 — NORMALIZAÇÃO (idempotente) ================
-- Grava somente dígitos (remove ' ( ) - + . / e espaços); remove o código do
-- país 55 quando sobra número nacional (10/11 dígitos); vazio -> NULL.
-- Reexecutar não altera nada (valores já canônicos permanecem iguais).
-- OBS.: valores com letras NÃO são alterados aqui — o Bloco 1 os lista para
-- correção manual antes desta etapa.

UPDATE aluno
SET telefone = CASE WHEN s.d = '' THEN NULL
                    WHEN length(s.d) IN (12, 13) AND left(s.d, 2) = '55'
                         THEN substr(s.d, 3)
                    ELSE s.d END
FROM (SELECT id, regexp_replace(telefone, '[()\-\./+\s]', '', 'g') AS d
      FROM aluno WHERE telefone IS NOT NULL) s
WHERE aluno.id = s.id;

UPDATE funcionario
SET telefone = CASE WHEN s.d = '' THEN NULL
                    WHEN length(s.d) IN (12, 13) AND left(s.d, 2) = '55'
                         THEN substr(s.d, 3)
                    ELSE s.d END
FROM (SELECT id, regexp_replace(telefone, '[()\-\./+\s]', '', 'g') AS d
      FROM funcionario WHERE telefone IS NOT NULL) s
WHERE funcionario.id = s.id;

UPDATE usuario
SET telefone = CASE WHEN s.d = '' THEN NULL
                    WHEN length(s.d) IN (12, 13) AND left(s.d, 2) = '55'
                         THEN substr(s.d, 3)
                    ELSE s.d END
FROM (SELECT id, regexp_replace(telefone, '[()\-\./+\s]', '', 'g') AS d
      FROM usuario WHERE telefone IS NOT NULL) s
WHERE usuario.id = s.id;

-- ================= BLOCO 3 — ÍNDICES ÚNICOS (idempotente) ====================
-- O filtro WHERE telefone IS NOT NULL mantém a leitura explícita do contrato
-- (vários cadastros sem telefone convivem; "" nunca é gravado — a aplicação
-- normaliza vazio para NULL) e espelha o índice filtrado do SQL Server.
CREATE UNIQUE INDEX IF NOT EXISTS "UX_aluno_telefone"
    ON aluno (telefone) WHERE telefone IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS "UX_funcionario_telefone"
    ON funcionario (telefone) WHERE telefone IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS "UX_usuario_telefone"
    ON usuario (telefone) WHERE telefone IS NOT NULL;

