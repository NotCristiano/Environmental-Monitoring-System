package pt.cristiano.metricLab.ingestion;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ReadingController {

	private static final Logger log = LoggerFactory.getLogger(ReadingController.class);
	private final ConcurrentMap<String, Instant> sourceLastSeen = new ConcurrentHashMap<>();

	@PostMapping("/readings")
	void receive(@RequestBody Reading reading, @RequestHeader(value = "X-Source-Id", required = false) String sourceId) {
		if (reading.temperature() == null || reading.humidity() == null || reading.timestamp() == null
				|| reading.roomName() == null || reading.roomName().isBlank() || reading.floorNumber() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "all reading fields are required");
		}
		if (sourceId != null && !sourceId.isBlank() && sourceId.length() <= 63) {
			sourceLastSeen.put(sourceId, Instant.now());
		}
		log.info("{} {}C {}% {} floor={}", reading.timestamp(), reading.temperature(), reading.humidity(),
				reading.roomName(), reading.floorNumber());
	}

	@GetMapping("/active-sources")
	int activeSources() {
		Instant cutoff = Instant.now().minusSeconds(30);
		// shortcut: stale IDs are removed when status is read, add periodic cleanup if status is rarely used.
		sourceLastSeen.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
		return sourceLastSeen.size();
	}

	record Reading(Integer temperature, Integer humidity, Instant timestamp, String roomName, Integer floorNumber) {}
}
