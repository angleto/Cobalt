package it.auties.whatsapp.model.message.model;

import it.auties.whatsapp.model.message.button.*;
import it.auties.whatsapp.model.message.standard.InteractiveMessage;
import it.auties.whatsapp.model.message.standard.ProductMessage;

/**
 * A model interface that represents a button message
 */
public sealed interface ButtonMessage extends Message
        permits InteractiveMessage, ProductMessage, ListMessage, HighlyStructuredMessage,
        TemplateMessage, ButtonsMessage, ButtonReplyMessage {
    @Override
    default MessageCategory category() {
        return MessageCategory.BUTTON;
    }
}
