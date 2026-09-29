package com.belval.academia.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Respostas de erro consistentes para a API.
 *
 * <p>Nada de stack trace, SQL, hash ou detalhe interno vai para o cliente: o
 * detalhe fica no log do servidor e o cliente recebe apenas uma mensagem útil.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Erros de negócio (duplicidade, CPF inválido, dados obrigatórios, ...). */
    @ExceptionHandler(ErroDeRegraDeNegocio.class)
    public ResponseEntity<Map<String, Object>> tratarErroDeNegocio(ErroDeRegraDeNegocio erro) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("erro", erro.getMessage());
        if (erro.getCampo() != null) {
            corpo.put("campo", erro.getCampo());
        }
        return ResponseEntity.status(erro.getStatus()).body(corpo);
    }

    /**
     * Violação de restrição do banco (ex.: índice único de e-mail/CPF) em uma
     * requisição simultânea que passou pela validação. Responde 409 sem expor
     * nomes de colunas/constraints.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> tratarViolacaoDeIntegridade(DataIntegrityViolationException e) {
        log.warn("Restrição de integridade do banco acionada: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "erro", "Não foi possível salvar: já existe um cadastro com estes dados."));
    }

    /** Corpo ausente ou mal formatado. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> tratarCorpoInvalido(HttpMessageNotReadableException e) {
        log.warn("Corpo da requisição inválido: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "erro", "Dados obrigatórios ausentes ou em formato inválido."));
    }

    /** Campos obrigatórios/validados por bean validation. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> tratarValidacao(MethodArgumentNotValidException e) {
        String campo = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(erro -> erro.getField())
                .orElse(null);
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("erro", "Dados obrigatórios ausentes ou inválidos.");
        if (campo != null) {
            corpo.put("campo", campo);
        }
        return ResponseEntity.badRequest().body(corpo);
    }

    /** Usuário autenticado sem permissão (exceções lançadas fora dos filtros). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> tratarSemPermissao(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "erro", "Seu perfil não tem permissão para esta operação."));
    }

    /** Qualquer erro não previsto: mensagem genérica, detalhe apenas no log. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> tratarErroInesperado(Exception e) {
        log.error("Erro inesperado ao processar a requisição", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "erro", "Não foi possível concluir a operação. Tente novamente."));
    }
}
