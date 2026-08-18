package com.portfolio.farewatch;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
 * Home-dashboard slice: the summary endpoint returns each watch with a live price,
 * buy signal and sparkline in ONE call, and PATCH pauses/resumes tracking.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WatchDashboardApiTest {

	@Autowired
	MockMvc mockMvc;

	private String createWatch(String userRef) throws Exception {
		String body = """
				{
				  "userRef": "%s",
				  "origin": "ICN",
				  "destination": "NRT",
				  "tripType": "ONE_WAY",
				  "departDateFrom": "2026-10-20",
				  "departDateTo": "2026-10-24"
				}
				""".formatted(userRef);
		String created = mockMvc.perform(post("/api/watches")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(created, "$.id");
	}

	@Test
	void summary_returns_price_signal_and_spark_in_one_call() throws Exception {
		String id = createWatch("dash-user");
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/watches/{id}/poll", id)).andExpect(status().isOk());
		}

		mockMvc.perform(get("/api/watches/summary").param("userRef", "dash-user"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].watch.id").value(id))
				.andExpect(jsonPath("$[0].watch.origin").value("ICN"))
				.andExpect(jsonPath("$[0].latestAmount").isNumber())
				.andExpect(jsonPath("$[0].latestObservedAt").exists())
				.andExpect(jsonPath("$[0].signal.recommendation").exists())
				.andExpect(jsonPath("$[0].signal.score").isNumber())
				// 3 polls all landed today → at least one sparkline day cell
				.andExpect(jsonPath("$[0].spark", hasSize(greaterThanOrEqualTo(1))))
				.andExpect(jsonPath("$[0].spark[0].amount").isNumber());
	}

	@Test
	void summary_of_unpolled_watch_has_no_data_signal_and_empty_spark() throws Exception {
		createWatch("dash-empty-user");

		mockMvc.perform(get("/api/watches/summary").param("userRef", "dash-empty-user"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].latestAmount").doesNotExist())
				.andExpect(jsonPath("$[0].signal.recommendation").value("NO_DATA"))
				.andExpect(jsonPath("$[0].spark", hasSize(0)));
	}

	@Test
	void patch_pauses_and_resumes_a_watch() throws Exception {
		String id = createWatch("pause-user");

		mockMvc.perform(patch("/api/watches/{id}", id)
						.contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/watches/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(patch("/api/watches/{id}", id)
						.contentType(MediaType.APPLICATION_JSON).content("{\"active\": true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));

		// empty body is a no-op, not an error
		mockMvc.perform(patch("/api/watches/{id}", id)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void patch_missing_watch_is_404() throws Exception {
		mockMvc.perform(patch("/api/watches/{id}", "00000000-0000-0000-0000-000000000000")
						.contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
				.andExpect(status().isNotFound());
	}
}
