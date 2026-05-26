package com.example.agent;

import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.github.cdimascio.dotenv.Dotenv;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AgentCliRunner {

    public static void main(String[] args) {
        // Load .env (no reflection injection). HelloTimeAgent.getRootAgent() will use dotenv as a fallback.
        Dotenv dotenv = Dotenv.configure().ignoreIfMalformed().ignoreIfMissing().load();

        RunConfig runConfig = RunConfig.builder().build();
        InMemoryRunner runner = new InMemoryRunner(HelloTimeAgent.getRootAgent());

        Session session = runner
                .sessionService()
                .createSession(runner.appName(), "user1234")
                .blockingGet();

        try (Scanner scanner = new Scanner(System.in, UTF_8)) {
            while (true) {
                System.out.print("\nYou > ");
                String userInput = scanner.nextLine();
                if ("quit".equalsIgnoreCase(userInput)) {
                    break;
                }

                Content userMsg = Content.fromParts(Part.fromText(userInput));
                Flowable<Event> events = runner.runAsync(session.userId(), session.id(), userMsg, runConfig);

                System.out.print("\nAgent > ");
                events.blockingForEach(event -> {
                    if (event.finalResponse()) {
                        System.out.println(event.stringifyContent());
                    }
                });
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