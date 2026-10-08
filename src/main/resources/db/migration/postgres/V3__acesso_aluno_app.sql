-- ============================================================================
-- GERENCIA ACADEMIA — V3: acesso do aluno ao aplicativo (e-mail + senha)
-- ============================================================================
-- Objetivo : permitir que o aluno entre no aplicativo com E-MAIL e SENHA
--            (antes usava CPF + data de nascimento) e adicione a foto de
--            perfil, com persistência real no banco.
--
-- O QUE ESTE SCRIPT FAZ (aditivo e reversível):
--   1) cria a coluna aluno.senha              (hash BCrypt; NULL = não liberada)
--   2) cria a coluna aluno.foto_perfil_base64 (data URL da foto)
--   3) NÃO apaga, não recria e não altera nenhuma tabela ou registro.
--
-- OBSERVAÇÕES IMPORTANTES:
--   - Nenhuma senha é inventada por esta migração: os alunos já cadastrados
--     continuam SEM senha (NULL) e, portanto, sem acesso ao app até a recepção
--     ou o ADMIN liberar uma senha em PUT /api/aluno/{id}/senha-acesso.
--   - O e-mail do aluno NÃO ganha índice único aqui: historicamente ele é um
--     e-mail de CONTATO e pode se repetir entre alunos (mesma família). A
--     unicidade como identidade de LOGIN é validada pela aplicação, e só
--     considera os alunos que já possuem senha — ver AlunoAcessoService.
--   - Para desfazer (rollback), veja o BLOCO DE ROLLOBACK no fim do arquivo.
--
-- COMO APLICAR (psql / console do provedor): execute o arquivo inteiro.
-- É idempotente (pode ser rodado mais de uma vez sem efeito colateral).
-- ============================================================================

-- ============================ COLUNAS ============================
ALTER TABLE aluno ADD COLUMN IF NOT EXISTS senha VARCHAR(255) NULL;
ALTER TABLE aluno ADD COLUMN IF NOT EXISTS foto_perfil_base64 TEXT NULL;

-- ============================ RELATÓRIO ============================
-- Quantos alunos JÁ têm senha de acesso definida após a migração.
-- Esperado: 0 (nenhuma senha é criada automaticamente).
SELECT COUNT(*) AS alunos_com_senha
FROM aluno
WHERE senha IS NOT NULL AND senha <> '';

-- Alunos sem e-mail cadastrado: eles não conseguirão acessar o app até a
-- recepção corrigir o e-mail e definir a senha.
SELECT id, nome, cpf
FROM aluno
WHERE email IS NULL OR BTRIM(email) = '';

-- ============================================================================
-- ROLLOBACK (somente se for realmente necessário; a aplicação funciona sem
-- estas colunas, apenas perde o acesso por e-mail/senha e a foto do aluno):
--
--   ALTER TABLE aluno DROP COLUMN IF EXISTS foto_perfil_base64;
--   ALTER TABLE aluno DROP COLUMN IF EXISTS senha;
-- ============================================================================