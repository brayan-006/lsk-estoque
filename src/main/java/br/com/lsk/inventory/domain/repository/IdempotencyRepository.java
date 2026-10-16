package br.com.lsk.inventory.domain.repository;

/** Registry of processed keys (sale events, NF-e). */
public interface IdempotencyRepository {

    /** Returns true the first time a key is registered in the current transaction, false for a duplicate. */
    boolean register(String key, String type);
}
