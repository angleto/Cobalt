package it.auties.whatsapp.model.interactive;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufName;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.whatsapp.model.message.button.InteractiveMessageContent;
import it.auties.whatsapp.model.message.button.InteractiveMessageContentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.util.Arrays;

import static it.auties.protobuf.base.ProtobufType.*;

/**
 * A model class that represents a shop
 */
@AllArgsConstructor(staticName = "of")
@Data
@Builder
@Jacksonized
@Accessors(fluent = true)
@ProtobufName("InteractiveMessage.ShopMessage")
public final class InteractiveShop implements InteractiveMessageContent {
    /**
     * The id of the shop
     */
    @ProtobufProperty(index = 1, type = STRING)
    private String id;

    /**
     * The surface of the shop
     */
    @ProtobufProperty(index = 2, type = MESSAGE, implementation = SurfaceType.class)
    private SurfaceType surfaceType;

    /**
     * The version of the message
     */
    @ProtobufProperty(index = 3, type = INT32)
    private int version;

    @Override
    public InteractiveMessageContentType contentType() {
        return InteractiveMessageContentType.SHOP;
    }

    /**
     * The constants of this enumerated type describe the various types of surfaces that a
     * {@link InteractiveShop} can have
     */
    @AllArgsConstructor
    @Accessors(fluent = true)
    @ProtobufName("Surface")
    public enum SurfaceType implements ProtobufMessage {
        /**
         * Unknown
         */
        UNKNOWN_SURFACE(0),
        /**
         * Facebook
         */
        FACEBOOK(1),
        /**
         * Instagram
         */
        INSTAGRAM(2),
        /**
         * Whatsapp
         */
        WHATSAPP(3);
        
        @Getter
        private final int index;

        public static SurfaceType of(int index) {
            return Arrays.stream(values()).filter(entry -> entry.index() == index).findFirst().orElse(null);
        }
    }
}