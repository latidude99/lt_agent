package com.example.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Map;

public class HelloTimeAgent {

    public static BaseAgent ROOT_AGENT = null;

    private static BaseAgent initAgent() {
        // Accept API key from environment or .env file via Dotenv
        Dotenv dotenv = Dotenv.configure().ignoreIfMalformed().ignoreIfMissing().load();
        String gKey = System.getenv("GOOGLE_API_KEY");
        if (gKey == null || gKey.isBlank()) gKey = dotenv.get("GOOGLE_API_KEY");
        String gem = System.getenv("GEMINI_API_KEY");
        if (gem == null || gem.isBlank()) gem = dotenv.get("GEMINI_API_KEY");
        String apiKey = (gKey != null && !gKey.isBlank()) ? gKey : gem;
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Warning: GOOGLE_API_KEY and GEMINI_API_KEY not found — HelloTimeAgent will not enable LLM features.");
            return null;
        }

        return LlmAgent.builder()
                .name("hello-time-agent")
                .description("Tells the current time in a specified city")
                .instruction("""
                You are a helpful assistant that tells the current time in a city.
                Use the 'getCurrentTime' tool for this purpose.
                """)
                .model("gemini-flash-latest")
                .tools(FunctionTool.create(HelloTimeAgent.class, "getCurrentTime"))
                .build();
    }

    public static synchronized BaseAgent getRootAgent() {
        if (ROOT_AGENT == null) {
            ROOT_AGENT = initAgent();
        }
        return ROOT_AGENT;
    }

    /** Mock tool implementation */
    @Schema(description = "Get the current time for a given city")
    public static Map<String, String> getCurrentTime(
            @Schema(name = "city", description = "Name of the city to get the time for") String city) {
        return Map.of(
                "city", city,
                "forecast", "The time is 10:30am."
        );
    }
}