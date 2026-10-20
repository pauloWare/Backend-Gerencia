-- Índices únicos equivalentes aos scripts de produção
-- (back-end/src/main/resources/db/migration/{sqlserver,postgres}/).
-- Em H2, índices únicos permitem vários NULL, por isso não é necessário
-- filtrar por NOT NULL.
CREATE UNIQUE INDEX IF NOT EXISTS ux_usuario_email ON usuario (email);
CREATE UNIQUE INDEX IF NOT EXISTS ux_aluno_cpf ON aluno (cpf);
CREATE UNIQUE INDEX IF NOT EXISTS ux_funcionario_cpf ON funcionario (cpf);
-- Unicidade de TELEFONE (V5__unicidade_telefone.sql no ambiente real).
-- A aplicação grava somente dígitos (Normalizacao.telefone), então a comparação
-- é canônica; nulos são permitidos em massa, vazios nunca são gravados.
CREATE UNIQUE INDEX IF NOT EXISTS ux_aluno_telefone ON aluno (telefone);
CREATE UNIQUE INDEX IF NOT EXISTS ux_funcionario_telefone ON funcionario (telefone);
CREATE UNIQUE INDEX IF NOT EXISTS ux_usuario_telefone ON usuario (telefone);
