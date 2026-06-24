package com.portfolio.farewatch;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the P1 vertical slice end-to-end against a real PostgreSQL (Testcontainers):
 * create a watch -> poll it -> a price point lands in the time-series -> history reads back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WatchApiIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void create_then_poll_then_read_history() throws Exception {
		String createBody = """
				{
				  "userRef": "demo-user",
				  "origin": "ICN",
				  "destination": "NRT",
				  "tripType": "ONE_WAY",
				  "departDateFrom": "2026-07-20",
				  "departDateTo": "2026-07-24"
				}
				""";

		String created = mockMvc.perform(post("/api/watches")
						.contentType(MediaType.APPLICATION_JSON).content(createBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.origin").value("ICN"))
				.andExpect(jsonPath("$.currency").value("KRW"))
				.andExpect(jsonPath("$.alertRule").value("NEW_LOW"))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.id");

		// First poll: the SIMULATOR source yields one quote; first observation is a new low.
		mockMvc.perform(post("/api/watches/{id}/poll", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.newPrices", hasSize(1)))
				.andExpect(jsonPath("$.newPrices[0].source").value("SIMULATOR"))
				.andExpect(jsonPath("$.lowestAmount").isNumber())
				.andExpect(jsonPath("$.lowestDeepLink", containsString("simulator")))
				.andExpect(jsonPath("$.newLow").value(true));

		// Second poll appends to the time-series.
		mockMvc.perform(post("/api/watches/{id}/poll", id)).andExpect(status().isOk());

		mockMvc.perform(get("/api/watches/{id}/prices", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].source").value("SIMULATOR"));
	}

	@Test
	void time_window_persists_and_calendar_returns_cheapest_per_date() throws Exception {
		String body = """
				{
				  "userRef": "cal-user",
				  "origin": "ICN",
				  "destination": "NRT",
				  "tripType": "ONE_WAY",
				  "departDateFrom": "2026-08-01",
				  "departDateTo": "2026-08-05",
				  "departTimeFrom": "06:00",
				  "departTimeTo": "12:00"
				}
				""";
		String created = mockMvc.perform(post("/api/watches")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.departTimeFrom", containsString("06:00")))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.id");

		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/watches/{id}/poll", id)).andExpect(status().isOk());
		}

		mockMvc.perform(get("/api/watches/{id}/calendar", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].date").exists())
				.andExpect(jsonPath("$[0].lowestAmount").isNumber());
	}

	@Test
	void missing_watch_is_404() throws Exception {
		mockMvc.perform(get("/api/watches/{id}", "00000000-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
	}

	@Test
	void invalid_iata_code_is_400() throws Exception {
		String bad = """
				{
				  "userRef": "u",
				  "origin": "ICNX",
				  "destination": "NRT",
				  "tripType": "ONE_WAY",
				  "departDateFrom": "2026-07-20",
				  "departDateTo": "2026-07-24"
				}
				""";
		mockMvc.perform(post("/api/watches")
						.contentType(MediaType.APPLICATION_JSON).content(bad))
				.andExpect(status().isBadRequest());
	}
}
