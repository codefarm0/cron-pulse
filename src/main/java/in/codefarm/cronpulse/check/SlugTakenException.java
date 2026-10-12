package in.codefarm.cronpulse.check;

public class SlugTakenException extends RuntimeException {

    public SlugTakenException(String slug) {
        super("A check with slug '" + slug + "' already exists in this organisation.");
    }
}
