package pt.cristiano.metricLab.source;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.mock.env.MockEnvironment;

@SpringBootTest
class SourceApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void createsSensorReading() {
		String reading = SourceApplication.randomReading();
		assertInstanceOf(Integer.class, JsonPath.read(reading, "$.temperature"));
		assertInstanceOf(Integer.class, JsonPath.read(reading, "$.humidity"));
		assertInstanceOf(Integer.class, JsonPath.read(reading, "$.floorNumber"));
		Instant.parse(JsonPath.read(reading, "$.timestamp"));
		assertTrue(Set.of("room 1", "room 2", "room 3", "room 4").contains(JsonPath.read(reading, "$.roomName")));
	}

	@Test
	void sendsRequestedCountAndContinuesAfterFailure() throws Exception {
		AtomicInteger received = new AtomicInteger();
		AtomicReference<String> sourceId = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/readings", exchange -> {
			sourceId.set(exchange.getRequestHeaders().getFirst("X-Source-Id"));
			int status = received.incrementAndGet() == 1 ? 500 : 204;
			exchange.sendResponseHeaders(status, -1);
			exchange.close();
		});
		server.start();
		try {
			MockEnvironment environment = new MockEnvironment();
			environment.setConversionService(new ApplicationConversionService());
			environment.withProperty("source.url", "http://127.0.0.1:" + server.getAddress().getPort() + "/readings")
					.withProperty("source.id", "source-a")
					.withProperty("source.rate", "100")
					.withProperty("source.count", "3")
					.withProperty("source.timeout", "1s")
					.withProperty("source.stop-on-error", "false");
			assertThrows(IOException.class, () -> SourceApplication.sendReadings(environment));
			assertEquals(3, received.get());
			assertEquals("source-a", sourceId.get());
		} finally {
			server.stop(0);
		}
	}

	@Test
	void rejectsInvalidRate() {
		MockEnvironment environment = new MockEnvironment().withProperty("source.rate", "0");
		assertThrows(IllegalArgumentException.class, () -> SourceApplication.sendReadings(environment));
	}

	@Test
	void durationEndsBeforeNextScheduledSend() throws Exception {
		AtomicInteger received = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/readings", exchange -> {
			received.incrementAndGet();
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});
		server.start();
		try {
			MockEnvironment environment = new MockEnvironment();
			environment.setConversionService(new ApplicationConversionService());
			environment.withProperty("source.url", "http://127.0.0.1:" + server.getAddress().getPort() + "/readings")
					.withProperty("source.rate", "0.5")
					.withProperty("source.duration", "100ms");
			assertTimeout(Duration.ofSeconds(1), () -> SourceApplication.sendReadings(environment));
			assertEquals(1, received.get());
		} finally {
			server.stop(0);
		}
	}

	@Test
	void zeroCountKeepsSendingUntilDurationEnds() throws Exception {
		AtomicInteger received = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/readings", exchange -> {
			received.incrementAndGet();
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});
		server.start();
		try {
			MockEnvironment environment = new MockEnvironment();
			environment.setConversionService(new ApplicationConversionService());
			environment.withProperty("source.url", "http://127.0.0.1:" + server.getAddress().getPort() + "/readings")
					.withProperty("source.rate", "50")
					.withProperty("source.count", "0")
					.withProperty("source.duration", "1s");
			assertTimeout(Duration.ofSeconds(3), () -> SourceApplication.sendReadings(environment));
			assertTrue(received.get() > 1);
		} finally {
			server.stop(0);
		}
	}

}
