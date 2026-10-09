package br.com.lsk.estoque.domain.repository;

/** Registro de chaves já processadas (eventos de venda, NF-e). */
public interface IdempotenciaRepository {

    /**
     * Tenta registrar a chave na transação corrente.
     *
     * @return true se foi a primeira vez; false se a chave já existia (duplicado)
     */
    boolean registrar(String chave, String tipo);
}
