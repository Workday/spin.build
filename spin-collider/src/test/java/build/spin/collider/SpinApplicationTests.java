package build.spin.collider;

import build.spawn.application.Console;
import build.spawn.application.option.Argument;
import build.spawn.jdk.option.SystemProperty;
import build.spawn.platform.local.LocalMachine;
import build.spin.option.ServerPort;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpinApplicationTests {

    /**
     * Test to ensure client can establish tcp connection with {@link SpinApplication}.
     *
     * @throws Exception
     */
    @Test
    public void shouldConnect()
        throws Exception {
        // start spin in collider
        startSpin();

        try (Socket socket = new Socket("localhost", ServerPort.DEFAULT.get())) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            final var symbolRequest = """
                {
                  "jsonrpc": "2.0",
                  "id": 0,
                  "method": "textDocument/documentSymbol",
                  "params": {
                    "textDocument": {
                      "uri": "test.wombl"
                    }
                  }
                }""";

            // send a request to server
            try {
                out.write(toMessage(symbolRequest));
                out.flush();
            }
            catch (final Exception e) {
                System.out.println("Failed to send request to server " + e.getMessage());
            }

            // should receive the server response
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            assertTrue(reader.readLine().contains("Content-Length"));
        }
        catch (final SocketException | SocketTimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] toMessage(final String payload) {
        final var message = String.format("Content-Length: %d\r\n\r\n%s", payload.getBytes().length, payload);
        return message.getBytes();
    }

    /**
     * Start {@link SpinApplication} in collider.
     *
     * @return the {@link SpinApplication}
     */
    public static SpinApplication startSpin() throws Exception {

        var server = LocalMachine.get().launch(
            SpinApplication.class,
            Console.ofSystem(),
            SystemProperty.of("java.net.preferIPv4Stack", true),
            Argument.of("--server"));

        server.onStart().get();

        return server;
    }
}
