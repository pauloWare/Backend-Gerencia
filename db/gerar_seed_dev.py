# -*- coding: utf-8 -*-
"""
Gerador da massa de dados de DEV/TESTE do projeto Gerencia (academia).

Uso:
    python3 gerar_seed_dev.py   ->  gera seed-dev-2024-2026.sql
    sqlcmd -S localhost -U sa -P '<senha sa>' -C -d academiaINF2CN -i seed-dev-2024-2026.sql

IMPORTANTE
  - Nao faz parte da aplicacao (nao e compilado, nao roda automaticamente).
  - Apenas LIMPA (DELETE) e POPULA as 10 tabelas; nao faz DROP de tabelas.
  - Os valores gerados seguem as regras reais do codigo:
      * planos/valores : front-end/src/pages/Cadastro/alunoFormUtils.js (PLANOS_ALUNO)
      * SLA de chamado : back-end .../model/SlaPolicy.java (URGENTE/CRITICA=0, ALTA=1, MEDIA=3, BAIXA=5)
      * receita de pagamento : MensalidadeController (tipo MENSALIDADE, "Mensalidade - <nome>")
      * identificacao de equipamento : ManutencaoController.proximaIdentificacao ("Tipo NN")
      * despesa.tipo  : ALUGUEL/FUNCIONARIOS/ENERGIA/AGUA/EQUIPAMENTOS/MANUTENCAO/OUTROS
      * despesa.classificacao : FIXO/VARIAVEL   |  receita.tipo : MENSALIDADE/VENDA_PRODUTOS/OUTROS
      * chamado.status : ABERTO/EM_ANDAMENTO/FINALIZADO  |  chamado.tipo : CORRETIVA
      * preventiva.status : AGENDADA/EM_ANDAMENTO/CONCLUIDA | tipo : PREVENTIVA
      * aluno.situacao : ATIVO/INATIVO/SUSPENSO/CANCELADO | sexo : M/F
      * equipamento.situacao : ATIVO/MANUTENCAO/INATIVO
      * mensalidade.status : PAGA/PENDENTE | forma : DINHEIRO/CARTAO/PIX
      * cargos : ADMIN/RECEPCIONISTA/FINANCEIRO/TECNICO
"""
import os
import random
import datetime as dt

RNG = random.Random(20260922)
HOJE = dt.date(2026, 9, 22)              # data atual do ambiente
FIM_MENSALIDADE = dt.date(2026, 9, 30)   # ultimo vencimento gerado

OUT = []
def w(s=""):
    OUT.append(s)

# ------------------------------------------------------------------ utils
def valid_cpf(base9):
    d = [int(c) for c in base9]
    r = sum(d[i] * (10 - i) for i in range(9)) % 11
    d.append(0 if r < 2 else 11 - r)
    r = sum(d[i] * (11 - i) for i in range(10)) % 11
    d.append(0 if r < 2 else 11 - r)
    return "".join(map(str, d))

def fmt_cpf(c):
    return "{}.{}.{}-{}".format(c[0:3], c[3:6], c[6:9], c[9:11])

def sem_acento(s):
    for a, b in (("á", "a"), ("à", "a"), ("ã", "a"), ("â", "a"), ("é", "e"), ("ê", "e"),
                 ("í", "i"), ("ó", "o"), ("õ", "o"), ("ô", "o"), ("ú", "u"), ("ç", "c"),
                 ("Á", "A"), ("Ã", "A"), ("É", "E"), ("Ó", "O"), ("Ç", "C")):
        s = s.replace(a, b)
    return s

def esc(s):
    if s is None:
        return "NULL"
    if isinstance(s, bool):
        return "1" if s else "0"
    if isinstance(s, (int, float)):
        return str(s)
    if isinstance(s, dt.date):
        return "'{}'".format(s.isoformat())
    return "N'" + str(s).replace("'", "''") + "'"

def add_dias(d, n):
    return d + dt.timedelta(days=n)

# ------------------------------------------------------------- funcionarios
# (nome, cargo, salario, admissao, nascimento, sexo, telefone, bairro, cidade)
FUNCIONARIOS = [
    ("Paulo Henrique Andrade de Oliveira", "ADMIN", 3200.00, dt.date(2023, 2, 1), dt.date(1985, 4, 12), "M", "19987650011", "Cambuí", "Campinas"),
    ("Samuel Fernandes Ribeiro", "FINANCEIRO", 1900.00, dt.date(2023, 5, 15), dt.date(1990, 9, 3), "M", "19987650022", "Centro", "Campinas"),
    ("Giulia Marques Prado", "FINANCEIRO", 1900.00, dt.date(2024, 3, 1), dt.date(1996, 1, 25), "F", "19987650033", "Taquaral", "Campinas"),
    ("Misael Souza Nogueira", "TECNICO", 1700.00, dt.date(2023, 8, 1), dt.date(1992, 6, 18), "M", "19987650044", "Botafogo", "Campinas"),
    ("Jsiesiel Alves Barbosa", "TECNICO", 1700.00, dt.date(2024, 1, 15), dt.date(1994, 11, 7), "M", "19987650055", "Vila Industrial", "Campinas"),
    ("Pablo Lemos Farias", "TECNICO", 1700.00, dt.date(2024, 6, 1), dt.date(1998, 3, 30), "M", "19987650066", "Guanabara", "Campinas"),
]
EMAILS = ["paulo@academia.com", "samuel@academia.com", "giulia@academia.com",
          "misael@academia.com", "jsiesiel@academia.com", "pablo@academia.com"]
SENHA = "Gerencia@123"
TECNICOS = [f[0] for f in FUNCIONARIOS if f[1] == "TECNICO"]
USUARIOS_RESP = ["samuel@academia.com", "giulia@academia.com", "paulo@academia.com"]

# ------------------------------------------------------------------- alunos
NOMES_ALUNOS = [
    ("Rafael Almeida Castro", "M", dt.date(1993, 5, 14)),
    ("Beatriz Nogueira Ramos", "F", dt.date(1997, 2, 8)),
    ("Thiago Moraes Lins", "M", dt.date(1989, 11, 22)),
    ("Camila Ribeiro Fontes", "F", dt.date(2000, 7, 30)),
    ("Gustavo Pacheco Diniz", "M", dt.date(1995, 1, 9)),
    ("Larissa Camargo Braga", "F", dt.date(1998, 9, 17)),
    ("Vinicius Teixeira Prado", "M", dt.date(1991, 4, 3)),
    ("Mariana Duarte Peixoto", "F", dt.date(1996, 12, 11)),
    ("Felipe Andrade Barros", "M", dt.date(1994, 8, 26)),
    ("Juliana Siqueira Matos", "F", dt.date(1999, 3, 5)),
    ("Rodrigo Vasconcelos Teles", "M", dt.date(1987, 6, 19)),
    ("Fernanda Queiroz Antunes", "F", dt.date(2001, 10, 2)),
    ("Leandro Menezes Fagundes", "M", dt.date(1990, 2, 27)),
    ("Patricia Sales Bonfim", "F", dt.date(1993, 7, 15)),
    ("Bruno Carvalho Esteves", "M", dt.date(1997, 5, 21)),
    ("Renata Lopes Cardoso", "F", dt.date(1995, 11, 4)),
    ("Marcelo Figueiredo Rios", "M", dt.date(1986, 9, 13)),
    ("Aline Correia Bastos", "F", dt.date(2002, 1, 28)),
    ("Diego Sampaio Aguiar", "M", dt.date(1992, 10, 16)),
    ("Tais Marinho Rezende", "F", dt.date(1998, 4, 7)),
    ("Andre Bastos Coelho", "M", dt.date(1994, 12, 23)),
    ("Vanessa Pires Otoni", "F", dt.date(1999, 8, 31)),
    ("Rogerio Damasceno Freire", "M", dt.date(1988, 3, 12)),
    ("Simone Braganca Leite", "F", dt.date(1996, 6, 24)),
    ("Eduardo Tavares Coutinho", "M", dt.date(1993, 1, 18)),
    ("Cristina Amaral Bittencourt", "F", dt.date(2000, 5, 26)),
    ("Fabio Reboucas Miranda", "M", dt.date(1991, 7, 29)),
    ("Priscila Nunes Salvador", "F", dt.date(1997, 9, 6)),
    ("Otavio Guimaraes Prado", "M", dt.date(1990, 12, 1)),
    ("Debora Antunes Vilela", "F", dt.date(2003, 2, 14)),
]

# (matricula, plano, situacao) por aluno.
# 0..17 planos longos (Anual/Semestral/Trimestral); 18..29 plano Mensal entrando em 2026.
MATRICULAS = [
    (dt.date(2024, 1, 15), "Plano Anual", "ATIVO"),
    (dt.date(2024, 3, 10), "Plano Anual", "ATIVO"),
    (dt.date(2025, 1, 20), "Plano Anual", "ATIVO"),
    (dt.date(2025, 6, 5), "Plano Anual", "ATIVO"),
    (dt.date(2024, 6, 1), "Plano Semestral", "ATIVO"),
    (dt.date(2024, 9, 15), "Plano Semestral", "INATIVO"),
    (dt.date(2025, 1, 10), "Plano Semestral", "ATIVO"),
    (dt.date(2025, 4, 20), "Plano Semestral", "ATIVO"),
    (dt.date(2025, 8, 5), "Plano Semestral", "SUSPENSO"),
    (dt.date(2026, 1, 15), "Plano Semestral", "ATIVO"),
    (dt.date(2024, 4, 1), "Plano Trimestral", "ATIVO"),
    (dt.date(2024, 7, 1), "Plano Trimestral", "ATIVO"),
    (dt.date(2024, 10, 1), "Plano Trimestral", "CANCELADO"),
    (dt.date(2025, 1, 5), "Plano Trimestral", "ATIVO"),
    (dt.date(2025, 4, 10), "Plano Trimestral", "ATIVO"),
    (dt.date(2025, 7, 20), "Plano Trimestral", "ATIVO"),
    (dt.date(2025, 10, 5), "Plano Trimestral", "INATIVO"),
    (dt.date(2026, 2, 1), "Plano Trimestral", "ATIVO"),
    (dt.date(2026, 1, 10), "Plano Mensal", "ATIVO"),
    (dt.date(2026, 2, 5), "Plano Mensal", "ATIVO"),
    (dt.date(2026, 2, 20), "Plano Mensal", "ATIVO"),
    (dt.date(2026, 3, 10), "Plano Mensal", "ATIVO"),
    (dt.date(2026, 3, 25), "Plano Mensal", "ATIVO"),
    (dt.date(2026, 4, 15), "Plano Mensal", "ATIVO"),
    (dt.date(2026, 5, 5), "Plano Mensal", "ATIVO"),
    (dt.date(2026, 5, 20), "Plano Mensal", "ATIVO"),
    (dt.date(2026, 6, 10), "Plano Mensal", "ATIVO"),
    (dt.date(2026, 7, 1), "Plano Mensal", "ATIVO"),
    (dt.date(2026, 7, 25), "Plano Mensal", "ATIVO"),
    (dt.date(2026, 8, 15), "Plano Mensal", "ATIVO"),
]

RUAS = ["Rua das Acácias", "Avenida Brasil", "Rua Bartira", "Rua das Palmeiras", "Avenida Andrade Neves",
        "Rua Regente Feijó", "Rua Barão de Jaguara", "Avenida Norte-Sul", "Rua Coronel Quirino",
        "Rua Jorge Figueiredo Corrêa", "Rua Padre Vieira", "Avenida Marechal Rondon"]
BAIRROS = ["Centro", "Cambuí", "Taquaral", "Guanabara", "Barão Geraldo", "Botafogo",
           "Vila Industrial", "Jardim Chapadão", "Ponte Preta", "Vila União"]
CIDADES = [("Campinas", "SP"), ("Valinhos", "SP"), ("Hortolândia", "SP"), ("Sumaré", "SP"),
           ("Indaiatuba", "SP"), ("Paulínia", "SP")]
PLANOS = {
    "Plano Mensal": (120.0, 30),
    "Plano Trimestral": (330.0, 90),
    "Plano Semestral": (600.0, 180),
    "Plano Anual": (1100.0, 365),
}
TIPOS_EQUIPAMENTO = [
    ("Esteira", 3, "Life Fitness"),
    ("Bicicleta Ergométrica", 2, "Movement"),
    ("Elíptico", 1, "Movement"),
    ("Remo", 1, "Kikos"),
    ("Leg Press", 1, "Righetto"),
    ("Supino Reto", 1, "Righetto"),
    ("Puxada Alta", 1, "Righetto"),
    ("Cadeira Extensora", 1, "Righetto"),
    ("Escada", 1, "Movement"),
]
LOCALIZACOES = ["Sala de cardio", "Sala de musculação", "Área funcional", "Sala de spinning"]
OBS_SITUACAO = {
    "INATIVO": "Aluno solicitou trancamento da matrícula.",
    "SUSPENSO": "Matrícula suspensa temporariamente por inadimplência.",
    "CANCELADO": "Contrato encerrado a pedido do aluno.",
}
PROBLEMAS = {
    "Esteira": ["Esteira desliga sozinha durante o uso", "Painel com display apagado",
                "Correia patinando em alta velocidade", "Ruído na estrutura ao acelerar"],
    "Bicicleta Ergométrica": ["Selim solto e travando", "Resistência não altera",
                              "Pedal com folga no eixo", "Painel não marca batimentos"],
    "Elíptico": ["Mancal estalando no movimento", "Braço de apoio com folga"],
    "Remo": ["Correia do remo desfiada", "Assento não desliza corretamente"],
    "Leg Press": ["Cabo do leg press desgastado", "Trava de segurança não engata"],
    "Supino Reto": ["Barra com rolamento travado", "Banco com rasgo no estofado"],
    "Puxada Alta": ["Cabo da puxada alta desgastado", "Polia rangendo"],
    "Cadeira Extensora": ["Estofado de apoio danificado", "Freio de segurança solto"],
    "Escada": ["Degrau com ruído e folga", "Sistema de resistência falhando"],
}
SERVICOS = {
    "Esteira": "Lubrificação da esteira e regulagem da correia",
    "Bicicleta Ergométrica": "Aperto e lubrificação do sistema de resistência",
    "Elíptico": "Troca de rolamentos e lubrificação geral",
    "Remo": "Inspeção do cabo e lubrificação do trilho",
    "Leg Press": "Revisão do cabo e lubrificação das polias",
    "Supino Reto": "Aperto da estrutura e inspeção do estofado",
    "Puxada Alta": "Revisão do cabo e das polias",
    "Cadeira Extensora": "Revisão dos eixos e aperto geral",
    "Escada": "Lubrificação dos degraus e revisão do sistema",
}

# ============================================================ emissao SQL
COUNTS = {}

def ins(table, cols, rows):
    COUNTS[table] = len(rows)
    if not rows:
        return
    w("SET IDENTITY_INSERT dbo.{} ON;".format(table))
    w("INSERT INTO dbo.{} ({}) VALUES".format(table, ", ".join(cols)))
    for i, r in enumerate(rows):
        suf = ";" if i == len(rows) - 1 else ","
        w("  (" + ", ".join(esc(v) for v in r) + ")" + suf)
    w("SET IDENTITY_INSERT dbo.{} OFF;".format(table))
    w()

TABELAS = ["frequencia", "receita", "mensalidade", "despesa", "chamado",
           "manutencao_preventiva", "funcionario", "equipamento", "aluno", "usuario"]

def emitir_limpeza():
    w("-- ===== LIMPEZA (DELETE respeitando as FKs; sem DROP de tabelas) =====")
    for t in TABELAS:
        w("DELETE FROM dbo.{};".format(t))
    for t in TABELAS:
        w("DBCC CHECKIDENT ('dbo.{}', RESEED, 0) WITH NO_INFOMSGS;".format(t))
    w()

# ---------------------------------------------------------------- usuarios
def gerar_usuarios():
    rows = []
    for i, f in enumerate(FUNCIONARIOS, start=1):
        nome, cargo, sal, adm, nasc, sexo, tel, bairro, cidade = f
        status = "admin" if cargo == "ADMIN" else "user"
        rows.append((i, nome, EMAILS[i - 1], SENHA, cargo, status, tel, sexo, nasc.isoformat()))
    ins("usuario", ["id", "nome", "email", "senha", "cargo", "status",
                    "telefone", "sexo", "data_nascimento"], rows)

# ------------------------------------------------------------ funcionarios
CPFS_USADOS = set()

def novo_cpf():
    while True:
        c = valid_cpf("".join(str(RNG.randint(0, 9)) for _ in range(9)))
        if c not in CPFS_USADOS:
            CPFS_USADOS.add(c)
            return c

def gerar_funcionarios():
    rows = []
    for i, f in enumerate(FUNCIONARIOS, start=1):
        nome, cargo, sal, adm, nasc, sexo, tel, bairro, cidade = f
        cep = "1301{:04d}".format(1000 + i)
        rows.append((i, nome, cargo, adm, sal, "ATIVO", tel, bairro, cep, cidade,
                     "Sala " + str(i), fmt_cpf(novo_cpf()), nasc,
                     EMAILS[i - 1], "SP", RUAS[i % len(RUAS)], str(100 + i * 7), i))
    ins("funcionario", ["id", "nome", "cargo", "data_admissao", "salario", "situacao",
                        "telefone", "bairro", "cep", "cidade", "complemento", "cpf",
                        "data_nascimento", "email", "estado", "logradouro", "numero",
                        "usuario_id"], rows)

# ------------------------------------------------------------- alunos
ALUNOS = []

def gerar_alunos():
    rows = []
    for i, (nome, sexo, nasc) in enumerate(NOMES_ALUNOS):
        matricula, plano, situacao = MATRICULAS[i]
        partes = sem_acento(nome).lower().split()
        email = "{}.{}@email.com".format(partes[0], partes[-1])
        bairro = BAIRROS[i % len(BAIRROS)]
        cidade, uf = CIDADES[i % len(CIDADES)]
        rua = RUAS[i % len(RUAS)]
        numero = str(80 + i * 13)
        cep = "13{:03d}{:03d}".format(100 + i, 200 + i)
        tel = "19987{:02d}{:04d}".format(60 + i, 1000 + i * 17)
        cpf = fmt_cpf(novo_cpf())
        cont_nome = NOMES_ALUNOS[(i + 7) % len(NOMES_ALUNOS)][0]
        cont_tel = "19987{:02d}{:04d}".format(90 + i, 2000 + i * 11)
        endereco = "{}, {} - {}, {}/{}".format(rua, numero, bairro, cidade, uf)
        rows.append((i + 1, nome, cpf, nasc, sexo, tel, email,
                     endereco, "{} - ({}) {}".format(cont_nome, tel[0:2], tel[2:]),
                     matricula, plano, situacao, OBS_SITUACAO.get(situacao),
                     cep, rua, numero, "Bloco " + chr(65 + (i % 4)), bairro,
                     cont_nome, cont_tel))
        ALUNOS.append({"id": i + 1, "nome": nome, "matricula": matricula,
                       "plano": plano, "situacao": situacao})
    ins("aluno", ["id", "nome", "cpf", "data_nascimento", "sexo", "telefone", "email",
                  "endereco", "contato_emergencia", "data_matricula", "plano", "situacao",
                  "observacoes", "cep", "logradouro", "numero", "complemento", "bairro",
                  "contato_emergencia_nome", "contato_emergencia_telefone"], rows)

# ------------------------------------------------------------ equipamentos
EQUIPAMENTOS = []

def gerar_equipamentos():
    rows = []
    eid = 1
    em_manutencao = {"Elíptico 01", "Cadeira Extensora 01"}
    inativos = {"Escada 01"}
    for tipo, qtd, marca in TIPOS_EQUIPAMENTO:
        for n in range(1, qtd + 1):
            nome = "{} {:02d}".format(tipo, n)
            sit = "MANUTENCAO" if nome in em_manutencao else ("INATIVO" if nome in inativos else "ATIVO")
            loc = LOCALIZACOES[eid % len(LOCALIZACOES)]
            rows.append((eid, nome, marca, loc, sit))
            EQUIPAMENTOS.append({"id": eid, "nome": nome, "tipo": tipo})
            eid += 1
    ins("equipamento", ["id", "nome", "marca", "localizacao", "situacao"], rows)

# ------------------------------------------------------------ mensalidades
MENSALIDADES = []

def cargas(matricula, duracao):
    res, k = [], 1
    while True:
        venc = add_dias(matricula, duracao * k)
        if venc > FIM_MENSALIDADE:
            break
        res.append(venc)
        k += 1
    return res

def paga_ou_nao(aluno, venc):
    if aluno["situacao"] in ("INATIVO", "CANCELADO"):
        return RNG.random() < 0.97
    if aluno["situacao"] == "SUSPENSO":
        return RNG.random() < 0.80
    if venc < dt.date(2026, 9, 1):
        return RNG.random() < 0.96
    return RNG.random() < 0.35

def gerar_mensalidades():
    rows, mid = [], 1
    for a in ALUNOS:
        valor, duracao = PLANOS[a["plano"]]
        for venc in cargas(a["matricula"], duracao):
            if paga_ou_nao(a, venc):
                status = "PAGA"
                dpag = add_dias(venc, RNG.randint(-4, 6))
                if dpag > HOJE:
                    dpag = HOJE
                forma = RNG.choices(["PIX", "CARTAO", "DINHEIRO"], [0.60, 0.25, 0.15])[0]
                resp = RNG.choice(USUARIOS_RESP)
            else:
                status, dpag, forma, resp = "PENDENTE", None, None, None
            rows.append((mid, a["id"], venc, status, valor, dpag, forma, resp))
            MENSALIDADES.append({"id": mid, "aluno_id": a["id"], "nome": a["nome"],
                                 "valor": valor, "venc": venc, "status": status,
                                 "data_pagamento": dpag})
            mid += 1
    ins("mensalidade", ["id", "aluno_id", "data_vencimento", "status", "valor",
                        "data_pagamento", "forma_pagamento", "usuario_responsavel"], rows)

# --------------------------------------------------------------- receitas
def gerar_receitas():
    rows, rid = [], 1
    for m in MENSALIDADES:          # regra do MensalidadeController.registrarPagamento
        if m["status"] == "PAGA":
            rows.append((rid, "MENSALIDADE", "Mensalidade - " + m["nome"], m["valor"],
                         m["data_pagamento"], m["aluno_id"], m["id"]))
            rid += 1
    extras = [                      # receitas eventuais, sempre com descricao explicita
        (dt.date(2024, 5, 18), "VENDA_PRODUTOS", "Venda de suplementos - Whey Protein 900g", 189.90),
        (dt.date(2024, 11, 23), "VENDA_PRODUTOS", "Venda de acessórios - Luvas de treino", 79.90),
        (dt.date(2025, 3, 8), "OUTROS", "Avaliação física avulsa (não associado)", 120.00),
        (dt.date(2025, 6, 14), "VENDA_PRODUTOS", "Venda de suplementos - Creatina 300g", 145.00),
        (dt.date(2025, 9, 27), "VENDA_PRODUTOS", "Venda de vestuário - Camiseta Academia", 69.90),
        (dt.date(2025, 12, 5), "OUTROS", "Aula de personal trainer avulsa", 150.00),
        (dt.date(2026, 2, 12), "VENDA_PRODUTOS", "Venda de suplementos - Whey Protein 900g", 189.90),
        (dt.date(2026, 4, 3), "VENDA_PRODUTOS", "Venda de acessórios - Garrafa térmica", 59.90),
        (dt.date(2026, 6, 20), "VENDA_PRODUTOS", "Venda de suplementos - Pré-treino", 129.90),
        (dt.date(2026, 8, 8), "OUTROS", "Avaliação física avulsa (não associado)", 120.00),
    ]
    for data, tipo, desc, valor in extras:
        rows.append((rid, tipo, desc, valor, data, None, None))
        rid += 1
    ins("receita", ["id", "tipo", "descricao", "valor", "data", "aluno_id", "mensalidade_id"], rows)

# -------------------------------------------------------------- frequencia
def gerar_frequencias():
    atrasados = {}
    for m in MENSALIDADES:
        if m["status"] == "PENDENTE" and m["venc"] < HOJE:
            atrasados[m["aluno_id"]] = atrasados.get(m["aluno_id"], 0) + 1

    indices = list(range(len(ALUNOS)))
    RNG.shuffle(indices)
    perfil = {}
    for pos, idx in enumerate(indices):
        perfil[idx] = "alta" if pos < 8 else ("media" if pos < 22 else "baixa")
    for idx, qtd in atrasados.items():          # inadimplente tende a frequentar menos
        if qtd >= 2 and perfil[idx - 1] == "alta":
            perfil[idx - 1] = "media"
        elif qtd >= 3:
            perfil[idx - 1] = "baixa"

    caps = {"alta": 17, "media": 11, "baixa": 6}
    corte = {"ATIVO": HOJE, "SUSPENSO": dt.date(2026, 6, 30),
             "INATIVO": dt.date(2026, 3, 31), "CANCELADO": dt.date(2025, 12, 31)}
    recente = add_dias(HOJE, -89)

    rows, fid = [], 1
    for a in ALUNOS:
        ini = a["matricula"]
        fim = min(HOJE, corte.get(a["situacao"], HOJE))
        if fim <= ini:
            continue
        dias = []
        d = ini
        while d <= fim:
            if d.weekday() != 6:                # academia fecha domingo
                dias.append(d)
            d = add_dias(d, 1)
        if not dias:
            continue
        cap = caps[perfil[a["id"] - 1]]
        pesos = []
        for d in dias:                          # peso maior para datas recentes
            if d >= add_dias(HOJE, -14):
                pesos.append(8.0)
            elif d >= recente:
                pesos.append(4.0)
            else:
                pesos.append(1.0)
        ordem = sorted(range(len(dias)),
                       key=lambda i: RNG.random() ** (1.0 / pesos[i]), reverse=True)
        sel = sorted(dias[i] for i in ordem[:min(cap, len(dias))])
        if a["situacao"] == "ATIVO" and HOJE.weekday() != 6 and RNG.random() < 0.32:
            if HOJE not in sel:
                sel.append(HOJE)                # garante movimento no dia atual (Dashboard)
        for d in sorted(sel):
            ent = RNG.randint(6 * 60, 20 * 60 + 30)
            sai = min(ent + RNG.randint(45, 105), 22 * 60)
            he = "{:02d}:{:02d}:00".format(ent // 60, ent % 60)
            hs = "{:02d}:{:02d}:00".format(sai // 60, sai % 60)
            rows.append((fid, a["id"], d, he, hs))
            fid += 1
    ins("frequencia", ["id", "aluno_id", "data", "hora_entrada", "hora_saida"], rows)

# ---------------------------------------------------------------- chamados
SLA_DIAS = {"URGENTE": 0, "ALTA": 1, "MEDIA": 3, "BAIXA": 5}   # = SlaPolicy.java
CHAMADOS = []

def prioridade_aleatoria():
    return RNG.choices(["MEDIA", "ALTA", "BAIXA", "URGENTE"], [0.40, 0.25, 0.20, 0.15])[0]

def data_no_ano(ano, inicio=dt.date(1, 1, 1), fim=dt.date(9999, 12, 31)):
    a = max(dt.date(ano, 1, 1), inicio)
    b = min(dt.date(ano, 12, 31), fim)
    return add_dias(a, RNG.randint(0, (b - a).days))

def gerar_chamados():
    rows, cid = [], 1
    plano = [("FINALIZADO", 2024), ("FINALIZADO", 2024), ("FINALIZADO", 2024),
             ("FINALIZADO", 2024), ("FINALIZADO", 2024), ("FINALIZADO", 2024),
             ("FINALIZADO", 2024),
             ("FINALIZADO", 2025), ("FINALIZADO", 2025), ("FINALIZADO", 2025),
             ("FINALIZADO", 2025), ("FINALIZADO", 2025), ("FINALIZADO", 2025),
             ("FINALIZADO", 2025), ("FINALIZADO", 2025),
             ("FINALIZADO", 2026), ("FINALIZADO", 2026), ("FINALIZADO", 2026),
             ("FINALIZADO", 2026), ("FINALIZADO", 2026), ("FINALIZADO", 2026),
             ("FINALIZADO", 2026), ("FINALIZADO", 2026), ("FINALIZADO", 2026)]
    plano += [("EM_ANDAMENTO", 2026)] * 5
    plano += [("ABERTO", 2026)] * 6
    RNG.shuffle(plano)
    for status, ano in plano:
        eq = RNG.choice(EQUIPAMENTOS)
        prioridade = prioridade_aleatoria()
        sla = SLA_DIAS[prioridade]
        if status == "FINALIZADO":
            data = data_no_ano(ano, dt.date(2024, 1, 15), dt.date(2026, 8, 20))
            conclusao = add_dias(data, RNG.randint(0, sla + 4))
            if conclusao > HOJE:
                conclusao = HOJE
            resp = RNG.choice(TECNICOS)
            obs = "Serviço concluído e equipamento liberado para uso."
        elif status == "EM_ANDAMENTO":
            data = data_no_ano(2026, dt.date(2026, 6, 1), dt.date(2026, 9, 15))
            conclusao = None
            resp = RNG.choice(TECNICOS)
            obs = "Peça solicitada ao fornecedor; aguardando chegada para finalizar."
        else:
            data = data_no_ano(2026, dt.date(2026, 7, 1), dt.date(2026, 9, 20))
            conclusao = None
            resp = RNG.choice(TECNICOS) if RNG.random() < 0.5 else None
            obs = "Chamado registrado pela recepção e aguardando triagem técnica."
        hora = "{:02d}:{:02d}:00".format(RNG.randint(6, 20), RNG.choice([0, 10, 15, 30, 45]))
        rows.append((cid, eq["id"], RNG.choice(PROBLEMAS[eq["tipo"]]), "CORRETIVA",
                     prioridade, sla, status, resp, data, hora, conclusao, obs))
        CHAMADOS.append({"id": cid, "status": status, "equipamento": eq["nome"],
                         "data": data, "prioridade": prioridade, "sla": sla})
        cid += 1
    ins("chamado", ["id", "equipamento_id", "problema", "tipo", "prioridade", "sla_dias",
                    "status", "responsavel", "data", "hora", "data_conclusao",
                    "observacoes"], rows)

# ------------------------------------------------------------- preventivas
def gerar_preventivas():
    rows, pid = [], 1
    statuses = ["CONCLUIDA"] * 10 + ["AGENDADA"] * 6 + ["EM_ANDAMENTO"] * 4
    RNG.shuffle(statuses)
    for st in statuses:
        eq = EQUIPAMENTOS[(pid * 5) % len(EQUIPAMENTOS)]
        per = RNG.choice([30, 60, 90, 180])
        if st == "CONCLUIDA":
            ultima = data_no_ano(RNG.choice([2024, 2025, 2026]),
                                 dt.date(2024, 2, 1), dt.date(2026, 6, 30))
            prox = add_dias(ultima, per)
            if prox < HOJE:
                prox = add_dias(HOJE, RNG.randint(5, 60))
            obs = "Manutenção preventiva realizada conforme plano de manutenção."
        elif st == "AGENDADA":
            if RNG.random() < 0.65:                  # agendada para o futuro (pendente)
                prox = add_dias(HOJE, RNG.randint(3, 50))
                ultima = add_dias(prox, -per)
            else:                                    # agendada e vencida (atrasada)
                ultima = add_dias(HOJE, -(per + RNG.randint(10, 60)))
                prox = add_dias(ultima, per)
            obs = "Preventiva agendada para a próxima janela de manutenção."
        else:                                        # EM_ANDAMENTO
            if RNG.random() < 0.5:
                prox = add_dias(HOJE, RNG.randint(5, 45))
                ultima = add_dias(prox, -per)
            else:
                ultima = add_dias(HOJE, -(per + RNG.randint(5, 40)))
                prox = add_dias(ultima, per)
            obs = "Preventiva em execução pela equipe técnica."
        rows.append((pid, "PREVENTIVA", eq["id"], SERVICOS[eq["tipo"]], per,
                     ultima, prox, RNG.choice(TECNICOS), st, obs))
        pid += 1
    ins("manutencao_preventiva", ["id", "tipo", "equipamento_id", "servico", "periodicidade",
                                  "data_ultima_manutencao", "proxima_manutencao", "responsavel",
                                  "status", "observacoes"], rows)

# ---------------------------------------------------------------- despesas
MESES_FINANCEIROS = [
    dt.date(2024, 4, 5), dt.date(2024, 9, 5), dt.date(2025, 1, 10), dt.date(2025, 5, 10),
    dt.date(2025, 9, 10), dt.date(2026, 1, 10), dt.date(2026, 3, 10), dt.date(2026, 5, 10),
    dt.date(2026, 7, 10), dt.date(2026, 9, 10),
]

def gerar_despesas():
    rows, did = [], 1
    folha = round(sum(f[2] for f in FUNCIONARIOS), 2)   # coerente com os salários cadastrados
    meses_aluguel = [MESES_FINANCEIROS[i] for i in (0, 2, 4, 5, 6, 7, 8, 9)]
    for m in meses_aluguel:
        rows.append((did, "ALUGUEL", "FIXO", "Aluguel do imóvel da academia", 1500.0, m, None))
        did += 1
    # Folha de pagamento registrada nos meses em que a base possui a despesa
    # detalhada (a tabela de despesas do sistema e uma amostra, nao o livro
    # caixa completo). Evita deixar o saldo global exageradamente negativo.
    for idx in (2, 7):
        rows.append((did, "FUNCIONARIOS", "FIXO", "Folha de pagamento da equipe",
                     folha, MESES_FINANCEIROS[idx], None))
        did += 1
    for m, v in [(dt.date(2024, 8, 12), 780.0), (dt.date(2025, 6, 12), 1120.0),
                 (dt.date(2026, 2, 12), 950.0), (dt.date(2026, 8, 12), 1380.0)]:
        rows.append((did, "ENERGIA", "VARIAVEL", "Conta de energia elétrica", v, m, None))
        did += 1
    for m, v in [(dt.date(2024, 11, 14), 240.0), (dt.date(2025, 9, 14), 310.0),
                 (dt.date(2026, 7, 14), 355.0)]:
        rows.append((did, "AGUA", "VARIAVEL", "Conta de água e esgoto", v, m, None))
        did += 1
    rows.append((did, "EQUIPAMENTOS", "VARIAVEL",
                 "Aquisição de acessórios de musculação (anilhas e halteres)",
                 3200.0, dt.date(2025, 3, 22), None))
    did += 1

    faixa = {"BAIXA": (150.0, 350.0), "MEDIA": (300.0, 800.0),
             "ALTA": (500.0, 1500.0), "URGENTE": (700.0, 2000.0)}
    concluidos = [c for c in CHAMADOS if c["status"] == "FINALIZADO"]
    concluidos.sort(key=lambda c: c["data"])
    passo = max(1, len(concluidos) // 12)
    escolhidos = concluidos[::passo][:12]
    for c in escolhidos:
        lo, hi = faixa[c["prioridade"]]
        valor = round(RNG.uniform(lo, hi), 2)
        data = add_dias(c["data"], RNG.randint(0, 3))
        rows.append((did, "MANUTENCAO", "VARIAVEL",
                     "Manutenção corretiva - " + c["equipamento"], valor, data, c["id"]))
        did += 1
    ins("despesa", ["id", "tipo", "classificacao", "descricao", "valor", "data",
                    "manutencao_id"], rows)

# ================================================================ execucao
def main():
    w("-- ============================================================================")
    w("-- MASSA DE DADOS DE DEV/TESTE - projeto Gerencia (academia)")
    w("-- Gerado automaticamente por gerar_seed_dev.py (nao roda junto com a aplicacao).")
    w("-- Limpa (DELETE) e popula: usuario, funcionario, aluno, equipamento,")
    w("-- mensalidade, receita, frequencia, chamado, manutencao_preventiva, despesa.")
    w("-- ============================================================================")
    w("USE academiaINF2CN;")
    w("GO")
    w("-- Opcoes exigidas pelas tabelas criadas pelo Hibernate (DDL do JPA)")
    w("SET QUOTED_IDENTIFIER ON;")
    w("SET ANSI_NULLS ON;")
    w("SET ANSI_PADDING ON;")
    w("SET ANSI_WARNINGS ON;")
    w("SET CONCAT_NULL_YIELDS_NULL ON;")
    w("SET ARITHABORT ON;")
    w("SET NUMERIC_ROUNDABORT OFF;")
    w("SET NOCOUNT ON;")
    w("SET XACT_ABORT ON;")
    w("GO")
    w("BEGIN TRANSACTION;")
    w()
    emitir_limpeza()
    w("-- ===== POPULACAO (ordem que respeita as FKs) =====")
    w()
    gerar_usuarios()
    gerar_funcionarios()
    gerar_alunos()
    gerar_equipamentos()
    gerar_mensalidades()
    gerar_receitas()
    gerar_frequencias()
    gerar_chamados()
    gerar_preventivas()
    gerar_despesas()
    w("COMMIT TRANSACTION;")
    w("PRINT 'Massa de dev/teste aplicada com sucesso.';")

    saida = os.path.join(os.path.dirname(os.path.abspath(__file__)), "seed-dev-2024-2026.sql")
    with open(saida, "w", encoding="utf-8") as f:
        f.write("\n".join(OUT) + "\n")

    print("SQL gerado:", saida)
    print("Linhas:", len(OUT))
    for t in ["usuario", "funcionario", "aluno", "equipamento", "mensalidade",
              "receita", "frequencia", "chamado", "manutencao_preventiva", "despesa"]:
        print("  {:22s} {}".format(t, COUNTS.get(t, 0)))
    print("Mensalidades PAGA/PENDENTE:",
          sum(1 for m in MENSALIDADES if m["status"] == "PAGA"),
          "/", sum(1 for m in MENSALIDADES if m["status"] == "PENDENTE"))
    anos = {}
    for m in MENSALIDADES:
        anos[m["venc"].year] = anos.get(m["venc"].year, 0) + 1
    print("Mensalidades por ano:", dict(sorted(anos.items())))


if __name__ == "__main__":
    main()








