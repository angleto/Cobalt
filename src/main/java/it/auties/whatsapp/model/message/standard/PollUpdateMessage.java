package it.auties.whatsapp.model.message.standard;

import it.auties.protobuf.base.ProtobufName;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.model.contact.ContactJid;
import it.auties.whatsapp.model.info.MessageInfo;
import it.auties.whatsapp.model.message.model.Message;
import it.auties.whatsapp.model.message.model.MessageCategory;
import it.auties.whatsapp.model.message.model.MessageKey;
import it.auties.whatsapp.model.message.model.MessageType;
import it.auties.whatsapp.model.poll.PollOption;
import it.auties.whatsapp.model.poll.PollUpdateEncryptedMetadata;
import it.auties.whatsapp.model.poll.PollUpdateMessageMetadata;
import it.auties.whatsapp.util.Clock;
import it.auties.whatsapp.util.Validate;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static it.auties.protobuf.base.ProtobufType.INT64;
import static it.auties.protobuf.base.ProtobufType.MESSAGE;

/**
 * A model class that represents a message holding a vote for a poll inside
 */
@Data
@Accessors(fluent = true)
@Jacksonized
@Builder
@ProtobufName("PollUpdateMessage")
public final class PollUpdateMessage implements Message {
    private static final String POLL_NAME = "Poll Vote";

    /**
     * The jid of the contact who voted in this poll
     */
    private ContactJid voter;

    /**
     * The MessageKey of the poll where the user voted
     */
    @ProtobufProperty(index = 1, name = "pollCreationMessageKey", type = MESSAGE)
    private MessageKey pollCreationMessageKey;

    /**
     * The actual message where the user voted
     */
    private PollCreationMessage pollCreationMessage;

    /**
     * All the options, including the previous ones, that the user voted
     */
    @Default
    private List<PollOption> votes = new ArrayList<>();

    /**
     * The encryption data necessary to decipher this message
     */
    @ProtobufProperty(index = 2, name = "vote", type = MESSAGE)
    private PollUpdateEncryptedMetadata encryptedMetadata;

    /**
     * Metadata about this message
     */
    @ProtobufProperty(index = 3, name = "metadata", type = MESSAGE)
    private PollUpdateMessageMetadata metadata;

    /**
     * The timestamp of this message
     */
    @ProtobufProperty(index = 4, name = "senderTimestampMs", type = INT64)
    @Default
    private long senderTimestampMilliseconds = Clock.nowSeconds();

    /**
     * Constructs a new builder to create a PollCreationMessage The result can be later sent using
     * {@link Whatsapp#sendMessage(MessageInfo)}
     *
     * @param poll  the non-null poll where the vote should be cast
     * @param votes the votes to cast: this list will override previous votes, so it can be empty or null if you want to revoke all votes
     * @return a non-null new message
     */
    @Builder(builderClassName = "SimplePollUpdateMessageBuilder", builderMethodName = "simpleBuilder")
    public static PollUpdateMessage of(@NonNull MessageInfo poll, List<PollOption> votes) {
        Validate.isTrue(poll.message()
                .type() == MessageType.POLL_CREATION, "Expected a poll, got %s".formatted(poll.message().type()));
        return PollUpdateMessage.builder()
                .pollCreationMessageKey(poll.key())
                .pollCreationMessage((PollCreationMessage) poll.message().content())
                .votes(Objects.requireNonNullElseGet(votes, List::of))
                .build();
    }

    /**
     * Returns the time when this message was sent, if available
     *
     * @return a non-null optional
     */
    public ZonedDateTime senderTimestamp() {
        return Clock.parseSeconds(senderTimestampMilliseconds);
    }

    /**
     * Returns the metadata of this message
     *
     * @return a non-null optional
     */
    public Optional<PollUpdateMessageMetadata> metadata(){
        return Optional.ofNullable(metadata);
    }

    public String secretName() {
        return POLL_NAME;
    }

    @Override
    public MessageType type() {
        return MessageType.POLL_UPDATE;
    }

    @Override
    public MessageCategory category() {
        return MessageCategory.STANDARD;
    }
}