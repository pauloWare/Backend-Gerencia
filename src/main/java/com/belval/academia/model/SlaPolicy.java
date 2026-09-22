package com.belval.academia.model;

/**
 * RODADA 1 — SLA DE MANUTENÇÃO (GERENCIA).
 *
 * O SLA é definido pelo SISTEMA a partir da prioridade informada
 * por quem abre o chamado. Nenhum usuário (recepcionista/técnico)
 * pode arbitrar o prazo manualmente pelo frontend.
 *
 * Regra do projeto:
 *   URGENTE -> 0 dias (mesmo dia)
 *   ALTA    -> 1 dia
 *   MÉDIA   -> 3 dias
 *   BAIXA   -> 5 dias
 *
 * Compatibilidade: o valor legado "CRITICA" equivale a "URGENTE" (0 dias),
 * para não quebrar chamados históricos que usam CRITICA.
 */
public final class SlaPolicy {

    private SlaPolicy() {
    }

    /**
     * Retorna o SLA em dias para a prioridade informada.
     * Retorna null apenas se a prioridade for nula/vazia (sem condição
     * para calcular); nesse caso o chamador deve preservar o valor existente.
     */
    public static Integer slaDiasParaPrioridade(String prioridade) {
        if (prioridade == null) {
            return null;
        }
        String p = prioridade.trim().toUpperCase().replace("É", "E").replace("Ã", "A");
        switch (p) {
            case "URGENTE":
            case "CRITICA":
                return 0;
            case "ALTA":
                return 1;
            case "MEDIA":
                return 3;
            case "BAIXA":
                return 5;
            default:
                return null;
        }
    }

    /**
     * Prioridade padrão quando nenhuma é informada na abertura.
     */
    public static String prioridadePadrao() {
        return "MEDIA";
    }
}
