package it.auties.whatsapp.crypto;

import it.auties.bytes.Bytes;
import it.auties.whatsapp.exception.AesException;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

@UtilityClass
public class AesGmc {
    private final int NONCE = 128;

    public byte[] cipher(long iv, byte @NonNull [] input, byte @NonNull [] key, boolean encrypt) {
        return cipher(iv, input, key, null, encrypt);
    }

    public byte[] cipher(long iv, byte @NonNull [] input, byte @NonNull [] key, byte[] additionalData, boolean encrypt) {
        var ivBytes = Bytes.newBuffer(4)
                .appendLong(iv)
                .assertSize(12)
                .toByteArray();
        return cipher(ivBytes, input, key, additionalData, encrypt);
    }


    public byte[] cipher(byte @NonNull [] iv, byte @NonNull [] input, byte @NonNull [] key, byte[] additionalData,
                         boolean encrypt) {
        try {
            var cipher = new GCMBlockCipher(new AESEngine());
            var parameters = new AEADParameters(new KeyParameter(key), NONCE, iv, additionalData);
            cipher.init(encrypt, parameters);
            var outputLength = cipher.getOutputSize(input.length);
            var output = new byte[outputLength];
            var outputOffset = cipher.processBytes(input, 0, input.length, output, 0);
            cipher.doFinal(output, outputOffset);
            return output;
        }catch (InvalidCipherTextException exception){
            throw new AesException("Cannot %s data using AesGMC".formatted(encrypt ? "encrypt" : "decrypt"), exception);
        }
    }
}
