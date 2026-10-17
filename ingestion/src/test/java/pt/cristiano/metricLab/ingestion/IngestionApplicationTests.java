package pt.cristiano.metricLab.ingestion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IngestionApplicationTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void receivesReadingAndRejectsMissingFields() throws Exception {
		String reading = """
				{"temperature":23,"humidity":55,"timestamp":"2026-10-16T12:00:00Z","roomName":"room 1","floorNumber":2}
				""";
		mvc.perform(get("/active-sources")).andExpect(content().string("0"));
		mvc.perform(post("/readings").contentType(MediaType.APPLICATION_JSON).content(reading)
				.header("X-Source-Id", "source-a"))
				.andExpect(status().isOk());
		mvc.perform(post("/readings").contentType(MediaType.APPLICATION_JSON).content(reading)
				.header("X-Source-Id", "source-a"))
				.andExpect(status().isOk());
		mvc.perform(post("/readings").contentType(MediaType.APPLICATION_JSON).content(reading)
				.header("X-Source-Id", "source-b"))
				.andExpect(status().isOk());
		mvc.perform(post("/readings").contentType(MediaType.APPLICATION_JSON).content(reading)
				.header("X-Source-Id", "x".repeat(64)))
				.andExpect(status().isOk());
		mvc.perform(post("/readings").contentType(MediaType.APPLICATION_JSON).content("{}")
				.header("X-Source-Id", "source-c"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/active-sources")).andExpect(content().string("2"));
	}

}
