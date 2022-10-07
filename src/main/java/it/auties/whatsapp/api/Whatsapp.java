package it.auties.whatsapp.api;

import it.auties.bytes.Bytes;
import it.auties.linkpreview.LinkPreview;
import it.auties.linkpreview.LinkPreviewMedia;
import it.auties.linkpreview.LinkPreviewResult;
import it.auties.whatsapp.controller.Keys;
import it.auties.whatsapp.controller.Store;
import it.auties.whatsapp.crypto.AesGmc;
import it.auties.whatsapp.crypto.Hkdf;
import it.auties.whatsapp.listener.*;
import it.auties.whatsapp.model.action.*;
import it.auties.whatsapp.model.business.BusinessCategory;
import it.auties.whatsapp.model.business.BusinessProfile;
import it.auties.whatsapp.model.chat.*;
import it.auties.whatsapp.model.contact.ContactJid;
import it.auties.whatsapp.model.contact.ContactJidProvider;
import it.auties.whatsapp.model.contact.ContactStatus;
import it.auties.whatsapp.model.info.ContextInfo;
import it.auties.whatsapp.model.info.MessageContextInfo;
import it.auties.whatsapp.model.info.MessageInfo;
import it.auties.whatsapp.model.message.button.ButtonsMessage;
import it.auties.whatsapp.model.message.button.ListMessage;
import it.auties.whatsapp.model.message.model.*;
import it.auties.whatsapp.model.message.server.ProtocolMessage;
import it.auties.whatsapp.model.message.standard.GroupInviteMessage;
import it.auties.whatsapp.model.message.standard.ReactionMessage;
import it.auties.whatsapp.model.message.standard.TextMessage;
import it.auties.whatsapp.model.request.Node;
import it.auties.whatsapp.model.request.NodeHandler;
import it.auties.whatsapp.model.request.Reply;
import it.auties.whatsapp.model.response.ContactStatusResponse;
import it.auties.whatsapp.model.response.HasWhatsappResponse;
import it.auties.whatsapp.model.signal.auth.Version;
import it.auties.whatsapp.model.sync.*;
import it.auties.whatsapp.serialization.ControllerProviderLoader;
import it.auties.whatsapp.socket.Socket;
import it.auties.whatsapp.util.*;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NonNull;
import lombok.With;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static it.auties.bytes.Bytes.ofRandom;
import static it.auties.whatsapp.api.Whatsapp.Options.defaultOptions;
import static it.auties.whatsapp.binary.PatchType.REGULAR_HIGH;
import static it.auties.whatsapp.binary.PatchType.REGULAR_LOW;
import static it.auties.whatsapp.model.contact.ContactJid.Server.GROUP;
import static it.auties.whatsapp.model.message.standard.TextMessage.TextMessagePreviewType.NONE;
import static it.auties.whatsapp.model.message.standard.TextMessage.TextMessagePreviewType.VIDEO;
import static it.auties.whatsapp.model.sync.RecordSync.Operation.SET;
import static it.auties.whatsapp.util.JacksonProvider.PROTOBUF;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;

/**
 * A class used to interface a user to WhatsappWeb's WebSocket.
 * It provides various functionalities, including the possibility to query, set and modify data associated with the loaded session of whatsapp.
 * It can be configured using a default configuration or a custom one.
 * Multiple instances of this class can be initialized, though it is not advisable as; is a singleton and cannot distinguish between the data associated with each session.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Whatsapp {
    /**
     * The socket associated with this session
     */
    private final Socket socket;

    private Whatsapp(Options options) {
        this(options, Store.of(options.id(), options.defaultSerialization()),
                Keys.of(options.id(), options.defaultSerialization()));
    }

    private Whatsapp(Options options, Store store, Keys keys) {
        this.socket = new Socket(this, options, store, keys);
        if (!options.autodetectListeners()) {
            return;
        }

        ListenerScanner.scan(this)
                .forEach(this::addListener);
    }

    /**
     * Constructs a new instance of the API.
     * If the id is not associated with any session, a new one will be created.
     *
     * @param id the id of the session
     * @return a non-null Whatsapp instance
     */
    public static Whatsapp newConnection(int id) {
        return newConnection(Options.defaultOptions()
                .withId(id));
    }

    /**
     * Constructs a new instance of the API
     *
     * @return a non-null Whatsapp instance
     */
    public static Whatsapp newConnection() {
        return newConnection(defaultOptions());
    }

    /**
     * Constructs a new instance of the API.
     * If the id is not associated with any session, a new one will be created.
     *
     * @param options the non-null options used to create this session
     * @return a non-null Whatsapp instance
     */
    public static Whatsapp newConnection(@NonNull Options options) {
        return new Whatsapp(options);
    }

    /**
     * Constructs a new instance of the API from a fresh connection using a random id
     *
     * @param options the non-null options used to create this session
     * @param store   the non-null store used to create this session
     * @param keys    the non-null keys used to create this session
     * @return a non-null Whatsapp instance
     */
    public static Whatsapp newConnection(@NonNull Options options, @NonNull Store store, @NonNull Keys keys) {
        return new Whatsapp(options, store, keys);
    }

    /**
     * Constructs a new instance of the API from the first session opened.
     * If no sessions are available, a new one will be created.
     *
     * @return a non-null Whatsapp instance
     */
    public static Whatsapp firstConnection() {
        return firstConnection(defaultOptions());
    }

    /**
     * Constructs a new instance of the API from the first session opened.
     * If no sessions are available, a new one will be created.
     *
     * @param options the non-null options
     * @return a non-null Whatsapp instance
     */
    public static Whatsapp firstConnection(@NonNull Options options) {
        return newConnection(options.withId(requireNonNullElseGet(
                ControllerProviderLoader.findAllIds(options.defaultSerialization())
                        .peekFirst(), KeyHelper::registrationId)));
    }

    /**
     * Constructs a new instance of the API from the last session opened.
     * If no sessions are available, a new one will be created.
     *
     * @return a non-null Whatsapp instance
     */
    public static Whatsapp lastConnection() {
        return lastConnection(defaultOptions());
    }

    /**
     * Constructs a new instance of the API from the last session opened.
     * If no sessions are available, a new one will be created.
     *
     * @param options the non-null options
     * @return a non-null Whatsapp instance
     */
    public static Whatsapp lastConnection(@NonNull Options options) {
        return newConnection(options.withId(requireNonNullElseGet(
                ControllerProviderLoader.findAllIds(options.defaultSerialization())
                        .peekLast(), KeyHelper::registrationId)));
    }

    /**
     * Returns a list of all known connections
     *
     * @return a non-null List
     */
    public static List<Whatsapp> listConnections() {
        return listConnections(defaultOptions());
    }

    /**
     * Returns a list of all known connections
     *
     * @param options the non-null options
     * @return a non-null List
     */
    public static List<Whatsapp> listConnections(@NonNull Options options) {
        return streamConnections(options).toList();
    }

    /**
     * Returns a stream of all known connections
     *
     * @return a non-null Stream
     */
    public static Stream<Whatsapp> streamConnections() {
        return streamConnections(defaultOptions());
    }

    /**
     * Returns a stream of all known connections
     *
     * @param options the non-null options
     * @return a non-null Stream
     */
    public static Stream<Whatsapp> streamConnections(@NonNull Options options) {
        return ControllerProviderLoader.findAllIds(options.defaultSerialization())
                .stream()
                .map(id -> Whatsapp.newConnection(options.withId(id)));
    }


    /**
     * Deletes all the known connections from memory
     */
    public static void deleteConnections() {
        streamConnections().forEach(Whatsapp::delete);
    }

    private static Optional<ContactStatusResponse> parseStatus(List<Node> response) {
        return Nodes.findFirst(response, "status")
                .map(ContactStatusResponse::new);
    }

    private static String parseInviteCode(Node result) {
        return result.findNode("invite")
                .orElseThrow(() -> new NoSuchElementException("Missing invite code in invite response"))
                .attributes()
                .getRequiredString("code");
    }

    private static List<BusinessCategory> parseBusinessCategories(Node result) {
        return result.findNode("response")
                .flatMap(entry -> entry.findNode("categories"))
                .stream()
                .map(entry -> entry.findNodes("category"))
                .flatMap(Collection::stream)
                .map(BusinessCategory::of)
                .toList();
    }

    private static List<Node> createWebsites(List<URI> websites) {
        if (websites == null) {
            return List.of();
        }

        return websites.stream()
                .map(entry -> Node.of("website", entry.toString()
                        .getBytes(StandardCharsets.UTF_8)))
                .toList();
    }

    /**
     * Returns the store associated with this session
     *
     * @return a non-null WhatsappStore
     */
    public Store store() {
        return socket.store();
    }

    /**
     * Returns the keys associated with this session
     *
     * @return a non-null WhatsappKeys
     */
    public Keys keys() {
        return socket.keys();
    }

    /**
     * Registers a listener
     *
     * @param listener the listener to register
     * @return the same instance
     */
    public Whatsapp addListener(@NonNull Listener listener) {
        Validate.isTrue(socket.options()
                .listenersLimit() < 0 || store().listeners()
                .size() + 1 <= socket.options()
                .listenersLimit(), "The number of listeners is too high: expected %s, got %s", socket.options()
                .listenersLimit(), socket.store()
                .listeners()
                .size());
        Validate.isTrue(socket.store()
                .listeners()
                .add(listener), "WhatsappAPI: Cannot add listener %s", listener.getClass()
                .getName());
        return this;
    }

    /**
     * Registers an action listener
     *
     * @param onAction the listener to register
     * @return the same instance
     */
    public Whatsapp addActionListener(OnAction onAction) {
        return addListener(onAction);
    }

    /**
     * Registers a chat recent messages listener
     *
     * @param onChatRecentMessages the listener to register
     * @return the same instance
     */
    public Whatsapp addChatMessagesListener(OnChatMessages onChatRecentMessages) {
        return addListener(onChatRecentMessages);
    }

    /**
     * Registers a chats listener
     *
     * @param onChats the listener to register
     * @return the same instance
     */
    public Whatsapp addChatsListener(OnChats onChats) {
        return addListener(onChats);
    }

    /**
     * Registers a contact presence listener
     *
     * @param onContactPresence the listener to register
     * @return the same instance
     */
    public Whatsapp addContactPresenceListener(OnContactPresence onContactPresence) {
        return addListener(onContactPresence);
    }

    /**
     * Registers a contacts listener
     *
     * @param onContacts the listener to register
     * @return the same instance
     */
    public Whatsapp addContactsListener(OnContacts onContacts) {
        return addListener(onContacts);
    }

    /**
     * Registers a message status listener
     *
     * @param onConversationMessageStatus the listener to register
     * @return the same instance
     */
    public Whatsapp addMessageStatusListener(OnConversationMessageStatus onConversationMessageStatus) {
        return addListener(onConversationMessageStatus);
    }

    /**
     * Registers a message status listener
     *
     * @param onMessageStatus the listener to register
     * @return the same instance
     */
    public Whatsapp addMessageStatusListener(OnMessageStatus onMessageStatus) {
        return addListener(onMessageStatus);
    }

    /**
     * Registers a disconnected listener
     *
     * @param onDisconnected the listener to register
     * @return the same instance
     */
    public Whatsapp addDisconnectedListener(OnDisconnected onDisconnected) {
        return addListener(onDisconnected);
    }

    /**
     * Registers a features listener
     *
     * @param onFeatures the listener to register
     * @return the same instance
     */
    public Whatsapp addFeaturesListener(OnFeatures onFeatures) {
        return addListener(onFeatures);
    }

    /**
     * Registers a logged in listener
     *
     * @param onLoggedIn the listener to register
     * @return the same instance
     */
    public Whatsapp addLoggedInListener(OnLoggedIn onLoggedIn) {
        return addListener(onLoggedIn);
    }

    /**
     * Registers a message deleted listener
     *
     * @param onMessageDeleted the listener to register
     * @return the same instance
     */
    public Whatsapp addMessageDeletedListener(OnMessageDeleted onMessageDeleted) {
        return addListener(onMessageDeleted);
    }

    /**
     * Registers a metadata listener
     *
     * @param onMetadata the listener to register
     * @return the same instance
     */
    public Whatsapp addMetadataListener(OnMetadata onMetadata) {
        return addListener(onMetadata);
    }

    /**
     * Registers a new contact listener
     *
     * @param onNewContact the listener to register
     * @return the same instance
     */
    public Whatsapp addNewContactListener(OnNewContact onNewContact) {
        return addListener(onNewContact);
    }

    /**
     * Registers a new message listener
     *
     * @param onNewMessage the listener to register
     * @return the same instance
     */
    public Whatsapp addNewMessageListener(OnNewMessage onNewMessage) {
        return addListener(onNewMessage);
    }

    /**
     * Registers a new status listener
     *
     * @param onNewStatus the listener to register
     * @return the same instance
     */
    public Whatsapp addNewStatusListener(OnNewStatus onNewStatus) {
        return addListener(onNewStatus);
    }

    /**
     * Registers a received node listener
     *
     * @param onNodeReceived the listener to register
     * @return the same instance
     */
    public Whatsapp addNodeReceivedListener(OnNodeReceived onNodeReceived) {
        return addListener(onNodeReceived);
    }

    /**
     * Registers a sent node listener
     *
     * @param onNodeSent the listener to register
     * @return the same instance
     */
    public Whatsapp addNodeSentListener(OnNodeSent onNodeSent) {
        return addListener(onNodeSent);
    }

    /**
     * Registers a setting listener
     *
     * @param onSetting the listener to register
     * @return the same instance
     */
    public Whatsapp addSettingListener(OnSetting onSetting) {
        return addListener(onSetting);
    }

    /**
     * Registers a status listener
     *
     * @param onStatus the listener to register
     * @return the same instance
     */
    public Whatsapp addStatusListener(OnStatus onStatus) {
        return addListener(onStatus);
    }

    /**
     * Registers an event listener
     *
     * @param onSocketEvent the listener to register
     * @return the same instance
     */
    public Whatsapp addSocketEventListener(OnSocketEvent onSocketEvent) {
        return addListener(onSocketEvent);
    }

    /**
     * Registers an action listener
     *
     * @param onAction the listener to register
     * @return the same instance
     */
    public Whatsapp addActionListener(OnWhatsappAction onAction) {
        return addListener(onAction);
    }

    /**
     * Registers a sync progress listener
     *
     * @param onSyncProgress the listener to register
     * @return the same instance
     */
    public Whatsapp addHistorySyncProgressListener(OnHistorySyncProgress onSyncProgress) {
        return addListener(onSyncProgress);
    }

    /**
     * Registers a chat recent messages listener
     *
     * @param onChatRecentMessages the listener to register
     * @return the same instance
     */
    public Whatsapp addChatMessagesListener(OnWhatsappChatMessagesSync onChatRecentMessages) {
        return addListener(onChatRecentMessages);
    }

    /**
     * Registers a chats listener
     *
     * @param onChats the listener to register
     * @return the same instance
     */
    public Whatsapp addChatsListener(OnChatMessagesSync onChats) {
        return addListener(onChats);
    }

    /**
     * Registers a contact presence listener
     *
     * @param onContactPresence the listener to register
     * @return the same instance
     */
    public Whatsapp addContactPresenceListener(OnWhatsappContactPresence onContactPresence) {
        return addListener(onContactPresence);
    }

    /**
     * Registers a contacts listener
     *
     * @param onContacts the listener to register
     * @return the same instance
     */
    public Whatsapp addContactsListener(OnWhatsappContacts onContacts) {
        return addListener(onContacts);
    }

    /**
     * Registers a message status listener
     *
     * @param onConversationMessageStatus the listener to register
     * @return the same instance
     */
    public Whatsapp addMessageStatusListener(OnWhatsappConversationMessageStatus onConversationMessageStatus) {
        return addListener(onConversationMessageStatus);
    }

    /**
     * Registers a message status listener
     *
     * @param onMessageStatus the listener to register
     * @return the same instance
     */
    public Whatsapp addMessageStatusListener(OnWhatsappMessageStatus onMessageStatus) {
        return addListener(onMessageStatus);
    }

    /**
     * Registers a disconnected listener
     *
     * @param onDisconnected the listener to register
     * @return the same instance
     */
    public Whatsapp addDisconnectedListener(OnWhatsappDisconnected onDisconnected) {
        return addListener(onDisconnected);
    }

    /**
     * Registers a features listener
     *
     * @param onFeatures the listener to register
     * @return the same instance
     */
    public Whatsapp addFeaturesListener(OnWhatsappFeatures onFeatures) {
        return addListener(onFeatures);
    }

    /**
     * Registers a logged in listener
     *
     * @param onLoggedIn the listener to register
     * @return the same instance
     */
    public Whatsapp addLoggedInListener(OnWhatsappLoggedIn onLoggedIn) {
        return addListener(onLoggedIn);
    }

    /**
     * Registers a message deleted listener
     *
     * @param onMessageDeleted the listener to register
     * @return the same instance
     */
    public Whatsapp addMessageDeletedListener(OnWhatsappMessageDeleted onMessageDeleted) {
        return addListener(onMessageDeleted);
    }

    /**
     * Registers a metadata listener
     *
     * @param onMetadata the listener to register
     * @return the same instance
     */
    public Whatsapp addMetadataListener(OnWhatsappMetadata onMetadata) {
        return addListener(onMetadata);
    }

    /**
     * Registers a new contact listener
     *
     * @param onNewContact the listener to register
     * @return the same instance
     */
    public Whatsapp addNewContactListener(OnWhatsappNewContact onNewContact) {
        return addListener(onNewContact);
    }

    /**
     * Registers a new message listener
     *
     * @param onNewMessage the listener to register
     * @return the same instance
     */
    public Whatsapp addNewMessageListener(OnWhatsappNewMessage onNewMessage) {
        return addListener(onNewMessage);
    }

    /**
     * Registers a new status listener
     *
     * @param onNewStatus the listener to register
     * @return the same instance
     */
    public Whatsapp addNewStatusListener(OnWhatsappNewStatus onNewStatus) {
        return addListener(onNewStatus);
    }

    /**
     * Registers a received node listener
     *
     * @param onNodeReceived the listener to register
     * @return the same instance
     */
    public Whatsapp addNodeReceivedListener(OnWhatsappNodeReceived onNodeReceived) {
        return addListener(onNodeReceived);
    }

    /**
     * Registers a sent node listener
     *
     * @param onNodeSent the listener to register
     * @return the same instance
     */
    public Whatsapp addNodeSentListener(OnWhatsappNodeSent onNodeSent) {
        return addListener(onNodeSent);
    }

    /**
     * Registers a setting listener
     *
     * @param onSetting the listener to register
     * @return the same instance
     */
    public Whatsapp addSettingListener(OnWhatsappSetting onSetting) {
        return addListener(onSetting);
    }

    /**
     * Registers a status listener
     *
     * @param onStatus the listener to register
     * @return the same instance
     */
    public Whatsapp addStatusListener(OnWhatsappStatus onStatus) {
        return addListener(onStatus);
    }

    /**
     * Registers an event listener
     *
     * @param onSocketEvent the listener to register
     * @return the same instance
     */
    public Whatsapp addSocketEventListener(OnWhatsappSocketEvent onSocketEvent) {
        return addListener(onSocketEvent);
    }

    /**
     * Registers a sync progress listener
     *
     * @param onSyncProgress the listener to register
     * @return the same instance
     */
    public Whatsapp addHistorySyncProgressListener(OnWhatsappHistorySyncProgress onSyncProgress) {
        return addListener(onSyncProgress);
    }

    /**
     * Registers a message reply listener
     *
     * @param onMessageReply the listener to register
     * @return the same instance
     */
    public Whatsapp addMessageReplyListener(OnMessageReply onMessageReply) {
        return addListener(onMessageReply);
    }

    /**
     * Registers a message reply listener
     *
     * @param onMessageReply the listener to register
     * @return the same instance
     */
    public Whatsapp addMessageReplyListener(OnWhatsappMessageReply onMessageReply) {
        return addListener(onMessageReply);
    }

    /**
     * Removes a listener
     *
     * @param listener the listener to remove
     * @return the same instance
     */
    public Whatsapp removeListener(@NonNull Listener listener) {
        Validate.isTrue(socket.store()
                .listeners()
                .remove(listener), "WhatsappAPI: Cannot remove listener %s", listener.getClass()
                .getName());
        return this;
    }

    /**
     * Waits for the socket to be closed on the current thread
     *
     * @return the same instance
     */
    public Whatsapp await() {
        socket.await();
        return this;
    }

    /**
     * Opens a connection with Whatsapp Web's WebSocket if a previous connection doesn't exist
     *
     * @return the same instance wrapped in a completable future
     */
    public CompletableFuture<Whatsapp> connect() {
        return socket.connect()
                .thenApplyAsync(ignored -> this);
    }

    /**
     * Disconnects from Whatsapp Web's WebSocket if a previous connection exists
     *
     * @return the same instance wrapped in a completable future
     */
    public CompletableFuture<Whatsapp> disconnect() {
        return socket.disconnect(false)
                .thenApplyAsync(ignored -> this);
    }

    /**
     * Deletes the data associated with this session and disconnects from it
     *
     * @return the same instance wrapped in a completable future
     */
    public CompletableFuture<Whatsapp> delete() {
        LocalSystem.delete(String.valueOf(keys().id()));
        return disconnect();
    }

    /**
     * Disconnects and reconnects to Whatsapp Web's WebSocket if a previous connection exists
     *
     * @return the same instance wrapped in a completable future
     */
    public CompletableFuture<Whatsapp> reconnect() {
        return socket.disconnect(true)
                .thenApplyAsync(ignored -> this);
    }

    /**
     * Disconnects from Whatsapp Web's WebSocket and logs out of WhatsappWeb invalidating the previous saved credentials.
     * The next time the API is used, the QR code will need to be scanned again.
     *
     * @return the same instance wrapped in a completable future
     */
    public CompletableFuture<Whatsapp> logout() {
        if (keys().hasCompanion()) {
            var metadata = Map.of("jid", keys().companion(), "reason", "user_initiated");
            var device = Node.ofAttributes("remove-companion-device", metadata);
            return socket.sendQuery("set", "md", device)
                    .thenRunAsync(socket::changeKeys)
                    .thenApplyAsync(ignored -> this);
        }

        return disconnect().thenRunAsync(socket::changeKeys)
                .thenApplyAsync(ignored -> this);
    }

    /**
     * Sends a request to Whatsapp in order to receive updates when the status of a contact changes.
     * These changes include the last known presence and the seconds the contact was last seen.
     *
     * @param jid the contact whose status the api should receive updates on
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> subscribeToPresence(@NonNull T jid) {
        var node = Node.ofAttributes("presence", Map.of("to", jid.toJid(), "type", "subscribe"));
        return socket.sendWithNoResponse(node)
                .thenApplyAsync(ignored -> jid);
    }

    /**
     * Remove a reaction from a message
     *
     * @param message the non-null message
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> removeReaction(@NonNull MessageMetadataProvider message) {
        return sendReaction(message, null);
    }

    /**
     * Send a reaction to a message
     *
     * @param message  the non-null message
     * @param reaction the reaction to send, null if you want to remove the reaction
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> sendReaction(@NonNull MessageMetadataProvider message, String reaction) {
        var key = MessageKey.newMessageKeyBuilder()
                .chatJid(message.chat()
                        .jid())
                .id(message.id())
                .build();
        var reactionMessage = ReactionMessage.newReactionMessageBuilder()
                .key(key)
                .content(reaction)
                .timestamp(Instant.now()
                        .toEpochMilli())
                .build();
        return sendMessage(message.chat(), reactionMessage);
    }

    /**
     * Builds and sends a message from a chat and a message
     *
     * @param chat    the chat where the message should be sent
     * @param message the message to send
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> sendMessage(@NonNull ContactJidProvider chat, @NonNull String message) {
        return sendMessage(chat, MessageContainer.of(message));
    }

    /**
     * Builds and sends a message from a chat, a message and a quoted message
     *
     * @param chat          the chat where the message should be sent
     * @param message       the message to send
     * @param quotedMessage the quoted message
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> sendMessage(@NonNull ContactJidProvider chat, @NonNull String message,
                                                      @NonNull MessageMetadataProvider quotedMessage) {
        return sendMessage(chat, TextMessage.of(message), quotedMessage);
    }

    /**
     * Builds and sends a message from a chat, a message and a quoted message
     *
     * @param chat          the chat where the message should be sent
     * @param message       the message to send
     * @param quotedMessage the quoted message
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> sendMessage(@NonNull ContactJidProvider chat,
                                                      @NonNull ContextualMessage message,
                                                      @NonNull MessageMetadataProvider quotedMessage) {
        Validate.isTrue(!quotedMessage.message()
                .isEmpty(), "Cannot quote an empty message");
        Validate.isTrue(!quotedMessage.message()
                .hasCategory(MessageCategory.SERVER), "Cannot quote a server message");
        return sendMessage(chat, message, ContextInfo.of(quotedMessage));
    }

    /**
     * Builds and sends a message from a chat, a message and a context
     *
     * @param chat        the chat where the message should be sent
     * @param message     the message to send
     * @param contextInfo the context of the message to send
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> sendMessage(@NonNull ContactJidProvider chat,
                                                      @NonNull ContextualMessage message,
                                                      @NonNull ContextInfo contextInfo) {
        message.contextInfo(contextInfo);
        return sendMessage(chat, message);
    }

    /**
     * Builds and sends a message from a chat and a message
     *
     * @param chat    the chat where the message should be sent
     * @param message the message to send
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> sendMessage(@NonNull ContactJidProvider chat, @NonNull Message message) {
        return sendMessage(chat, MessageContainer.of(message));
    }

    /**
     * Builds and sends a message from a chat and a message
     *
     * @param chat    the chat where the message should be sent
     * @param message the message to send
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> sendMessage(@NonNull ContactJidProvider chat,
                                                      @NonNull MessageContainer message) {
        var key = MessageKey.newMessageKeyBuilder()
                .chatJid(chat.toJid())
                .fromMe(true)
                .senderJid(chat.toJid()
                        .hasServer(GROUP) ?
                        keys().companion() :
                        null)
                .build();
        var info = MessageInfo.newMessageInfo()
                .senderJid(chat.toJid()
                        .hasServer(GROUP) ?
                        keys().companion() :
                        null)
                .key(key)
                .message(message)
                .timestamp(Clock.now())
                .build();
        return sendMessage(info);
    }

    /**
     * Sends a message info to a chat
     *
     * @param info the info to send
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> sendMessage(@NonNull MessageInfo info) {
        store().attribute(info);
        info.key()
                .chatJid(info.chatJid()
                        .toUserJid());
        info.key()
                .senderJid(info.senderJid() == null ?
                        null :
                        info.senderJid()
                                .toUserJid());
        createPreview(info);
        parseEphemeralMessage(info);
        fixButtons(info);

        var future = info.chat().hasUnreadMessages() ?
                markRead(info.chat()).thenComposeAsync(ignored -> socket.sendMessage(info)) :
                socket.sendMessage(info);
        return future.thenApplyAsync(ignored -> info);
    }

    /**
     * Awaits for a single response to a message
     *
     * @param info the non-null message whose response is pending
     * @return a non-null future
     */
    public CompletableFuture<MessageInfo> awaitReply(@NonNull MessageInfo info){
        return awaitReply(info.id());
    }

    /**
     * Awaits for a single response to a message
     *
     * @param id the non-null id of message whose response is pending
     * @return a non-null future
     */
    public CompletableFuture<MessageInfo> awaitReply(@NonNull String id){
        return store().addPendingReply(Reply.Single.of(id));
    }

    /**
     * Awaits for a multiple responses to a message
     *
     * @param info the non-null message whose response is pending
     * @param consumer the non-null reply consumer
     */
    public void awaitReplies(@NonNull MessageInfo info, @NonNull Consumer<MessageInfo> consumer){
        awaitReplies(info.id(), consumer);
    }

    /**
     * Awaits for a multiple responses to a message
     *
     * @param id the non-null id of the message whose response is pending
     * @param consumer the non-null reply consumer
     */
    public void awaitReplies(@NonNull String id, @NonNull Consumer<MessageInfo> consumer){
        store().addPendingReply(Reply.Multi.of(id, consumer));
    }

    // Credit to Baileys
    // https://github.com/adiwajshing/Baileys/blob/f0bdb12e56cea8b0bfbb0dff37c01690274e3e31/src/Utils/messages.ts#L781
    private void fixButtons(MessageInfo info){
        if(!(info.message().content() instanceof ButtonsMessage) && !(info.message().content() instanceof ListMessage)){
            return;
        }
        
        var context = MessageContextInfo.builder()
                .deviceListMetadataVersion(2)
                .deviceListMetadata(DeviceListMetadata.of())
                .build();
        info.message(info.message().toViewOnce());
        info.message().deviceInfo(context);
    }

    private void createPreview(MessageInfo info) {
        switch (info.message().content()){
            case TextMessage textMessage -> {
                if (socket.options().textPreviewSetting() == TextPreviewSetting.DISABLED) {
                    return;
                }

                var match = LinkPreview.createPreview(textMessage.text())
                        .orElse(null);
                if (match == null) {
                    return;
                }

                if(socket.options().textPreviewSetting() == TextPreviewSetting.ENABLED_WITH_INFERENCE && !match.text().equals(match.result().uri().toString())){
                    var parsed = textMessage.text().replace(match.text(), match.result().uri().toString());
                    textMessage.text(parsed);
                }

                var imageUri = match.result()
                        .images()
                        .stream()
                        .reduce(Whatsapp::compareDimensions)
                        .map(LinkPreviewMedia::uri)
                        .orElse(null);
                var videoUri = match.result()
                        .videos()
                        .stream()
                        .reduce(Whatsapp::compareDimensions)
                        .map(LinkPreviewMedia::uri)
                        .orElse(null);
                textMessage.canonicalUrl(Objects.requireNonNullElse(videoUri, match.result().uri()).toString());
                textMessage.matchedText(match.result().uri().toString());
                textMessage.thumbnail(Medias.getPreview(imageUri).orElse(null));
                textMessage.description(match.result().siteDescription());
                textMessage.title(match.result().title());
                textMessage.previewType(videoUri != null ? VIDEO : NONE);
            }

            case GroupInviteMessage inviteMessage -> {
                if(!(info.message().content() instanceof GroupInviteMessage invite)){
                    return;
                }

                // This is not needed probably, but Whatsapp uses a text message by default so maybe it makes sense
                Validate.isTrue(invite.code() != null, "Invalid message code");
                var url = "https://chat.whatsapp.com/%s".formatted(invite.code());
                var preview = LinkPreview.createPreview(URI.create(url))
                        .stream()
                        .map(LinkPreviewResult::images)
                        .map(Collection::stream)
                        .map(Stream::findFirst)
                        .flatMap(Optional::stream)
                        .findFirst()
                        .map(LinkPreviewMedia::uri)
                        .orElse(null);
                var replacement = TextMessage.newTextMessageBuilder()
                        .text(invite.caption() != null ? "%s: %s".formatted(invite.caption(), url) : url)
                        .description("WhatsApp Group Invite")
                        .title(invite.groupName())
                        .previewType(NONE)
                        .thumbnail(readGroupThumbnail(preview))
                        .matchedText(url)
                        .canonicalUrl(url)
                        .build();
                info.message(MessageContainer.of(replacement));
            }

            default -> {}
        }
    }

    private static LinkPreviewMedia compareDimensions(LinkPreviewMedia first, LinkPreviewMedia second) {
        return first.width() * first.height() > second.width() * second.height() ?
                first :
                second;
    }

    public byte[] readGroupThumbnail(URI preview){
        try {
            if(preview == null){
                return null;
            }

            return preview.toURL().openConnection().getInputStream().readAllBytes();
        }catch (Throwable throwable){
            return null;
        }
    }

    private void parseEphemeralMessage(MessageInfo info) {
        if (info.message().hasCategory(MessageCategory.SERVER)) {
            return;
        }

        if (!info.chat()
                .isEphemeral()) {
            return;
        }

        createEphemeralMessage(info);
    }

    private void createEphemeralMessage(MessageInfo info) {
        info.message()
                .contentWithContext()
                .map(ContextualMessage::contextInfo)
                .ifPresent(contextInfo -> createEphemeralContext(info.chat(), contextInfo));
        info.message(info.message()
                .toEphemeral());
    }

    private void createEphemeralContext(Chat chat, ContextInfo contextInfo) {
        var period = chat.ephemeralMessageDuration()
                .period()
                .toSeconds();
        contextInfo.ephemeralExpiration((int) period);
    }

    /**
     * Executes a query to determine whether any number of users have an account on Whatsapp
     *
     * @param chats the users to check
     * @return a CompletableFuture that wraps a non-null list of HasWhatsappResponse
     */
    public CompletableFuture<List<HasWhatsappResponse>> hasWhatsapp(@NonNull ContactJidProvider @NonNull ... chats) {
        var contactNodes = Arrays.stream(chats)
                .map(jid -> Node.of("contact", "+%s".formatted(jid.toJid()
                        .user())))
                .toArray(Node[]::new);
        return socket.sendInteractiveQuery(Node.of("contact"), Node.ofChildren("user", contactNodes))
                .thenApplyAsync(nodes -> nodes.stream()
                        .map(HasWhatsappResponse::new)
                        .toList());
    }

    /**
     * Queries the block list
     *
     * @return a CompletableFuture that wraps a non-null list of ContactJid
     */
    public CompletableFuture<List<ContactJid>> queryBlockList() {
        return socket.sendQuery("get", "blocklist", (Node) null)
                .thenApplyAsync(this::parseBlockList);
    }

    private List<ContactJid> parseBlockList(Node result) {
        return result.findNode("list")
                .orElseThrow(() -> new NoSuchElementException("Missing block list in response"))
                .findNodes("item")
                .stream()
                .map(item -> item.attributes()
                        .getJid("jid"))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Queries the written whatsapp status of a Contact
     *
     * @param chat the target contact
     * @return a CompletableFuture that wraps an optional contact status response
     */
    public CompletableFuture<Optional<ContactStatusResponse>> queryStatus(@NonNull ContactJidProvider chat) {
        var query = Node.of("status");
        var body = Node.ofAttributes("user", Map.of("jid", chat.toJid()));
        return socket.sendInteractiveQuery(query, body)
                .thenApplyAsync(Whatsapp::parseStatus);
    }

    /**
     * Queries the profile picture
     *
     * @param chat the chat of the chat to query
     * @return a CompletableFuture that wraps nullable jpg url hosted on Whatsapp's servers
     */
    public CompletableFuture<Optional<URI>> queryPicture(@NonNull ContactJidProvider chat) {
        var body = Node.ofAttributes("picture", Map.of("query", "url"));
        return socket.sendQuery("get", "w:profile:picture", Map.of("target", chat.toJid()), body)
                .thenApplyAsync(this::parseChatPicture);
    }

    private Optional<URI> parseChatPicture(Node result) {
        return result.findNode("picture")
                .flatMap(picture -> picture.attributes()
                        .getOptionalString("url"))
                .map(URI::create);
    }

    /**
     * Queries the metadata of a group
     *
     * @param chat the target group
     * @return a CompletableFuture
     */
    public CompletableFuture<GroupMetadata> queryGroupMetadata(@NonNull ContactJidProvider chat) {
        return socket.queryGroupMetadata(chat.toJid());
    }

    /**
     * Queries a business profile, if any exists
     *
     * @param contact the target contact
     * @return a CompletableFuture
     */
    public CompletableFuture<Optional<BusinessProfile>> queryBusinessProfile(@NonNull ContactJidProvider contact) {
        return socket.sendQuery("get", "w:biz",
                        Node.ofChildren("business_profile", Node.of("v", "116"), Node.ofAttributes("profile", Map.of("jid", contact.toJid()))))
                .thenApplyAsync(this::getBusinessProfile);
    }

    private Optional<BusinessProfile> getBusinessProfile(Node result) {
        return result.findNode("business_profile")
                .flatMap(entry -> entry.findNode("profile"))
                .map(BusinessProfile::of);
    }

    /**
     * Queries all the known business categories
     *
     * @return a CompletableFuture
     */
    public CompletableFuture<List<BusinessCategory>> queryBusinessCategories() {
        return socket.sendQuery("get", "fb:thrift_iq",
                        Node.of("request", Map.of("op", "profile_typeahead", "type", "catkit", "v", "1"),
                                Node.ofChildren("query", List.of())))
                .thenApplyAsync(Whatsapp::parseBusinessCategories);
    }

    /**
     * Queries the invite code of a group
     *
     * @param chat the target group
     * @return a CompletableFuture
     */
    public CompletableFuture<String> queryGroupInviteCode(@NonNull ContactJidProvider chat) {
        return socket.sendQuery(chat.toJid(), "get", "w:g2", Node.of("invite"))
                .thenApplyAsync(Whatsapp::parseInviteCode);
    }

    /**
     * Revokes the invite code of a group
     *
     * @param chat the target group
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> revokeGroupInvite(@NonNull T chat) {
        return socket.sendQuery(chat.toJid(), "set", "w:g2", Node.of("invite"))
                .thenApplyAsync(ignored -> chat);
    }

    /**
     * Accepts the invite for a group
     *
     * @param inviteCode the invite code
     * @return a CompletableFuture
     */
    public CompletableFuture<Optional<Chat>> acceptGroupInvite(@NonNull String inviteCode) {
        return socket.sendQuery(ContactJid.GROUP, "set", "w:g2", Node.ofAttributes("invite",
                        Map.of("code", inviteCode)))
                .thenApplyAsync(this::parseAcceptInvite);
    }

    private Optional<Chat> parseAcceptInvite(Node result) {
        return result.findNode("group")
                .flatMap(group -> group.attributes()
                        .getJid("jid"))
                .map(jid -> store().findChatByJid(jid)
                        .orElseGet(() -> socket.createChat(jid)));
    }

    /**
     * Changes your presence for everyone on Whatsapp
     *
     * @param available whether you are online or not
     * @return a CompletableFuture
     */
    public CompletableFuture<Boolean> changePresence(boolean available) {
        var presence = available ?
                ContactStatus.AVAILABLE :
                ContactStatus.UNAVAILABLE;
        var node = Node.ofAttributes("presence", Map.of("type", presence.data()));
        return socket.sendWithNoResponse(node)
                .thenApplyAsync(ignored -> available);
    }

    /**
     * Changes your presence for a specific chat
     *
     * @param chat     the target chat
     * @param presence the new status
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> changePresence(@NonNull T chat,
                                                                              @NonNull ContactStatus presence) {
        var node = Node.ofAttributes("presence", Map.of("to", chat.toJid(), "type", presence.data()));
        return socket.sendWithNoResponse(node)
                .thenApplyAsync(ignored -> chat);
    }

    /**
     * Promotes any number of contacts to admin in a group
     *
     * @param group    the target group
     * @param contacts the target contacts
     * @return a CompletableFuture
     */
    public CompletableFuture<List<ContactJid>> promote(@NonNull ContactJidProvider group,
                                                       @NonNull ContactJidProvider @NonNull ... contacts) {
        return executeActionOnGroupParticipant(group, GroupAction.PROMOTE, contacts);
    }

    /**
     * Demotes any number of contacts to admin in a group
     *
     * @param group    the target group
     * @param contacts the target contacts
     * @return a CompletableFuture
     */
    public CompletableFuture<List<ContactJid>> demote(@NonNull ContactJidProvider group,
                                                      @NonNull ContactJidProvider @NonNull ... contacts) {
        return executeActionOnGroupParticipant(group, GroupAction.DEMOTE, contacts);
    }

    /**
     * Adds any number of contacts to a group
     *
     * @param group    the target group
     * @param contacts the target contact/s
     * @return a CompletableFuture
     */
    public CompletableFuture<List<ContactJid>> addGroupParticipant(@NonNull ContactJidProvider group,
                                                                   @NonNull ContactJidProvider @NonNull ... contacts) {
        return executeActionOnGroupParticipant(group, GroupAction.ADD, contacts);
    }

    /**
     * Removes any number of contacts from group
     *
     * @param group    the target group
     * @param contacts the target contact/s
     * @return a CompletableFuture
     */
    public CompletableFuture<List<ContactJid>> removeGroupParticipant(@NonNull ContactJidProvider group,
                                                                      @NonNull ContactJidProvider @NonNull ... contacts) {
        return executeActionOnGroupParticipant(group, GroupAction.REMOVE, contacts);
    }

    private CompletableFuture<List<ContactJid>> executeActionOnGroupParticipant(ContactJidProvider group,
                                                                                GroupAction action,
                                                                                ContactJidProvider... jids) {
        var body = Arrays.stream(jids)
                .map(ContactJidProvider::toJid)
                .map(jid -> Node.ofAttributes("participant", Map.of("jid", checkGroupParticipantJid(jid))))
                .map(innerBody -> Node.ofChildren(action.data(), innerBody))
                .toArray(Node[]::new);
        return socket.sendQuery(group.toJid(), "set", "w:g2", body)
                .thenApplyAsync(result -> parseGroupActionResponse(result, action));
    }

    private List<ContactJid> parseGroupActionResponse(Node result, GroupAction action) {
        return result.findNode(action.data())
                .orElseThrow(() -> new NoSuchElementException("An erroneous group operation was executed"))
                .findNodes("participant")
                .stream()
                .filter(participant -> !participant.attributes()
                        .hasKey("error"))
                .map(participant -> participant.attributes()
                        .getJid("jid"))
                .flatMap(Optional::stream)
                .toList();
    }

    private ContactJid checkGroupParticipantJid(ContactJid jid) {
        Validate.isTrue(!Objects.equals(jid.toUserJid(), keys().companion()
                .toUserJid()), "Cannot execute action on yourself");
        return jid;
    }

    /**
     * Changes the name of a group
     *
     * @param group   the target group
     * @param newName the new name for the group
     * @return a CompletableFuture
     * @throws IllegalArgumentException if the provided new name is empty or blank
     */
    public <T extends ContactJidProvider> CompletableFuture<T> changeGroupSubject(@NonNull T group,
                                                                                  @NonNull String newName) {
        var body = Node.of("subject", newName.getBytes(StandardCharsets.UTF_8));
        return socket.sendQuery(group.toJid(), "set", "w:g2", body)
                .thenApplyAsync(ignored -> group);
    }

    /**
     * Changes the description of a group
     *
     * @param group       the target group
     * @param description the new name for the group, can be null if you want to remove it
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> changeGroupDescription(@NonNull T group,
                                                                                      String description) {
        return socket.queryGroupMetadata(group.toJid())
                .thenApplyAsync(GroupMetadata::descriptionId)
                .thenComposeAsync(descriptionId -> changeGroupDescription(group, description, descriptionId))
                .thenApplyAsync(ignored -> group);
    }

    private CompletableFuture<Node> changeGroupDescription(ContactJidProvider group, String description,
                                                           String descriptionId) {
        var descriptionNode = Optional.ofNullable(description)
                .map(content -> Node.of("body", content.getBytes(StandardCharsets.UTF_8)))
                .orElse(null);
        var attributes = Attributes.empty()
                .put("id", MessageKey.randomId(), () -> description != null)
                .put("delete", true, () -> description == null)
                .put("prev", descriptionId, () -> descriptionId != null)
                .map();
        var body = Node.ofChildren("description", attributes, descriptionNode);
        return socket.sendQuery(group.toJid(), "set", "w:g2", body);
    }

    /**
     * Changes which category of users can send messages in a group
     *
     * @param group  the target group
     * @param policy the new policy to enforce
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> changeWhoCanSendMessages(@NonNull T group,
                                                                                        @NonNull GroupPolicy policy) {
        var body = Node.of(policy != GroupPolicy.ANYONE ?
                "not_announcement" :
                "announcement");
        return socket.sendQuery(group.toJid(), "set", "w:g2", body)
                .thenApplyAsync(ignored -> group);
    }

    /**
     * Changes which category of users can edit the group's settings
     *
     * @param group  the target group
     * @param policy the new policy to enforce
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> changeWhoCanEditInfo(@NonNull T group,
                                                                                    @NonNull GroupPolicy policy) {
        var body = Node.of(policy != GroupPolicy.ANYONE ?
                "locked" :
                "unlocked");
        return socket.sendQuery(group.toJid(), "set", "w:g2", body)
                .thenApplyAsync(ignored -> group);
    }

    /**
     * Changes the profile picture of yourself
     *
     * @param image the new image, can be null if you want to remove it
     * @return a CompletableFuture
     */
    public CompletableFuture<ContactJid> changeProfilePicture(byte[] image) {
        return changeGroupPicture(keys().companion(), image);
    }

    /**
     * Changes the picture of a group
     *
     * @param group the target group
     * @param image the new image, can be null if you want to remove it
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> changeGroupPicture(@NonNull T group, byte[] image) {
        var profilePic = image != null ?
                Medias.getProfilePic(image) :
                null;
        var body = Node.of("picture", Map.of("type", "image"), profilePic);
        return socket.sendQuery(group.toJid()
                        .toUserJid(), "set", "w:profile:picture", body)
                .thenApplyAsync(ignored -> group);
    }

    /**
     * Creates a new group with the provided name and with at least one contact
     *
     * @param subject  the new group's name
     * @param contacts at least one contact to add to the group
     * @return a CompletableFuture
     */
    public CompletableFuture<GroupMetadata> createGroup(@NonNull String subject,
                                                        @NonNull ContactJidProvider... contacts) {
        var participants = Arrays.stream(contacts)
                .map(contact -> Node.ofAttributes("participant", Map.of("jid", contact.toJid())))
                .toArray(Node[]::new);
        var key = ofRandom(12).toHex();
        var body = Node.ofChildren("create", Map.of("subject", subject, "key", key), participants);
        return socket.sendQuery(ContactJid.GROUP, "set", "w:g2", body)
                .thenApplyAsync(response -> Optional.ofNullable(response)
                        .flatMap(node -> node.findNode("group"))
                        .orElseThrow(() -> new NoSuchElementException(
                                "Missing group response, something went wrong: %s".formatted(findErrorNode(response)))))
                .thenApplyAsync(GroupMetadata::of);
    }

    /**
     * Leaves a group
     *
     * @param group the target group
     * @throws IllegalArgumentException if the provided chat is not a group
     */
    public <T extends ContactJidProvider> CompletableFuture<T> leaveGroup(@NonNull T group) {
        var body = Node.ofChildren("leave", Node.ofAttributes("group", Map.of("id", group.toJid())));
        return socket.sendQuery(ContactJid.GROUP, "set", "w:g2", body)
                .thenApplyAsync(ignored -> group);
    }

    /**
     * Mutes a chat indefinitely
     *
     * @param chat the target chat
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> mute(@NonNull T chat) {
        return mute(chat, ChatMute.muted());
    }

    /**
     * Mutes a chat
     *
     * @param chat the target chat
     * @param mute the type of mute
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> mute(@NonNull T chat, @NonNull ChatMute mute) {
        var muteAction = MuteAction.of(true, mute.type() == ChatMute.Type.MUTED_FOR_TIMEFRAME ?
                mute.endTimeStamp() * 1000L :
                mute.endTimeStamp());
        var syncAction = ActionValueSync.of(muteAction);
        var request = PatchRequest.of(REGULAR_HIGH, syncAction, SET, 2, chat.toJid()
                .toString());
        return socket.pushPatch(request)
                .thenApplyAsync(ignored -> chat);
    }

    /**
     * Unmutes a chat
     *
     * @param chat the target chat
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> unmute(@NonNull T chat) {
        var muteAction = MuteAction.of(false, null);
        var syncAction = ActionValueSync.of(muteAction);
        var request = PatchRequest.of(REGULAR_HIGH, syncAction, SET, 2, chat.toJid()
                .toString());
        return socket.pushPatch(request)
                .thenApplyAsync(ignored -> chat);
    }

    /**
     * Blocks a contact
     *
     * @param chat the target chat
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> block(@NonNull T chat) {
        var body = Node.ofAttributes("item", Map.of("action", "block", "jid", chat.toJid()));
        return socket.sendQuery("set", "blocklist", body)
                .thenApplyAsync(ignored -> chat);
    }

    /**
     * Unblocks a contact
     *
     * @param chat the target chat
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> unblock(@NonNull T chat) {
        var body = Node.ofAttributes("item", Map.of("action", "unblock", "jid", chat.toJid()));
        return socket.sendQuery("set", "blocklist", body)
                .thenApplyAsync(ignored -> chat);
    }

    /**
     * Enables ephemeral messages in a chat, this means that messages will be automatically cancelled in said chat after a week
     *
     * @param chat the target chat
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> changeEphemeralTimer(@NonNull T chat,
                                                                                    @NonNull ChatEphemeralTimer timer) {
        return switch (chat.toJid()
                .server()) {
            case USER, WHATSAPP -> {
                var message = ProtocolMessage.newProtocolMessageBuilder()
                        .protocolType(ProtocolMessage.ProtocolMessageType.EPHEMERAL_SETTING)
                        .ephemeralExpiration(timer.period()
                                .toSeconds())
                        .build();
                yield sendMessage(chat, message).thenApplyAsync(ignored -> chat);
            }

            case GROUP -> {
                var body = timer == ChatEphemeralTimer.OFF ?
                        Node.of("not_ephemeral") :
                        Node.ofAttributes("ephemeral", Map.of("expiration", timer.period()
                                .toSeconds()));
                yield socket.sendQuery(chat.toJid(), "set", "w:g2", body)
                        .thenApplyAsync(ignored -> chat);
            }

            default -> throw new IllegalArgumentException(
                    "Unexpected chat %s: ephemeral messages are only supported for conversations and groups".formatted(
                            chat.toJid()));
        };
    }

    /**
     * Marks a chat as read
     *
     * @param chat the target chat
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> markRead(@NonNull T chat) {
        return mark(chat, true);
    }

    /**
     * Marks a chat as unread
     *
     * @param chat the target chat
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> markUnread(@NonNull T chat) {
        return mark(chat, false);
    }

    private <T extends ContactJidProvider> CompletableFuture<T> mark(@NonNull T chat, boolean read) {
        var range = createRange(chat, false);
        var markAction = MarkChatAsReadAction.of(read, range);
        var syncAction = ActionValueSync.of(markAction);
        var request = PatchRequest.of(REGULAR_LOW, syncAction, SET, 3, chat.toJid()
                .toString());
        return socket.pushPatch(request)
                .thenApplyAsync(ignored -> chat);
    }

    /**
     * Pins a chat to the top.
     * A maximum of three chats can be pinned to the top.
     * This condition can be checked using;.
     *
     * @param chat the target chat
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> pin(@NonNull T chat) {
        return pin(chat, true);
    }

    /**
     * Unpins a chat from the top
     *
     * @param chat the target chat
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> unpin(@NonNull T chat) {
        return pin(chat, false);
    }

    private <T extends ContactJidProvider> CompletableFuture<T> pin(T chat, boolean pin) {
        var pinAction = PinAction.of(pin);
        var syncAction = ActionValueSync.of(pinAction);
        var request = PatchRequest.of(REGULAR_LOW, syncAction, SET, 5, chat.toJid()
                .toString());
        return socket.pushPatch(request)
                .thenApplyAsync(ignored -> chat);
    }

    /**
     * Stars a message
     *
     * @param info the target message
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> star(@NonNull MessageInfo info) {
        return star(info, true);
    }

    /**
     * Removes star from a message
     *
     * @param info the target message
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> unstar(@NonNull MessageInfo info) {
        return star(info, false);
    }

    private CompletableFuture<MessageInfo> star(MessageInfo info, boolean star) {
        var starAction = StarAction.of(star);
        var syncAction = ActionValueSync.of(starAction);
        var request = PatchRequest.of(REGULAR_HIGH, syncAction, SET, 3, info.chatJid()
                .toString(), info.id(), fromMeToFlag(info), participantToFlag(info));
        return socket.pushPatch(request)
                .thenApplyAsync(ignored -> info);
    }

    /**
     * Archives a chat.
     * If said chat is pinned, it will be unpinned.
     *
     * @param chat the target chat
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> archive(@NonNull T chat) {
        return archive(chat, true);
    }

    /**
     * Unarchives a chat
     *
     * @param chat the target chat
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> unarchive(@NonNull T chat) {
        return archive(chat, false);
    }

    private <T extends ContactJidProvider> CompletableFuture<T> archive(T chat, boolean archive) {
        var range = createRange(chat, false);
        var archiveAction = ArchiveChatAction.of(archive, range);
        var syncAction = ActionValueSync.of(archiveAction);
        var request = PatchRequest.of(REGULAR_LOW, syncAction, SET, 3, chat.toJid()
                .toString());
        return socket.pushPatch(request)
                .thenApplyAsync(ignored -> chat);
    }

    /**
     * Deletes a message
     *
     * @param info     the non-null message to delete
     * @param everyone whether the message should be deleted for everyone or only for this client and its companions
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> delete(@NonNull MessageInfo info, boolean everyone) {
        if (everyone) {
            var message = ProtocolMessage.newProtocolMessageBuilder()
                    .protocolType(ProtocolMessage.ProtocolMessageType.REVOKE)
                    .key(info.key())
                    .build();
            return sendMessage(info.chatJid(), message);
        }

        var range = createRange(info.chatJid(), false);
        var deleteMessageAction = DeleteMessageForMeAction.of(false, info.timestamp());
        var syncAction = ActionValueSync.of(deleteMessageAction);
        var request = PatchRequest.of(REGULAR_HIGH, syncAction, SET, 3, info.chatJid()
                .toString(), info.id(), fromMeToFlag(info), participantToFlag(info));
        return socket.pushPatch(request)
                .thenApplyAsync(ignored -> info);
    }

    /**
     * Deletes a chat for this client and its companions using a modern version of Whatsapp
     * Important: this message doesn't seem to work always as of now
     *
     * @param chat the non-null chat to delete
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> delete(@NonNull T chat) {
        var range = createRange(chat.toJid(), false);
        var deleteChatAction = DeleteChatAction.of(range);
        var syncAction = ActionValueSync.of(deleteChatAction);
        var request = PatchRequest.of(REGULAR_HIGH, syncAction, SET, 6, chat.toJid()
                .toString(), "1");
        return socket.pushPatch(request)
                .thenApplyAsync(ignored -> chat);
    }

    /**
     * Clears the content of a chat for this client and its companions using a modern version of Whatsapp
     * Important: this message doesn't seem to work always as of now
     *
     * @param chat                the non-null chat to clear
     * @param keepStarredMessages whether starred messages in this chat should be kept
     * @return a CompletableFuture
     */
    public <T extends ContactJidProvider> CompletableFuture<T> clear(@NonNull T chat, boolean keepStarredMessages) {
        var known = store().findChatByJid(chat);
        var range = createRange(chat.toJid(), true);
        var clearChatAction = ClearChatAction.of(range);
        var syncAction = ActionValueSync.of(clearChatAction);
        var request = PatchRequest.of(REGULAR_HIGH, syncAction, SET, 6, chat.toJid()
                .toString(), booleanToInt(keepStarredMessages), "0");
        return socket.pushPatch(request)
                .thenApplyAsync(ignored -> chat);
    }

    /**
     * Change the description of this business profile
     *
     * @param description the new description, can be null
     * @return a CompletableFuture
     */
    public CompletableFuture<String> changeBusinessDescription(String description) {
        return changeBusinessAttribute("description", description);
    }

    /**
     * Change the address of this business profile
     *
     * @param address the new address, can be null
     * @return a CompletableFuture
     */
    public CompletableFuture<String> changeBusinessAddress(String address) {
        return changeBusinessAttribute("address", address);
    }

    /**
     * Change the email of this business profile
     *
     * @param email the new email, can be null. If a value is provided, it must be RFC822 compliant
     * @return a CompletableFuture
     */
    public CompletableFuture<String> changeBusinessEmail(String email) {
        Validate.isTrue(email == null || EmailChecker.isValid(email), "Invalid email: %s", email);
        return changeBusinessAttribute("email", email);
    }

    /**
     * Change the categories of this business profile
     *
     * @param categories the new categories, can be null
     * @return a CompletableFuture
     */
    public CompletableFuture<List<BusinessCategory>> changeBusinessCategories(List<BusinessCategory> categories) {
        return socket.sendQuery("set", "w:biz", Node.ofChildren("business_profile", Map.of("v", "3", "mutation_type", "delta"),
                        Node.ofChildren("categories", createCategories(categories))))
                .thenApplyAsync(ignored -> categories);
    }

    private Collection<Node> createCategories(List<BusinessCategory> categories) {
        if (categories == null) {
            return List.of();
        }

        return categories.stream()
                .map(entry -> Node.ofAttributes("category", Map.of("id", entry.id())))
                .toList();
    }

    /**
     * Change the websites of this business profile
     *
     * @param websites the new websites, can be null
     * @return a CompletableFuture
     */
    public CompletableFuture<List<URI>> changeBusinessWebsites(List<URI> websites) {
        return socket.sendQuery("set", "w:biz",
                        Node.ofChildren("business_profile", Map.of("v", "3", "mutation_type", "delta"), createWebsites(websites)))
                .thenApplyAsync(ignored -> websites);
    }

    /**
     * Query the catalog of this business
     *
     * @return a CompletableFuture
     */
    public CompletableFuture<List<BusinessCatalogEntry>> queryBusinessCatalog() {
        return queryBusinessCatalog(10);
    }

    /**
     * Query the catalog of this business
     *
     * @param productsLimit the maximum number of products to query
     * @return a CompletableFuture
     */
    public CompletableFuture<List<BusinessCatalogEntry>> queryBusinessCatalog(int productsLimit) {
        return queryBusinessCatalog(keys().companion()
                .toUserJid(), productsLimit);
    }

    /**
     * Query the catalog of a business
     *
     * @param contact the business
     * @return a CompletableFuture
     */
    public CompletableFuture<List<BusinessCatalogEntry>> queryBusinessCatalog(@NonNull ContactJidProvider contact) {
        return queryBusinessCatalog(contact, 10);
    }

    /**
     * Query the catalog of a business
     *
     * @param contact       the business
     * @param productsLimit the maximum number of products to query
     * @return a CompletableFuture
     */
    public CompletableFuture<List<BusinessCatalogEntry>> queryBusinessCatalog(@NonNull ContactJidProvider contact,
                                                                              int productsLimit) {
        return socket.sendQuery("get", "w:biz:catalog",
                        Node.ofChildren("product_catalog", Map.of("jid", contact, "allow_shop_source", "true"), Node.of("limit",
                                        String.valueOf(productsLimit)
                                                .getBytes(StandardCharsets.UTF_8)),
                                Node.of("width", "100".getBytes(StandardCharsets.UTF_8)),
                                Node.of("height", "100".getBytes(StandardCharsets.UTF_8))))
                .thenApplyAsync(this::parseCatalog);
    }

    private List<BusinessCatalogEntry> parseCatalog(Node result) {
        return Objects.requireNonNull(result, "Cannot query business catalog, missing response node")
                .findNode("product_catalog")
                .map(entry -> entry.findNodes("product"))
                .stream()
                .flatMap(Collection::stream)
                .map(BusinessCatalogEntry::of)
                .toList();
    }

    /**
     * Query the collections of this business
     *
     * @return a CompletableFuture
     */
    public CompletableFuture<?> queryBusinessCollections() {
        return queryBusinessCollections(50);
    }

    /**
     * Query the collections of this business
     *
     * @param collectionsLimit the maximum number of collections to query
     * @return a CompletableFuture
     */
    public CompletableFuture<?> queryBusinessCollections(int collectionsLimit) {
        return queryBusinessCollections(keys().companion()
                .toUserJid(), collectionsLimit);
    }

    /**
     * Query the collections of a business
     *
     * @param contact the business
     * @return a CompletableFuture
     */
    public CompletableFuture<?> queryBusinessCollections(@NonNull ContactJidProvider contact) {
        return queryBusinessCollections(contact, 50);
    }

    /**
     * Query the collections of a business
     *
     * @param contact          the business
     * @param collectionsLimit the maximum number of collections to query
     * @return a CompletableFuture
     */
    public CompletableFuture<List<BusinessCollectionEntry>> queryBusinessCollections(
            @NonNull ContactJidProvider contact, int collectionsLimit) {
        return socket.sendQuery("get", "w:biz:catalog", Map.of("smax_id", "35"), // Just why
                        Node.ofChildren("collections", Map.of("biz_jid", contact),
                                Node.of("collection_limit", String.valueOf(collectionsLimit).getBytes(StandardCharsets.UTF_8)),
                                Node.of("item_limit", String.valueOf(collectionsLimit).getBytes(StandardCharsets.UTF_8)),
                                Node.of("width", "100".getBytes(StandardCharsets.UTF_8)),
                                Node.of("height", "100".getBytes(StandardCharsets.UTF_8))))
                .thenApplyAsync(this::parseCollections);
    }

    private List<BusinessCollectionEntry> parseCollections(Node result) {
        return Objects.requireNonNull(result, "Cannot query business collections, missing response node")
                .findNode("collections")
                .stream()
                .map(entry -> entry.findNodes("collection"))
                .flatMap(Collection::stream)
                .map(BusinessCollectionEntry::of)
                .toList();
    }

    private CompletableFuture<String> changeBusinessAttribute(String key, String value) {
        return socket.sendQuery("set", "w:biz", Node.ofChildren("business_profile", Map.of("v", "3", "mutation_type", "delta"),
                        Node.of(key, requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8))))
                .thenAcceptAsync(result -> checkBusinessAttributeConflict(key, value, result))
                .thenApplyAsync(ignored -> value);
    }

    private void checkBusinessAttributeConflict(String key, String value, Node result) {
        var keyNode = result.findNode("profile")
                .flatMap(entry -> entry.findNode(key));
        if (keyNode.isEmpty()) {
            return;
        }

        var actual = keyNode.get()
                .contentAsString()
                .orElseThrow(() -> new NoSuchElementException(
                        "Missing business %s response, something went wrong: %s".formatted(key,
                                findErrorNode(result))));
        Validate.isTrue(value == null || value.equals(actual),
                "Cannot change business %s: conflict(expected %s, got %s)", key, value, actual);
    }

    private String findErrorNode(Node result) {
        return Optional.ofNullable(result)
                .flatMap(node -> node.findNode("error"))
                .map(Node::toString)
                .orElse("unknown");
    }

    /**
     * Downloads a media from Whatsapp's servers.
     * If the media is available, it will be returned asynchronously.
     * Otherwise, a retry request will be issued.
     * If that also fails, an exception will be thrown.
     * The difference between this method and {@link MediaMessage#decodedMedia()} is that this automatically attempts a retry request.
     *
     * @param info the non-null message info wrapping the media
     * @return a CompletableFuture
     */
    public CompletableFuture<byte[]> downloadMedia(@NonNull MessageInfo info){
        Validate.isTrue(info.message().category() == MessageCategory.MEDIA,
                "Expected media message, got: %s(%s)", info.message().category(), info.message().type());
        return downloadMedia(info, false);
    }

    private CompletableFuture<byte[]> downloadMedia(MessageInfo info, boolean retried) {
        var mediaMessage = (MediaMessage) info.message().content();
        var result = mediaMessage.decodedMedia();
        return switch (result.status()) {
            case SUCCESS -> CompletableFuture.completedFuture(result.media().get());
            case MISSING -> {
                if(retried){
                    throw new IllegalArgumentException("Media reupload failed");
                }

                yield requireMediaReupload(info)
                        .thenComposeAsync(entry -> downloadMedia(entry, true));
            }
            case ERROR -> throw new IllegalArgumentException("Cannot download media");
        };
    }

    /**
     * Asks Whatsapp for a media reupload for a specific media
     *
     * @param info the non-null message info wrapping the media
     * @return a CompletableFuture
     */
    public CompletableFuture<MessageInfo> requireMediaReupload(@NonNull MessageInfo info) {
        Validate.isTrue(info.message().category() == MessageCategory.MEDIA,
                "Expected media message, got: %s(%s)", info.message().category(), info.message().type());
        var mediaMessage = (MediaMessage) info.message().content();

        var retryKey = Hkdf.extractAndExpand(mediaMessage.mediaKey(),
                "WhatsApp Media Retry Notification".getBytes(StandardCharsets.UTF_8), 32);
        var retryIv = Bytes.ofRandom(12).toByteArray();
        var retryIdData = info.key().id().getBytes(StandardCharsets.UTF_8);
        var receipt = createReceipt(info);
        var ciphertext = AesGmc.cipher(retryIv, receipt, retryKey, retryIdData, true);
        var rmrAttributes = Attributes.empty()
                .put("jid", info.chatJid())
                .put("from_me", String.valueOf(info.fromMe()))
                .put("participant", info.senderJid(), () -> !Objects.equals(info.chatJid(), info.senderJid()))
                .map();
        var node = Node.ofChildren("receipt",
                Map.of("id", info.key().id(), "to", socket.keys().companion().toUserJid(), "type", "server-error"),
                Node.ofChildren("encrypt",
                        Node.of("enc_p", ciphertext),
                        Node.of("enc_iv", retryIv)),
                Node.ofAttributes("rmr", rmrAttributes));
        var handler = NodeHandler.of(entry -> entry.hasDescription("notification") && entry.attributes().getString("type").equals("mediaretry"));
        return socket.send(node)
                .thenApplyAsync(ignored -> this.store().addNodeHandler(handler))
                .thenApplyAsync(result -> parseMediaReupload(info, mediaMessage, retryKey, retryIdData, node));
    }

    private byte[] createReceipt(MessageInfo info) {
        try {
            return PROTOBUF.writeValueAsBytes(ServerErrorReceipt.of(info.id()));
        }catch (IOException exception){
            throw new UncheckedIOException("Cannot create receipt", exception);
        }
    }

    private MessageInfo parseMediaReupload(MessageInfo info, MediaMessage mediaMessage, byte[] retryKey,
                                           byte[] retryIdData, Node node) {
        var actualId = node.attributes()
                .getString("id");
        Validate.isTrue(Objects.equals(info.id(), actualId),
                "Wrong id in media reupload: expected %s, got %s", info.id(), actualId);
        var rmrNode = node.findNode("rmr")
                .orElseThrow(() -> new NoSuchElementException("Missing rmr node in media reupload"));
        Validate.isTrue(!rmrNode.hasNode("error"),
                "Erroneous response from media reupload");
        var encryptNode = node.findNode("encrypt")
                .orElseThrow(() -> new NoSuchElementException("Missing encrypt node in media reupload"));
        var mediaPayload = encryptNode.findNode("enc_p")
                .flatMap(Node::contentAsBytes)
                .orElseThrow(() -> new NoSuchElementException("Missing encrypted payload node in media reupload"));
        var mediaIv = encryptNode.findNode("enc_iv")
                .flatMap(Node::contentAsBytes)
                .orElseThrow(() -> new NoSuchElementException("Missing encrypted iv node in media reupload"));
        var mediaRetryNotificationData = AesGmc.cipher(mediaIv, mediaPayload, retryKey, retryIdData, false);
        var mediaRetryNotification = readRetryNotification(mediaRetryNotificationData);
        Validate.isTrue(mediaRetryNotification.directPath() != null,
                "Media retry upload failed: %s", mediaRetryNotification.result());
        mediaMessage.mediaUrl(Medias.createMediaUrl(mediaRetryNotification.directPath()));
        mediaMessage.mediaDirectPath(mediaRetryNotification.directPath());
        return info;
    }

    private MediaRetryNotification readRetryNotification(byte[] mediaRetryNotificationData) {
        try {
            return PROTOBUF.readMessage(mediaRetryNotificationData, MediaRetryNotification.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read retry notification", exception);
        }
    }

    private ActionMessageRangeSync createRange(ContactJidProvider chat, boolean allMessages) {
        var known = store().findChatByJid(chat.toJid())
                .orElseGet(() -> socket.createChat(chat.toJid()));
        return new ActionMessageRangeSync(known, allMessages);
    }

    private String participantToFlag(MessageInfo info) {
        return info.chatJid()
                .hasServer(GROUP) && !info.fromMe() ?
                info.senderJid()
                        .toString() :
                "0";
    }

    private String fromMeToFlag(MessageInfo info) {
        return booleanToInt(info.fromMe());
    }

    private String booleanToInt(boolean keepStarredMessages) {
        return keepStarredMessages ?
                "1" :
                "0";
    }

    /**
     * A configuration class used to specify the behaviour of {@link Whatsapp}
     */
    @Builder(builderMethodName = "newOptions")
    @With
    @Data
    @Accessors(fluent = true)
    public static class Options {
        /**
         * Last known version of Whatsapp
         */
        private static final Version WHATSAPP_VERSION = new Version(2, 2236, 10);

        /**
         * Constant for unlimited listeners size
         */
        private static final int UNLIMITED_LISTENERS = -1;

        /**
         * The id of the session.
         * This id needs to be unique.
         * By default, a random integer.
         */
        @Default
        private final int id = KeyHelper.registrationId();

        /**
         * Whether listeners marked with @RegisteredListener should be registered automatically.
         * By default, this option is enabled.
         */
        @Default
        private final boolean autodetectListeners = true;

        /**
         * Whether the default serialization mechanism should be used or not.
         * Set this to false if you want to implement a custom serializer.
         */
        @Default
        private final boolean defaultSerialization = true;

        /**
         * Whether a preview should be automatically generated and attached to text messages that contain links.
         * By default, it's enabled with inference.
         */
        @Default
        private final TextPreviewSetting textPreviewSetting = TextPreviewSetting.ENABLED_WITH_INFERENCE;

        /**
         * The version of WhatsappWeb to use.
         * If the version is too outdated, the server will refuse to connect.
         */
        @Default
        private final Version version = Version.ofLatest(WHATSAPP_VERSION);

        /**
         * The description provided to Whatsapp during the authentication process.
         * This should be, for example, the name of your service.
         * By default, it's WhatsappWeb4j.
         */
        @Default
        @NonNull
        private final String description = "WhatsappWeb4j";

        /**
         * Describes how much chat history Whatsapp should send when the QR is first scanned.
         * By default, one year.
         */
        @Default
        private HistoryLength historyLength = HistoryLength.ONE_YEAR;

        /**
         * Handles the qr code when a connection is first established with Whatsapp.
         * By default, the qr code is printed on the terminal.
         */
        @Default
        private QrHandler qrHandler = QrHandler.toTerminal();

        /**
         * Handles failures in the WebSocket.
         * By default, uses the simple handler and prints to the terminal.
         */
        @Default
        private ErrorHandler errorHandler = ErrorHandler.toTerminal();

        /**
         * The number of maximum listeners that the linked Whatsapp instance supports.
         * By default, unlimited.
         */
        @Default
        private int listenersLimit = UNLIMITED_LISTENERS;

        /**
         * Constructs a new instance of WhatsappConfiguration with default options
         *
         * @return a non-null options configuration
         */
        public static Options defaultOptions() {
            return newOptions().build();
        }
    }
}
