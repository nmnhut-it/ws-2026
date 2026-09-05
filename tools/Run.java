import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class Run {

    static final String GRADLE_VERSION = "8.14.3";
    static final String GRADLE_URL     = "https://services.gradle.org/distributions/gradle-" + GRADLE_VERSION + "-bin.zip";
    static final String MAVEN_VERSION  = "3.9.10";
    static final String MAVEN_URL      = "https://dlcdn.apache.org/maven/maven-3/" + MAVEN_VERSION
                                       + "/binaries/apache-maven-" + MAVEN_VERSION + "-bin.zip";
    static final Path   TOOLS_DIR      = Paths.get(System.getProperty("user.home"), ".jtools");
    static final int    MIN_JAVA       = 17;

    static final String SRC_DIR   = "src";
    static final String LIB_DIR   = "lib";
    static final String OUT_DIR   = "out";
    static final String TEST_SRC  = "tests/src/Tests.java";
    static final String TEST_OUT  = "tests/out";
    static final String MAIN      = "GameServer";
    static final String TEST_MAIN = "Tests";
    static final String DEFAULT_PORT = "9000";
    static final String DEFAULT_THRESHOLD = "3";

    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    public static void main(String[] args) throws Exception {
        checkJava();
        String mode = args.length > 0 ? args[0] : detectMode();
        List<String> rest = args.length > 1 ? Arrays.asList(args).subList(1, args.length) : List.of();
        int rc;
        switch (mode) {
            case "gradle": rc = gradle(rest.isEmpty() ? List.of("run") : rest); break;
            case "mvn":    rc = maven(rest.isEmpty() ? List.of("compile", "exec:java") : rest); break;
            case "test":   rc = plainTest(); break;
            case "server": rc = plainServer(rest.isEmpty() ? DEFAULT_PORT : rest.get(0)); break;
            default: System.out.println("dung / usage: java Run.java [server [port] | test | gradle <task..> | mvn <goal..>]"); rc = 2;
        }
        System.exit(rc);
    }

    static String detectMode() {
        if (Files.exists(Paths.get("build.gradle.kts")) || Files.exists(Paths.get("build.gradle"))) return "gradle";
        if (Files.exists(Paths.get("pom.xml"))) return "mvn";
        return "server";
    }

    static void checkJava() {
        int major = Runtime.version().feature();
        if (major < MIN_JAVA) {
            System.out.println("!! Java " + major + " qua cu. Can Java " + MIN_JAVA + " tro len: https://adoptium.net");
            System.exit(1);
        }
        System.out.println("[run] Java " + Runtime.version() + "  (" + System.getProperty("os.name") + ")");
    }

    static int plainCompile(String outDir, String... extraSources) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(javaTool("javac"), "-encoding", "UTF-8", "-cp", classpath(""), "-d", outDir));
        for (Path p : listJava(Paths.get(SRC_DIR))) cmd.add(p.toString());
        cmd.addAll(Arrays.asList(extraSources));
        Files.createDirectories(Paths.get(outDir));
        System.out.println("[run] bien dich / compiling -> " + outDir);
        int rc = exec(cmd);
        if (rc != 0) System.out.println("!! LOI BIEN DICH -- doc dong '<File>.java:<so dong>' o tren, sua, roi chay lai.");
        return rc;
    }

    static int plainServer(String port) throws Exception {
        int rc = plainCompile(OUT_DIR);
        if (rc != 0) return rc;
        String threshold = System.getProperty("beast.threshold", DEFAULT_THRESHOLD);
        System.out.println("[run] chay / starting " + MAIN + " " + port + " (nguong " + threshold + "). Ctrl-C de dung.");
        return exec(List.of(javaTool("java"), "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                            "-Dbeast.threshold=" + threshold, "-cp", classpath(OUT_DIR), MAIN, port));
    }

    static int plainTest() throws Exception {
        int rc = plainCompile(TEST_OUT, TEST_SRC);
        if (rc != 0) return rc;
        return exec(List.of(javaTool("java"), "-Dstdout.encoding=UTF-8", "-cp", classpath(TEST_OUT), TEST_MAIN));
    }

    static String classpath(String out) throws IOException {
        List<String> parts = new ArrayList<>();
        if (!out.isEmpty()) parts.add(out);
        Path lib = Paths.get(LIB_DIR);
        if (Files.isDirectory(lib)) try (var s = Files.list(lib)) {
            s.filter(p -> p.toString().endsWith(".jar")).forEach(p -> parts.add(p.toString()));
        }
        return String.join(File.pathSeparator, parts);
    }

    static List<Path> listJava(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) throw new FileNotFoundException("khong thay thu muc " + dir + " -- chay tu thu muc du an");
        try (var s = Files.walk(dir)) { return s.filter(p -> p.toString().endsWith(".java")).sorted().toList(); }
    }

    static String javaTool(String name) {
        Path p = Paths.get(System.getProperty("java.home"), "bin", WINDOWS ? name + ".exe" : name);
        return Files.exists(p) ? p.toString() : name;
    }

    static int gradle(List<String> tasks) throws Exception {
        String exe = wrapperOrPath("gradlew", "gradle");
        if (exe == null) exe = ensureTool("gradle-" + GRADLE_VERSION, GRADLE_URL, "gradle").toString();
        List<String> cmd = new ArrayList<>(shell(exe));
        cmd.addAll(tasks);
        return exec(cmd);
    }

    static int maven(List<String> goals) throws Exception {
        String exe = wrapperOrPath("mvnw", "mvn");
        if (exe == null) exe = ensureTool("apache-maven-" + MAVEN_VERSION, MAVEN_URL, "mvn").toString();
        List<String> cmd = new ArrayList<>(shell(exe));
        cmd.addAll(goals);
        return exec(cmd);
    }

    static String wrapperOrPath(String wrapper, String tool) {
        Path w = Paths.get(WINDOWS ? wrapper + (wrapper.equals("mvnw") ? ".cmd" : ".bat") : wrapper);
        if (Files.exists(w)) { System.out.println("[run] dung wrapper " + w); return w.toAbsolutePath().toString(); }
        String onPath = findOnPath(WINDOWS ? tool + (tool.equals("mvn") ? ".cmd" : ".bat") : tool);
        if (onPath != null) System.out.println("[run] dung " + onPath);
        return onPath;
    }

    static String findOnPath(String exe) {
        for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            Path p = Paths.get(dir, exe);
            if (Files.isExecutable(p)) return p.toString();
        }
        return null;
    }

    static Path ensureTool(String folder, String url, String tool) throws Exception {
        Path home = TOOLS_DIR.resolve(folder);
        Path bin = home.resolve("bin").resolve(WINDOWS ? tool + (tool.equals("mvn") ? ".cmd" : ".bat") : tool);
        if (Files.exists(bin)) { System.out.println("[run] dung " + bin); return bin; }
        Files.createDirectories(TOOLS_DIR);
        Path zip = TOOLS_DIR.resolve(folder + ".zip");
        System.out.println("[run] chua co " + tool + " -- tai ve " + url);
        download(url, zip);
        unzip(zip, TOOLS_DIR);
        Files.deleteIfExists(zip);
        if (!WINDOWS) bin.toFile().setExecutable(true);
        return bin;
    }

    static void download(String url, Path dest) throws Exception {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                                               HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " khi tai " + url);
        long total = r.headers().firstValueAsLong("content-length").orElse(-1), done = 0, lastPct = -1;
        try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(dest)) {
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n); done += n;
                long pct = total > 0 ? done * 100 / total : -1;
                if (pct != lastPct && pct % 10 == 0) { System.out.println("[run]   " + pct + "%  (" + done / (1 << 20) + " MB)"); lastPct = pct; }
            }
        }
    }

    static void unzip(Path zip, Path into) throws IOException {
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                Path p = into.resolve(e.getName()).normalize();
                if (!p.startsWith(into)) throw new IOException("zip entry ngoai thu muc: " + e.getName());
                if (e.isDirectory()) Files.createDirectories(p);
                else { Files.createDirectories(p.getParent()); Files.copy(z, p, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }

    static List<String> shell(String exe) {
        return WINDOWS ? List.of("cmd", "/c", exe) : List.of(exe);
    }

    static int exec(List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).inheritIO();
        pb.environment().put("JAVA_HOME", System.getProperty("java.home"));
        return pb.start().waitFor();
    }
}
