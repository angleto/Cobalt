package it.auties.whatsapp.api;

import it.auties.whatsapp.controller.ControllerSerializer;
import it.auties.whatsapp.controller.Keys;
import it.auties.whatsapp.controller.Store;
import it.auties.whatsapp.listener.RegisterListener;
import it.auties.whatsapp.model.signal.auth.Version;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

@SuppressWarnings("unused")
public sealed class OptionsBuilder<T extends OptionsBuilder<T>> permits MobileOptionsBuilder, WebOptionsBuilder {
    protected Store store;
    protected Keys keys;

    public OptionsBuilder(UUID connectionUuid, ControllerSerializer serializer, ConnectionType connectionType, ClientType clientType){
        if(connectionType.equals(ConnectionType.NEW)) {
            var sessionUuid = Objects.requireNonNullElseGet(connectionUuid, UUID::randomUUID);
            this.store = Store.of(sessionUuid, connectionType, clientType, serializer);
            this.keys = Keys.of(sessionUuid, connectionType, clientType, serializer);
        } else {
            this.store = Store.of(connectionUuid, connectionType, clientType, serializer);
            this.keys = Keys.of(connectionUuid, connectionType, clientType, serializer);
        }
    }

    /**
     * Sets the name to provide to Whatsapp during the authentication process
     * The web api will display this name in the devices section, while the mobile api will show it to the people you send messages to
     * By default, this value will be set to this library's name
     *
     * @return the same instance for chaining
     */
    @SuppressWarnings("unchecked")
    public T name(String name) {
        store.name(name);
        return (T) this;
    }

    /**
     * Sets the version of Whatsapp to use
     * If the version is too outdated, the server will refuse to connect
     * If you are using the mobile api and the version doesn't match the hash, the server will refuse to connect
     * By default the latest stable version will be used
     *
     * @return the same instance for chaining
     */
    @SuppressWarnings("unchecked")
    public T version(Version version) {
        store.version(version);
        return (T) this;
    }

    /**
     * Sets whether listeners marked with the {@link RegisterListener} annotation should be automatically detected and registered
     * By default, this option is enabled
     *
     * @return the same instance for chaining
     */
    @SuppressWarnings("unchecked")
    public T autodetectListeners(boolean autodetectListeners) {
        store.autodetectListeners(autodetectListeners);
        return (T) this;
    }

    /**
     * Sets whether a preview should be automatically generated and attached to text messages that contain links
     * By default, it's enabled with inference
     *
     * @return the same instance for chaining
     */
    @SuppressWarnings("unchecked")
    public T textPreviewSetting(TextPreviewSetting textPreviewSetting) {
        store.textPreviewSetting(textPreviewSetting);
        return (T) this;
    }

    /**
     * Sets the error handler for this session
     *
     * @return the same instance for chaining
     */
    @SuppressWarnings("unchecked")
    public T errorHandler(ErrorHandler errorHandler) {
        store.errorHandler(errorHandler);
        return (T) this;
    }


    /**
     * Sets the executor to use for the socket
     *
     * @return the same instance for chaining
     */
    @SuppressWarnings("unchecked")
    public T socketExecutor(Executor socketExecutor) {
        store.socketExecutor(socketExecutor);
        return (T) this;
    }

    /**
     * Sets the proxy to use for the socket
     *
     * @return the same instance for chaining
     */
    @SuppressWarnings("unchecked")
    public T proxy(URI proxy) {
        store.proxy(proxy);
        return (T) this;
    }
}