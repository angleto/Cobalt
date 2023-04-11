package it.auties.whatsapp.model.message.model;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufName;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.whatsapp.model.contact.ContactJid;
import it.auties.whatsapp.util.Clock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static it.auties.protobuf.base.ProtobufType.INT64;
import static it.auties.protobuf.base.ProtobufType.STRING;

/**
 * A model that represents the receipt for a message
 */
@AllArgsConstructor
@Data
@Builder
@Jacksonized
@Accessors(fluent = true)
@ProtobufName("UserReceipt")
public class MessageReceipt implements ProtobufMessage {
    /**
     * When the message was delivered(two ticks)
     */
    @ProtobufProperty(index = 2, type = INT64)
    private Long deliveredTimestampSeconds;

    /**
     * When the message was read(two blue ticks)
     */
    @ProtobufProperty(index = 3, type = INT64)
    private Long readTimestampSeconds;

    /**
     * When the message was played(two blue ticks)
     */
    @ProtobufProperty(index = 4, type = INT64)
    private Long playedTimestampSeconds;

    /**
     * A list of contacts who received the message(two ticks)
     */
    @ProtobufProperty(index = 5, type = STRING, repeated = true, implementation = ContactJid.class)
    @Default
    private List<ContactJid> deliveredJids = new ArrayList<>();

    /**
     * A list of contacts who read the message(two blue ticks)
     */
    @ProtobufProperty(index = 6, type = STRING, repeated = true, implementation = ContactJid.class)
    @Default
    private List<ContactJid> readJids = new ArrayList<>();

    /**
     * Returns a default message receipt
     *
     * @return a non-null instance
     */
    public static MessageReceipt of() {
        return MessageReceipt.builder().build();
    }

    /**
     * Returns the date when the message was delivered
     *
     * @return a non-null optional
     */
    public Optional<ZonedDateTime> deliveredTimestamp() {
        return Clock.parseSeconds(deliveredTimestamp);
    }

    /**
     * Returns the date when the message was delivered
     *
     * @return a non-null optional
     */
    public Optional<ZonedDateTime> readTimestamp() {
        return Clock.parseSeconds(readTimestamp);
    }

    /**
     * Returns the date when the message was played
     *
     * @return a non-null optional
     */
    public Optional<ZonedDateTime> playedTimestamp() {
        return Clock.parseSeconds(playedTimestamp);
    }

    /**
     * Sets the read timestamp
     *
     * @param readTimestamp the timestamp
     * @return the same instance
     */
    public MessageReceipt readTimestamp(long readTimestamp) {
        if (deliveredTimestamp == null) {
            this.deliveredTimestamp = readTimestamp;
        }
        this.readTimestamp = readTimestamp;
        return this;
    }

    /**
     * Sets the played timestamp
     *
     * @param playedTimestamp the timestamp
     * @return the same instance
     */
    public MessageReceipt playedTimestamp(long playedTimestamp) {
        if (deliveredTimestamp == null) {
            this.deliveredTimestamp = playedTimestamp;
        }
        if (readTimestamp == null) {
            this.readTimestamp = playedTimestamp;
        }
        this.playedTimestamp = playedTimestamp;
        return this;
    }
}