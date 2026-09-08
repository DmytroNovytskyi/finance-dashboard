package com.financedashboard.infrastructure.fx;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NbpClientTest {

    private HttpServer server;
    private NbpClient client;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/exchangerates/rates/a/", this::handleRate);
        server.start();
        client = new NbpClient("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private void handleRate(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.contains("/USD/2026-03-10/")) {
            byte[] body = """
                    {"table":"A","currency":"dolar amerykański","code":"USD",
                     "rates":[{"no":"001/A/NBP/2026","effectiveDate":"2026-03-10","mid":4.1234}]}
                    """.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }
    }

    @Test
    void parsesMidRateWhenPublished() {
        assertThat(client.midRate("USD", LocalDate.of(2026, 3, 10)))
                .contains(new BigDecimal("4.1234"));
    }

    @Test
    void returnsEmptyWhenNoRatePublished() {
        assertThat(client.midRate("USD", LocalDate.of(2026, 3, 8))).isEmpty();
    }
}
