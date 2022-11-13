package it.auties.whatsapp.listener;

import it.auties.whatsapp.socket.SocketHandler;

import java.util.List;

public interface OnFeatures extends Listener {
    /**
     * Called when {@link SocketHandler} receives new features from Whatsapp.
     *
     * @param features the non-null features that were sent
     */
    @Override
    void onFeatures(List<String> features);
}