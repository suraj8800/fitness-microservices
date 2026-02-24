package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAiService {
    private final GeminiService geminiService;

    public void generateRecommendation(Activity activity){
        String prompt = createPromptForActivity(activity);
        log.info("RESPONSE FROM AI {} " + geminiService.getRecommendations(prompt));
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
