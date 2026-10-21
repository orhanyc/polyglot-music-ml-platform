package com.orhanyc.music.privacy;

import com.orhanyc.music.model.ListeningEvent;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@Service
public class PrivacyService {
    private static final Set<String> ALLOWED_CONTEXT = Set.of(
            "country",
            "deviceClass",
            "subscriptionTier");
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secret = "local-demo-secret".getBytes(StandardCharsets.UTF_8);

    public ListeningEvent minimize(ListeningEvent event) {
        Map<String, String> context = event.context().entrySet().stream()
                .filter(entry -> ALLOWED_CONTEXT.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));
        return new ListeningEvent(
                hashIdentifier(event.anonymousId()),
                event.trackId(),
                event.type(),
                event.timestamp(),
                event.playSeconds(),
                event.surface(),
                context);
    }

    public String hashIdentifier(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 24);
        } catch (NoSuchAlgorithmException | InvalidKeyException error) {
            throw new IllegalStateException("Unable to protect listener identifier", error);
        }
    }
}

