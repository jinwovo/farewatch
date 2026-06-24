package com.portfolio.farewatch.seed;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Loads airport reference data into the {@code airport} table on first startup
 * (from classpath:data/airports.tsv — OurAirports, public domain). Idempotent:
 * skips if the table already has rows. Uses a JDBC batch insert so seeding ~3k
 * rows is sub-second even on a fresh Testcontainers database.
 */
@Component
public class AirportSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AirportSeeder.class);

	private final JdbcTemplate jdbc;

	public AirportSeeder(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		Integer existing = jdbc.queryForObject("select count(*) from airport", Integer.class);
		if (existing != null && existing > 0) {
			return;
		}
		List<Object[]> batch = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new ClassPathResource("data/airports.tsv").getInputStream(), StandardCharsets.UTF_8))) {
			reader.readLine(); // header
			String line;
			while ((line = reader.readLine()) != null) {
				String[] f = line.split("\t", -1);
				if (f.length < 7) {
					continue;
				}
				batch.add(new Object[] {
						f[0],                                   // iata
						f[2],                                   // name
						f[3].isBlank() ? null : f[3],           // municipality
						f[4],                                   // country
						Double.parseDouble(f[5]),               // lat
						Double.parseDouble(f[6]),               // lon
						"L".equals(f[1])                        // large
				});
			}
		}
		jdbc.batchUpdate(
				"insert into airport (iata, name, municipality, country, lat, lon, large) values (?, ?, ?, ?, ?, ?, ?)",
				batch);
		log.info("seeded {} airports", batch.size());
	}
}
