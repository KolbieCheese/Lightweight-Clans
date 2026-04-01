package github.scarsz.discordsrv.util;

public final class DiscordUtil {

    public static Object lastChannel;
    public static String lastMessage;

    private DiscordUtil() {
    }

    public static void queueMessage(Object channel, String message) {
        lastChannel = channel;
        lastMessage = message;
    }

    public static void reset() {
        lastChannel = null;
        lastMessage = null;
    }
}
