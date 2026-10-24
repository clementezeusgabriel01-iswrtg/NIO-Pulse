import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpServer {

    private static final int PORT = 8080;
    private static final int BUFFER_SIZE = 4096;
    private final ExecutorService workerPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    private final Router router = new Router();

    public static void main(String[] args) {
        try {
            new HttpServer().start();
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }

    public void start() throws IOException {
        // Register default routes
        registerRoutes();

        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            serverChannel.bind(new InetSocketAddress(PORT));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("HTTP Server started successfully on port " + PORT);
            System.out.println("Press Ctrl+C to stop.");

            while (true) {
                selector.select(); // Block until events occur
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        acceptConnection(selector, serverChannel);
                    } else if (key.isReadable()) {
                        readData(key);
                    }
                }
            }
        } finally {
            workerPool.shutdown();
        }
    }

    private void acceptConnection(Selector selector, ServerSocketChannel serverChannel) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    private void readData(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                closeChannel(key);
                return;
            }

            buffer.flip();
            byte[] requestBytes = new byte[buffer.remaining()];
            buffer.get(requestBytes);
            String rawRequest = new String(requestBytes, StandardCharsets.UTF_8);

            // Offload parsing and request handling to the thread pool
            workerPool.submit(() -> handleRequest(clientChannel, rawRequest));

        } catch (IOException e) {
            closeChannel(key);
        }
    }

    private void handleRequest(SocketChannel clientChannel, String rawRequest) {
        try {
            HttpRequest request = HttpParser.parse(rawRequest);
            HttpResponse response = new HttpResponse();

            // Route execution
            router.route(request, response);

            byte[] responseBytes = response.getBytes();
            ByteBuffer outBuffer = ByteBuffer.wrap(responseBytes);

            while (outBuffer.hasRemaining()) {
                clientChannel.write(outBuffer);
            }

            // Close connection if keep-alive is not requested
            if (!"keep-alive".equalsIgnoreCase(request.getHeader("Connection"))) {
                clientChannel.close();
            }
        } catch (Exception e) {
            try {
                HttpResponse errorResponse = new HttpResponse();
                errorResponse.setStatusCode(500);
                errorResponse.setBody("Internal Server Error: " + e.getMessage());
                clientChannel.write(ByteBuffer.wrap(errorResponse.getBytes()));
                clientChannel.close();
            } catch (IOException ignored) {}
        }
    }

    private void closeChannel(SelectionKey key) {
        try {
            key.channel().close();
        } catch (IOException ignored) {}
        key.cancel();
    }

    private void registerRoutes() {
        router.get("/", (req, res) -> {
            res.setStatusCode(200);
            res.setHeader("Content-Type", "text/html; charset=UTF-8");
            res.setBody("<h1>Welcome to Java NIO Web Server</h1><p>Engineered entirely from scratch in pure Java.</p>");
        });

        router.get("/api/status", (req, res) -> {
            res.setStatusCode(200);
            res.setHeader("Content-Type", "application/json; charset=UTF-8");
            res.setBody("{\"status\":\"UP\",\"activeThreads\":" + Thread.activeCount() + ",\"timestamp\":" + System.currentTimeMillis() + "}");
        });
    }

    // --- Supporting Inner Classes ---

    public static class HttpRequest {
        private String method;
        private String uri;
        private final Map<String, String> headers = new HashMap<>();
        private String body;

        public String getMethod() { return method; }
        public String getUri() { return uri; }
        public String getHeader(String name) { return headers.get(name); }
        public String getBody() { return body; }
    }

    public static class HttpResponse {
        private int statusCode = 200;
        private final Map<String, String> headers = new HashMap<>();
        private String body = "";

        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
        public void setHeader(String name, String value) { headers.put(name, value); }
        public void setBody(String body) {
            this.body = body;
            headers.put("Content-Length", String.valueOf(body.getBytes(StandardCharsets.UTF_8).length));
        }

        public byte[] getBytes() {
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(getStatusText(statusCode)).append("\r\n");
            headers.putIfAbsent("Server", "JavaNIOHttpServer/1.0");
            headers.putIfAbsent("Connection", "close");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
            sb.append("\r\n").append(body);
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        private String getStatusText(int code) {
            switch (code) {
                case 200: return "OK";
                case 404: return "Not Found";
                case 500: return "Internal Server Error";
                default: return "OK";
            }
        }
    }

    public static class HttpParser {
        public static HttpRequest parse(String rawRequest) {
            HttpRequest request = new HttpRequest();
            String[] lines = rawRequest.split("\r\n");
            if (lines.length == 0) return request;

            // Parse Request Line
            String[] requestLine = lines[0].split(" ");
            if (requestLine.length >= 2) {
                request.method = requestLine[0];
                request.uri = requestLine[1];
            }

            // Parse Headers and Body
            boolean bodyParsed = false;
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.isEmpty()) {
                    bodyParsed = true;
                    continue;
                }
                if (!bodyParsed) {
                    String[] headerParts = line.split(": ", 2);
                    if (headerParts.length == 2) {
                        request.headers.put(headerParts[0], headerParts[1]);
                    }
                } else {
                    bodyBuilder.append(line).append("\n");
                }
            }
            request.body = bodyBuilder.toString().trim();
            return request;
        }
    }

    @FunctionalInterface
    public interface RouteHandler {
        void handle(HttpRequest request, HttpResponse response) throws Exception;
    }

    public static class Router {
        private final Map<String, RouteHandler> getRoutes = new HashMap<>();

        public void get(String path, RouteHandler handler) {
            getRoutes.put(path, handler);
        }

        public void route(HttpRequest request, HttpResponse response) throws Exception {
            RouteHandler handler = null;
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                handler = getRoutes.get(request.getUri());
            }

            if (handler != null) {
                handler.handle(request, response);
            } else {
                response.setStatusCode(404);
                response.setHeader("Content-Type", "text/plain; charset=UTF-8");
                response.setBody("404 Not Found");
            }
        }
    }
}
