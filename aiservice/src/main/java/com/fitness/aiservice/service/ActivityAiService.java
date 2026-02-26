package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAiService {
    private final GeminiService geminiService;

    public Recommendation generateRecommendation(Activity activity){
        String prompt = createPromptForActivity(activity);
        String aiResponse = geminiService.getRecommendations(prompt);
        log.info("RESPONSE FROM AI {} " + aiResponse);
        return processAIResponse(activity, aiResponse);
    }

    private Recommendation processAIResponse(Activity activity, String aiResponse) {
        try{
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(aiResponse);
            JsonNode textNode = rootNode.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");

            if(textNode.isTextual()) {
                String jsonContent = textNode.asText()
                        .replaceAll("```json\\n", "")
                        .replaceAll("\\n```", "")
                        .trim();
//                log.info("RESPONSE FROM CLEANED AI {} " + jsonContent);
                JsonNode analysisJson = mapper.readTree(jsonContent);
                JsonNode analysisNode = analysisJson.path("analysis");
                StringBuilder fullAnalysis = new StringBuilder();
                addAnalysisSection(fullAnalysis, analysisNode, "overall", "Overall:" );
                addAnalysisSection(fullAnalysis, analysisNode, "pace", "Pace:" );
                addAnalysisSection(fullAnalysis, analysisNode, "heartRate", "Heart Rate:" );
                addAnalysisSection(fullAnalysis, analysisNode, "caloriesBurned", "Calories Burned:" );

                List<String> improvements = extractImprovement(analysisJson.path("improvements"));
                List<String> suggestions = extractSuggestions(analysisJson.path("suggestions"));
                List<String> safety = extractSafety(analysisJson.path("safety"));

                return Recommendation.builder()
                        .activityId(activity.getId())
                        .userId(activity.getUserId())
                        .type(activity.getType().toString())
                        .recommendation(fullAnalysis.toString())
                        .improvement(improvements)
                        .suggestions(suggestions)
                        .safety(safety)
                        .createdAt(LocalDateTime.now())
                        .build();
            }

        }catch (Exception e){
            e.printStackTrace();
            return createDefaultRecommendation(activity);
        }
        return createDefaultRecommendation(activity);
    }

    private Recommendation createDefaultRecommendation(Activity activity) {
        return Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .type(activity.getType().toString())
                .recommendation("Unable to generate detail analysis")
                .improvement(Collections.singletonList("Continue with your current routine"))
                .suggestions(Collections.singletonList("Consider consulting a fitness consultant"))
                .safety(Arrays.asList(
                        "Always warm up before exercise",
                        "Stay hydrated",
                        "Listen your body"
                ))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private List<String> extractSafety(JsonNode safefyNode) {
        List<String> safety = new ArrayList<>();
        if(safefyNode.isArray()){
            safefyNode.forEach(item -> safety.add(item.asText()));
        }
        return safety.isEmpty() ?
                Collections.singletonList("Follow general safety guidelines") :
                safety;
    }

    private List<String> extractSuggestions(JsonNode suggestionsNode) {
        List<String> suggestions = new ArrayList<>();
        if(suggestionsNode.isArray()){
            suggestionsNode.forEach(suggestion -> {
                String workout = suggestion.path("workout").asText();
                String description = suggestion.path("description").asText();
                suggestions.add(String.format("%s: %s", workout, description));
            });
        }
        return suggestions.isEmpty() ?
                Collections.singletonList("No specific suggestion provided") :
                suggestions;

    }

    private List<String> extractImprovement(JsonNode improvementsNode) {
        List<String> improvements = new ArrayList<>();
        if(improvementsNode.isArray()){
            improvementsNode.forEach(improvement -> {
                String area = improvement.path("area").asText();
                String recommendation = improvement.path("recommendation").asText();
                improvements.add(String.format("%s: %s", area, recommendation));
            });
        }
        return improvements.isEmpty() ?
                Collections.singletonList("No specific improvement provided") :
                improvements;
    }

    private void addAnalysisSection(StringBuilder fullAnalysis, JsonNode analysisNode, String key, String prefix) {
        if(!analysisNode.path(key).isMissingNode()){
            fullAnalysis.append(prefix)
                    .append(analysisNode.path(key).asText())
                    .append("\n\n");
        }
    }

    private String createPromptForActivity(Activity activity) {
        return String.format("""
                Analyze this fitness activity and provide detailed recommendation in the following EXACT JSON format:
                {
                 "analysis": {
                    "overall": "Overall analysis here",
                    "pace": "Pace analysis here",
                    "heartRate": "Heart rate anaysis here",
                    "caloriesBurned": "Calories analysis here"
                 },
                 "improvements": [
                     { 
                        "area": "Area name",
                        "recommendation": "Detailed recommendation"
                     }
                 ],
                 "suggestions":[
                    { 
                        "workout": "Workout name",
                        "description": "Detailed workout description"
                    }
                 ],
                 "safefy":[
                    "Safety point 1",
                    "Safety point 2"
                 ]
                }
                
                Analyze this activity:
                Activity Type: %s
                Duration: %d minutes
                calories Burned: %d
                Additional Metrics: %s
                
                Provide detailed analysis focusing on performance, improvement, next workout suggestions, and safety gudelines.
                Ensure the response follows the EXACT JSON format shown above.
                """,
                    activity.getType(),
                    activity.getDuration(),
                    activity.getCaloriesBurned(),
                    activity.getAdditionalMetrices()
                );
    }
}
