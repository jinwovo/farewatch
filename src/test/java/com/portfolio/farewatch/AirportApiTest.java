package com.portfolio.farewatch;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Airport autocomplete over the real seeded reference data (OurAirports).
 * Shares the MockMvc context with the other web tests, so the seeder runs once.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AirportApiTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void search_by_iata_code() throws Exception {
		mockMvc.perform(get("/api/airports").param("q", "ICN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].iata").value("ICN"))
				.andExpect(jsonPath("$[0].municipality").value("Seoul"));
	}

	@Test
	void search_by_city_name() throws Exception {
		mockMvc.perform(get("/api/airports").param("q", "Tokyo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.iata=='HND')]").exists());
	}

	@Test
	void search_by_korean_alias_returns_seoul_airports() throws Exception {
		mockMvc.perform(get("/api/airports").param("q", "서울"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.iata=='ICN')]").exists())
				.andExpect(jsonPath("$[?(@.iata=='GMP')]").exists());
	}

	@Test
	void search_by_korean_alias_works_worldwide() throws Exception {
		mockMvc.perform(get("/api/airports").param("q", "프랑크푸르트"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.iata=='FRA')]").exists());
	}

	@Test
	void nearby_returns_airports_with_distance() throws Exception {
		mockMvc.perform(get("/api/airports/{iata}/nearby", "ICN").param("limit", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)))
				.andExpect(jsonPath("$[0].distanceKm").isNumber())
				.andExpect(jsonPath("$[?(@.iata=='GMP')]").exists()); // Gimpo is right next to Incheon
	}

	@Test
	void unknown_airport_nearby_is_404() throws Exception {
		mockMvc.perform(get("/api/airports/{iata}/nearby", "ZZZ"))
				.andExpect(status().isNotFound());
	}
}
