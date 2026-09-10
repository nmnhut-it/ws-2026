import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Deploy {

    static final String SRC_DIR   = "src";
    static final String JAVA_EXT  = ".java";
    static final int    MAX_CHARS = 256 * 1024;

    public static void main(String[] args) throws IOException {
        String group = Report.group();
        if (group.isEmpty()) fail(Report.NO_GROUP);
        int workshop = args.length > 0 && "2".equals(args[0]) ? 2 : 1;

        System.out.println("  Sending group " + group + " code to " + Report.host() + " ...");
        JsonObject body = new JsonObject();
        body.addProperty("group", group);
        body.addProperty("workshop", workshop);
        body.add("files", filesJson());
        String reply = Report.post(Report.DEPLOY_PATH, body.toString());
        if (reply == null) fail("  Could not send -- check the network, or the main server is down.");
        System.out.println();
        System.out.println(reply);
    }

    static void fail(String text) {
        System.out.println(text);
        System.exit(1);
    }

    static JsonObject filesJson() throws IOException {
        JsonObject files = new JsonObject();
        int total = 0;
        for (Path p : sources()) {
            String body = Files.readString(p, StandardCharsets.UTF_8);
            total += body.length();
            if (total > MAX_CHARS) fail("  Code too large to send.");
            files.addProperty(p.getFileName().toString(), body);
        }
        if (files.size() == 0) fail("  No .java file found in " + SRC_DIR + "/");
        return files;
    }

    static List<Path> sources() throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(Path.of(SRC_DIR))) {
            s.filter(p -> p.getFileName().toString().endsWith(JAVA_EXT)).sorted().forEach(out::add);
        }
        return out;
    }
}
