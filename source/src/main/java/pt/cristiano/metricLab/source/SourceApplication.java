package pt.cristiano.metricLab.source;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class SourceApplication {

	public static void main(String[] args) throws Exception {
		try (ConfigurableApplicationContext context = SpringApplication.run(SourceApplication.class, args)) {
			sendReadings(context.getEnvironment());
		}
	}

	static void sendReadings(Environment environment) throws IOException, InterruptedException {
		String url = environment.getProperty("source.url", "http://localhost:8080/readings");
		String sourceId = environment.getProperty("source.id", "local");
		double rate = environment.getProperty("source.rate", Double.class, 1.0);
		long count = environment.getProperty("source.count", Long.class,
				environment.containsProperty("source.rate") || environment.containsProperty("source.duration")
						? Long.MAX_VALUE : 1L);
		Duration duration = environment.getProperty("source.duration", Duration.class);
		Duration timeout = environment.getProperty("source.timeout", Duration.class, Duration.ofSeconds(10));
		boolean stopOnError = environment.getProperty("source.stop-on-error", Boolean.class, true);
		if (!Double.isFinite(rate) || rate <= 0 || count < 0 || duration != null && duration.isNegative()
				|| duration != null && duration.isZero() || timeout.isNegative() || timeout.isZero()) {
			throw new IllegalArgumentException("rate, duration, and timeout must be positive; count must be nonnegative");
		}
		URI endpoint = URI.create(url);
		if (!"http".equals(endpoint.getScheme()) && !"https".equals(endpoint.getScheme())) {
			throw new IllegalArgumentException("source.url must be an HTTP or HTTPS URL");
		}

		long intervalNanos = Math.max(1, Math.round(1_000_000_000d / rate));
		long durationNanos = duration == null ? Long.MAX_VALUE : duration.toNanos();
		long failures = 0;
		try (HttpClient client = HttpClient.newHttpClient()) {
			long started = System.nanoTime();
			long nextSend = started;
			for (long sent = 0; count == 0 || sent < count; sent++) {
				long remainingNanos = durationNanos - (System.nanoTime() - started);
				if (remainingNanos <= 0) {
					break;
				}
				long waitNanos = nextSend - System.nanoTime();
				if (waitNanos > 0) {
					TimeUnit.NANOSECONDS.sleep(Math.min(waitNanos, remainingNanos));
				}
				if (System.nanoTime() - started >= durationNanos) {
					break;
				}
				nextSend = System.nanoTime() + intervalNanos;
				String reading = randomReading();
				HttpRequest request = HttpRequest.newBuilder(endpoint)
						.header("Content-Type", "application/json")
						.header("X-Source-Id", sourceId)
						.timeout(timeout)
						.POST(HttpRequest.BodyPublishers.ofString(reading))
						.build();
				try {
					int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
					if (status < 200 || status >= 300) {
						throw new IOException("Ingestion returned HTTP " + status);
					}
					System.out.println("Sent reading: " + reading);
				} catch (IOException e) {
					if (stopOnError) {
						throw e;
					}
					failures++;
					System.err.println("Send failed: " + e.getMessage());
				}
			}
		}
		if (failures > 0) {
			throw new IOException(failures + " send(s) failed");
		}
	}

	static String randomReading() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		String[] rooms = {"room 1", "room 2", "room 3", "room 4"};
		return """
				{"temperature":%d,"humidity":%d,"timestamp":"%s","roomName":"%s","floorNumber":%d}
				""".formatted(random.nextInt(40), random.nextInt(100), Instant.now(),
				rooms[random.nextInt(rooms.length)], random.nextInt(10));
	}

}
