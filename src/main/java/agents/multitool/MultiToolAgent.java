package agents.multitool;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.github.cdimascio.dotenv.Dotenv;
import io.reactivex.rxjava3.core.Flowable;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class MultiToolAgent {

    private static String USER_ID = "student";
    private static String NAME = "multi_tool_agent";

    // The run your agent with Dev UI, the ROOT_AGENT should be a global public static final variable.
    public static BaseAgent ROOT_AGENT = null;

    public static BaseAgent initAgent() {
        // Ensure API key is present before building the LLM client to avoid NPE inside vendor library
        String gKey = System.getenv("GOOGLE_API_KEY");
        String gem = System.getenv("GEMINI_API_KEY");
        String apiKey = (gKey != null && !gKey.isBlank()) ? gKey : gem;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Set GOOGLE_API_KEY or GEMINI_API_KEY in the environment before running");
        }

        return LlmAgent.builder()
                .name(NAME)
                .model("gemini-2.0-flash")
                .description("Agent to answer questions about the time and weather in a city.")
                .instruction(
                        "You are a helpful agent who can answer user questions about the time and weather"
                                + " in a city.")
                .tools(
                        FunctionTool.create(MultiToolAgent.class, "getCurrentTime"),
                        FunctionTool.create(MultiToolAgent.class, "getWeather"))
                .build();
    }

    public static Map<String, String> getCurrentTime(
            @Schema(name = "city",
                    description = "The name of the city for which to retrieve the current time")
            String city) {
        String normalizedCity =
                Normalizer.normalize(city, Normalizer.Form.NFD)
                        .trim()
                        .toLowerCase()
                        .replaceAll("(\\p{IsM}+|\\p{IsP}+)", "")
                        .replaceAll("\\s+", "_");

        return ZoneId.getAvailableZoneIds().stream()
                .filter(zid -> zid.toLowerCase().endsWith("/" + normalizedCity))
                .findFirst()
                .map(
                        zid ->
                                Map.of(
                                        "status",
                                        "success",
                                        "report",
                                        "The current time in "
                                                + city
                                                + " is "
                                                + ZonedDateTime.now(ZoneId.of(zid))
                                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                                                + "."))
                .orElse(
                        Map.of(
                                "status",
                                "error",
                                "report",
                                "Sorry, I don't have timezone information for " + city + "."));
    }

    public static Map<String, String> getWeather(
            @Schema(name = "city",
                    description = "The name of the city for which to retrieve the weather report")
            String city) {
        if (city.toLowerCase().equals("new york")) {
            return Map.of(
                    "status",
                    "success",
                    "report",
                    "The weather in New York is sunny with a temperature of 25 degrees Celsius (77 degrees"
                            + " Fahrenheit).");

        } else {
            return Map.of(
                    "status", "error", "report", "Weather information for " + city + " is not available.");
        }
    }

    public static void main(String[] args) throws Exception {

        // Load .env and inject GOOGLE_API_KEY / GEMINI_API_KEY into the process environment
        Dotenv dotenv = Dotenv.configure().ignoreIfMalformed().ignoreIfMissing().load();
        Map<String, String> toSet = new HashMap<>();
        String gKey = dotenv.get("GOOGLE_API_KEY");
        if (gKey != null && !gKey.isBlank()) toSet.put("GOOGLE_API_KEY", gKey);
        String gem = dotenv.get("GEMINI_API_KEY");
        if (gem != null && !gem.isBlank()) toSet.put("GEMINI_API_KEY", gem);
        if (!toSet.isEmpty()) {
            try {
                setEnv(toSet);
            } catch (Exception e) {
                System.err.println("Warning: failed to inject .env variables: " + e.getMessage());
            }
        }
        ROOT_AGENT = initAgent();

        InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT);

        Session session =
                runner
                        .sessionService()
                        .createSession(NAME, USER_ID)
                        .blockingGet();

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print("\nYou Lati > ");
                String userInput = scanner.nextLine();

                if ("quit".equalsIgnoreCase(userInput)) {
                    break;
                }

                Content userMsg = Content.fromParts(Part.fromText(userInput));
                Flowable<Event> events = runner.runAsync(USER_ID, session.id(), userMsg);

                System.out.print("\nAgent > ");
                events.blockingForEach(event -> System.out.println(event.stringifyContent()));
            }
        }
    }

    // Best-effort injection of env vars into System.getenv() map via reflection.
    // This is platform-dependent but works on common OpenJDK/Oracle JVMs.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnv(Map<String, String> newenv) throws Exception {
        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newenv);
            Field theCIField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCIField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>) theCIField.get(null);
            cienv.putAll(newenv);
        } catch (NoSuchFieldException e) {
            // Fallback for other JVM implementations
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> map = (Map<String, String>) field.get(env);
            map.putAll(newenv);
        }
    }
}