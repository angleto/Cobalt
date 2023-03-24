package it.auties.whatsapp.controller;

import it.auties.whatsapp.api.ClientType;

import java.util.UUID;

/**
 * This interface provides a standardized way to serialize a session. Implement this interface and
 * <a href="https://www.baeldung.com/java-spi#3-service-provider">register it in your manifest</a>
 */
public interface ControllerSerializer {
    /**
     * Serializes the keys
     *
     * @param keys  the non-null keys to serialize
     * @param async whether the operation should be executed asynchronously
     */
    void serializeKeys(Keys keys, boolean async);

    /**
     * Serializes the store
     *
     * @param store the non-null store to serialize
     * @param async whether the operation should be executed asynchronously
     */
    void serializeStore(Store store, boolean async);

    /**
     * Deletes a session
     *
     * @param type the non-null type of client
     * @param id   the id of the session to delete
     */
    void deleteSession(ClientType type, UUID id);
}