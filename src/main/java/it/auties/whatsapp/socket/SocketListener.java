package it.auties.whatsapp.socket;

import jakarta.websocket.CloseReason;

import java.util.Optional;

public interface SocketListener {
    void onOpen(SocketSession session);

    void onMessage(byte[] message);

    void onClose();

    void onError(Throwable throwable);
}
