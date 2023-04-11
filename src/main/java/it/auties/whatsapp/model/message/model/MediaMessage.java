package it.auties.whatsapp.model.message.model;

import it.auties.whatsapp.model.info.MessageInfo;
import it.auties.whatsapp.model.media.AttachmentProvider;
import it.auties.whatsapp.model.media.AttachmentType;
import it.auties.whatsapp.model.message.payment.PaymentInvoiceMessage;
import it.auties.whatsapp.model.message.standard.*;
import it.auties.whatsapp.util.Medias;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * A model class that represents a message holding media inside This class is only a model, this
 * means that changing its values will have no real effect on WhatsappWeb's servers. Even though the
 * same instance is in the wrapping message info(MessageInfo -> MessageContainer -> MediaMessage),
 * there is currently no way to navigate the tree upwards or any errorReason to do so considering that
 * this is a special use case. Considering that passing the same instance to
 * {@link MediaMessage#decodedMedia()} is verbose and unnecessary, there is a copy here.
 */
@AllArgsConstructor
@SuperBuilder
@NoArgsConstructor
@Accessors(fluent = true)
@EqualsAndHashCode(callSuper = true)
public abstract sealed class MediaMessage extends ContextualMessage implements AttachmentProvider permits PaymentInvoiceMessage, AudioMessage, DocumentMessage, ImageMessage, StickerMessage, VideoMessage {
    /**
     * The cached decoded media, by default null
     */
    private byte[] decodedMedia;

    /**
     * Saves this media to the provided path. Throws an error if the media cannot be downloaded
     * successfully.
     *
     * @param path the non-null path where the media should be written.
     * @return the non-null path where the file was downloaded
     */
    public Path save(@NonNull Path path) {
        try {
            var data = decodedMedia().orElseThrow(() -> new NoSuchElementException("Cannot save a media that wasn't decoded correctly"));
            Files.createDirectories(path.getParent());
            Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return path;
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot write media to file", exception);
        }
    }

    /**
     * Returns the cached decoded media wrapped by this object if available. Otherwise, the encoded
     * media that this object wraps is decoded, cached and returned. The difference between this
     * method and {@link it.auties.whatsapp.api.Whatsapp#downloadMedia(MessageInfo)} is that this
     * method doesn't try to issue a reupload.
     *
     * @return a non-null result
     */
    public Optional<byte[]> decodedMedia() {
        if (decodedMedia == null) {
            this.decodedMedia = Medias.download(this)
                    .join()
                    .orElse(null);
        }
        return Optional.ofNullable(decodedMedia);
    }

    /**
     * Returns the timestamp, that is the seconds elapsed since {@link java.time.Instant#EPOCH}, for
     * {@link MediaMessage#mediaKey()}
     *
     * @return an unsigned long
     */
    public abstract long mediaKeyTimestamp();

    @Override
    public String mediaName() {
        return mediaType().keyName();
    }
}
