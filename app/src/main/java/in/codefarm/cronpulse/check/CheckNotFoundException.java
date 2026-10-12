package in.codefarm.cronpulse.check;

import java.util.UUID;

/** Also thrown for checks that exist in another organisation: we never reveal they exist. */
public class CheckNotFoundException extends RuntimeException {

    public CheckNotFoundException(UUID publicId) {
        super("Check " + publicId + " not found");
    }
}
