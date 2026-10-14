package br.com.lsk.estoque.domain.repository;

/** Registry of processed keys (sale events, NF-e). */
public interface IdempotenciaRepository {

    /** Returns true the first time a key is registered in the current transaction, false for a duplicate. */
    boolean registrar(String chave, String tipo);
}
