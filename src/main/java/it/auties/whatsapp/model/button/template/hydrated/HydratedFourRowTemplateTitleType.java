package it.auties.whatsapp.model.button.template.hydrated;

import com.fasterxml.jackson.annotation.JsonCreator;
import it.auties.protobuf.base.ProtobufMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Arrays;

/**
 * The constants of this enumerated type describe the various types of title that a template can
 * wrap
 */
@AllArgsConstructor
@Accessors(fluent = true)
public enum HydratedFourRowTemplateTitleType implements ProtobufMessage {
    /**
     * No title
     */
    NONE(0),
    /**
     * Document title
     */
    DOCUMENT(1),
    /**
     * Text title
     */
    TEXT(2),
    /**
     * Image title
     */
    IMAGE(3),
    /**
     * Video title
     */
    VIDEO(4),
    /**
     * Location title
     */
    LOCATION(5);

    @Getter
    private final int index;

    @JsonCreator
    public static HydratedFourRowTemplateTitleType of(int index) {
        return Arrays.stream(values())
                .filter(entry -> entry.index() == index)
                .findFirst()
                .orElse(HydratedFourRowTemplateTitleType.NONE);
    }
}
