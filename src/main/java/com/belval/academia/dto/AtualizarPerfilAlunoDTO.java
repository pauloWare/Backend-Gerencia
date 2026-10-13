package com.belval.academia.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Campos que o ALUNO pode editar diretamente no próprio perfil.
 *
 * <p>Regra GERENCIA (perfil do aluno): edição direta SOMENTE para contato,
 * endereço estruturado e contato de emergência. Nome completo, CPF, data de
 * nascimento e sexo são protegidos (somente leitura; correção via recepção) e
 * por isso NÃO existem neste DTO. Plano, situação, mensalidades, observações,
 * senha e identificadores também não existem aqui.</p>
 *
 * <p>Campos desconhecidos (ex.: {@code nome}, {@code cpf},
 * {@code dataNascimento}, {@code sexo}, {@code plano}, {@code situacao})
 * enviados manualmente são ignorados pelo Jackson — nunca chegam ao serviço,
 * então não há caminho para alterar dado protegido por requisição forjada.
 * O id do registro alterado NUNCA vem daqui: ele é sempre derivado do token
 * ({@code UsuarioAutenticado.alunoId()}).</p>
 *
 * <p>Semântica de ausência (nunca apaga por acidente): {@code null} = campo
 * omitido, mantém o valor atual. String vazia/embranco = tentativa de limpar:
 * aceita para campos opcionais (telefone, endereço, emergência) e recusada
 * para o e-mail (obrigatório). O backend valida tudo de novo,
 * independentemente do app.</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AtualizarPerfilAlunoDTO {

    private String email;
    private String telefone;

    /** CEP com ou sem máscara (8 dígitos); vazio limpa. */
    private String cep;
    /** Rua/avenida. Aceita o apelido {@code rua} enviado pelo app. */
    @JsonAlias("rua")
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;

    private String contatoEmergenciaNome;
    private String contatoEmergenciaTelefone;
}