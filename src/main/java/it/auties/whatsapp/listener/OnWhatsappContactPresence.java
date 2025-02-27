package it.auties.whatsapp.listener;

import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.model.chat.Chat;
import it.auties.whatsapp.model.contact.ContactJid;
import it.auties.whatsapp.model.contact.ContactStatus;

public interface OnWhatsappContactPresence extends Listener {
    /**
     * Called when the socket receives an update regarding the presence of a contact
     *
     * @param whatsapp   an instance to the calling api
     * @param chat       the chat that this update regards
     * @param contactJid the contact that this update regards
     * @param status     the new status of the contact
     */
    @Override
    void onContactPresence(Whatsapp whatsapp, Chat chat, ContactJid contactJid, ContactStatus status);
}