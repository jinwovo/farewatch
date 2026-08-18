package com.portfolio.farewatch;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Alert-condition slice: BELOW_THRESHOLD actually fires, rules are editable via
 * PATCH (with parameter validation), and fired alerts surface on the global feed.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AlertRuleApiTest {

	@Autowired
	MockMvc mockMvc;

	private String createWatch(String body) throws Exception {
		String created = mockMvc.perform(post("/api/watches")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(created, "$.id");
	}

	@Test
	void below_threshold_fires_and_appears_on_global_feed() throws Exception {
		// threshold far above any simulated fare → the very first poll crosses it
		String id = createWatch("""
				{
				  "userRef": "rule-user",
				  "origin": "ICN",
				  "destination": "NRT",
				  "tripType": "ONE_WAY",
				  "departDateFrom": "2026-11-10",
				  "departDateTo": "2026-11-14",
				  "alertRule": "BELOW_THRESHOLD",
				  "thresholdAmount": 99999999
				}
				""");

		mockMvc.perform(post("/api/watches/{id}/poll", id)).andExpect(status().isOk());

		mockMvc.perform(get("/api/watches/{id}/alerts", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].rule").value("BELOW_THRESHOLD"));

		// the same alert is on the global feed, with watch context for rendering
		mockMvc.perform(get("/api/alerts").param("limit", "50"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.watchId == '%s')]".formatted(id), hasSize(1)))
				.andExpect(jsonPath("$[?(@.watchId == '%s')].origin".formatted(id)).value("ICN"))
				.andExpect(jsonPath("$[?(@.watchId == '%s')].rule".formatted(id)).value("BELOW_THRESHOLD"));
	}

	@Test
	void patch_edits_alert_rule_with_validation() throws Exception {
		String id = createWatch("""
				{
				  "userRef": "rule-edit-user",
				  "origin": "ICN",
				  "destination": "KIX",
				  "tripType": "ONE_WAY",
				  "departDateFrom": "2026-11-01",
				  "departDateTo": "2026-11-03"
				}
				""");

		// a rule that needs a parameter is rejected when none is set
		mockMvc.perform(patch("/api/watches/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"alertRule\": \"DROP_PCT\"}"))
				.andExpect(status().isBadRequest());

		// rule + parameter in one call
		mockMvc.perform(patch("/api/watches/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"alertRule\": \"DROP_PCT\", \"dropPct\": 15}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alertRule").value("DROP_PCT"))
				.andExpect(jsonPath("$.dropPct").value(15));

		mockMvc.perform(patch("/api/watches/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"alertRule\": \"BELOW_THRESHOLD\", \"thresholdAmount\": 300000}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alertRule").value("BELOW_THRESHOLD"))
				.andExpect(jsonPath("$.thresholdAmount").value(300000));

		// bean validation: dropPct outside (0, 90] is rejected
		mockMvc.perform(patch("/api/watches/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"dropPct\": 95}"))
				.andExpect(status().isBadRequest());
	}
}