package com.smu.cs203_g1_1.itinerary_planner.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.smu.cs203_g1_1.itinerary_planner.ai.OpenAiPlanChooser;
import com.smu.cs203_g1_1.itinerary_planner.ai.PlanChooser;
import com.smu.cs203_g1_1.itinerary_planner.preference.OpenAiPreferenceSummaryGenerator;
import com.smu.cs203_g1_1.itinerary_planner.preference.PreferenceSummaryGenerator;

/** Fails if callers could get the OpenAI beans directly and bypass the guard. */
@SpringBootTest(properties = {
        "CLERK_ISSUER_URI=",
        "app.polling.enabled=false",
        "app.llm.daily-call-budget=0"})
class LlmCallGuardWiringTest {

    @Autowired
    private PlanChooser planChooser;
    @Autowired
    private PreferenceSummaryGenerator summaryGenerator;
    @MockitoBean
    private OpenAiPlanChooser openAiPlanChooser;
    @MockitoBean
    private OpenAiPreferenceSummaryGenerator openAiSummaryGenerator;

    @Test
    void injectedBeansStopAtTheGuardWhenTheBudgetIsUsed() {
        assertThat(planChooser.choose(null)).isEmpty();
        assertThat(summaryGenerator.summariseGroup(List.of(), null)).isEmpty();
        assertThat(summaryGenerator.summariseUser(null, null)).isEmpty();

        verifyNoInteractions(openAiPlanChooser, openAiSummaryGenerator);
    }
}
