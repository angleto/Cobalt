package it.auties.whatsapp.model.interactive;

import it.auties.protobuf.base.ProtobufProperty;
import it.auties.whatsapp.model.contact.ContactJid;
import it.auties.whatsapp.model.message.button.InteractiveMessageContent;
import it.auties.whatsapp.model.message.button.InteractiveMessageContentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import static it.auties.protobuf.base.ProtobufType.INT32;
import static it.auties.protobuf.base.ProtobufType.STRING;

/**
 * A model class that represents a business collection
 */
@AllArgsConstructor
@Data
@Builder
@Jacksonized
@Accessors(fluent = true)
public final class InteractiveCollection implements InteractiveMessageContent {
    /**
     * The business jid of the message
     */
    @ProtobufProperty(index = 1, type = STRING, implementation = ContactJid.class)
    private ContactJid business;

    /**
     * The id of the message
     */
    @ProtobufProperty(index = 2, type = STRING)
    private String id;

    /**
     * The version of the message
     */
    @ProtobufProperty(index = 3, type = INT32)
    private int version;

    @Override
    public InteractiveMessageContentType contentType() {
        return InteractiveMessageContentType.COLLECTION;
    }
}
