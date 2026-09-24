-- ============================================================================
-- CORRECAO PONTUAL (nao recria massa, nao faz DELETE, nao roda o seed completo)
--
-- 1) Unifica o NOME do funcionario com o NOME da conta de usuario vinculada.
-- 2) Corrige o e-mail da conta do Jesiel (jsiesiel@ -> jesiel@).
--
-- Mantem: ids, cargos, senha (Gerencia@123), vinculo funcionario.usuario_id
-- e TODAS as demais tabelas intactas.
--
-- Uso:
--   sqlcmd -S localhost -U sa -P '<senha>' -C -I -f 65001 -b -i correcao-nomes-contas.sql
-- ============================================================================
USE academiaINF2CN;
GO
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRANSACTION;

-- ---------------------------------------------------------------- usuario
UPDATE dbo.usuario SET nome = N'Paulo Henrique Andrade de Oliveira'  WHERE id = 1 AND email = N'paulo@academia.com';
UPDATE dbo.usuario SET nome = N'Samuel dos Santos Martins Vital de Jesus' WHERE id = 2 AND email = N'samuel@academia.com';
UPDATE dbo.usuario SET nome = N'Giulia de Oliveira Nascimento'       WHERE id = 3 AND email = N'giulia@academia.com';
UPDATE dbo.usuario SET nome = N'Misael Levi Almeida da Silva'        WHERE id = 4 AND email = N'misael@academia.com';
UPDATE dbo.usuario SET nome = N'Jesiel Santos Gomes', email = N'jesiel@academia.com' WHERE id = 5 AND email = N'jsiesiel@academia.com';
UPDATE dbo.usuario SET nome = N'Pablo Vinicius Santos Barros'        WHERE id = 6 AND email = N'pablo@academia.com';

-- ------------------------------------------------------------ funcionario
-- Espelha exatamente o nome/e-mail da conta de usuario vinculada.
UPDATE dbo.funcionario SET nome = N'Paulo Henrique Andrade de Oliveira'  WHERE usuario_id = 1;
UPDATE dbo.funcionario SET nome = N'Samuel dos Santos Martins Vital de Jesus' WHERE usuario_id = 2;
UPDATE dbo.funcionario SET nome = N'Giulia de Oliveira Nascimento'       WHERE usuario_id = 3;
UPDATE dbo.funcionario SET nome = N'Misael Levi Almeida da Silva'        WHERE usuario_id = 4;
UPDATE dbo.funcionario SET nome = N'Jesiel Santos Gomes', email = N'jesiel@academia.com' WHERE usuario_id = 5;
UPDATE dbo.funcionario SET nome = N'Pablo Vinicius Santos Barros'        WHERE usuario_id = 6;

COMMIT TRANSACTION;
GO

PRINT 'Nomes/e-mails das 6 contas corrigidos.';
GO

-- ---------------------------------------------------------------- conferencia
SELECT f.id,
       f.nome  AS funcionario,
       u.nome  AS usuario,
       f.email AS email_func,
       u.email AS email_usu,
       u.cargo,
       u.senha,
       CASE WHEN f.nome = u.nome THEN 'OK' ELSE 'DIVERGENTE' END AS nome_confere
FROM dbo.funcionario f
JOIN dbo.usuario u ON u.id = f.usuario_id
ORDER BY f.id;
