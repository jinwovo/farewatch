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
 * Loads airport reference data on first startup (classpath:data/airports.tsv —
 * OurAirports, public domain) and applies Korean search aliases
 * (classpath:data/airports-ko.tsv) so "서울"/"도쿄" match. Both steps are
 * idempotent and run via JDBC batch, so they are sub-second on a fresh database.
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
		seedAirports();
		applyAliases();
	}

	private void seedAirports() throws Exception {
		Integer existing = jdbc.queryForObject("select count(*) from airport", Integer.class);
		if (existing != null && existing > 0) {
			return;
		}
		List<Object[]> batch = new ArrayList<>();
		try (BufferedReader reader = open("data/airports.tsv")) {
			reader.readLine(); // header
			String line;
			while ((line = reader.readLine()) != null) {
				String[] f = line.split("\t", -1);
				if (f.length < 7) {
					continue;
				}
				batch.add(new Object[] {
						f[0], f[2], f[3].isBlank() ? null : f[3], f[4],
						Double.parseDouble(f[5]), Double.parseDouble(f[6]), "L".equals(f[1])
				});
			}
		}
		jdbc.batchUpdate(
				"insert into airport (iata, name, municipality, country, lat, lon, large) values (?, ?, ?, ?, ?, ?, ?)",
				batch);
		log.info("seeded {} airports", batch.size());
	}

	private void applyAliases() throws Exception {
		Integer withAliases = jdbc.queryForObject("select count(*) from airport where aliases is not null", Integer.class);
		if (withAliases != null && withAliases > 0) {
			return;
		}
		List<Object[]> batch = new ArrayList<>();
		try (BufferedReader reader = open("data/airports-ko.tsv")) {
			reader.readLine(); // header
			String line;
			while ((line = reader.readLine()) != null) {
				int tab = line.indexOf('\t');
				if (tab <= 0) {
					continue;
				}
				batch.add(new Object[] { line.substring(tab + 1).trim(), line.substring(0, tab).trim() });
			}
		}
		int[] updated = jdbc.batchUpdate("update airport set aliases = ? where iata = ?", batch);
		int hit = 0;
		for (int u : updated) {
			hit += u;
		}
		log.info("applied Korean aliases to {} airports", hit);
	}

	private BufferedReader open(String path) throws Exception {
		return new BufferedReader(new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8));
	}
}
