package it.auties.whatsapp.listener;

import it.auties.whatsapp.model.request.Node;
import it.auties.whatsapp.socket.SocketHandler;

public interface OnNodeSent extends Listener {
    /**
     * Called when {@link SocketHandler} sends a node to Whatsapp
     *
     * @param outgoing the non-null node that was just sent
     */
    @Override
    void onNodeSent(Node outgoing);
}