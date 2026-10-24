# NIO-Pulse 🚀

NIO-Pulse is a lightweight, high-performance, non-blocking HTTP/1.1 web server engineered entirely from scratch using **Java NIO (`java.nio.channels.*`)** and standard concurrency utilities (`java.util.concurrent.*`). 

Unlike traditional thread-per-connection servers that bottleneck under heavy load, NIO-Pulse utilizes an event-driven `Selector` architecture coupled with a worker thread pool to process concurrent requests asynchronously with **zero external dependencies**.

---

## 🌟 Key Features

* **Non-Blocking I/O Architecture**: Powered by Java NIO `ServerSocketChannel`, `SocketChannel`, and `Selector` to manage thousands of concurrent connections efficiently.
* **Asynchronous Request Processing**: Offloads heavy lifting from the main I/O thread loop to a dynamic worker thread pool (`ThreadPoolExecutor`).
* **Custom HTTP/1.1 Parser**: Built-in stateless parser that reads and structures raw request lines, headers, and payloads.
* **Modular Routing Engine**: Clean functional routing syntax for mapping endpoints to custom `RouteHandler` logic supporting JSON and HTML responses.
* **Zero Dependencies**: Written completely in core Java with no external libraries or frameworks (no Netty, no Spring, no Apache Commons).

---

## 📁 Project Structure

```text
.
├── HttpServer.java          # Main server class, NIO event loop, routes, and utilities
└── README.md                # Project documentation
```

---

## ⚙️ Prerequisites

* **Java Development Kit (JDK)**: Version 8 or higher.

---

## 🚀 Getting Started

### 1. Save the Source Code
Save the core server logic into a file named `HttpServer.java`.

### 2. Compile the Code
Open your terminal in the directory containing the source file and run:
```bash
javac HttpServer.java
```

### 3. Run the Server
Start the server instance:
```bash
java HttpServer
```

You should see output similar to this:
```text
HTTP Server started successfully on port 8080
Press Ctrl+C to stop.
```

---

## 📦 Usage & API Endpoints

You can test the server using `curl` or any web browser while it is running on port `8080`.

### 1. Home Endpoint (`GET /`)
* **Request:**
  ```bash
  curl http://localhost:8080/
  ```
* **Response:**
  ```html
  <h1>Welcome to Java NIO Web Server</h1><p>Engineered entirely from scratch in pure Java.</p>
  ```

### 2. Status / Health Check (`GET /api/status`)
* **Request:**
  ```bash
  curl http://localhost:8080/api/status
  ```
* **Response:**
  ```json
  {"status":"UP","activeThreads":4,"timestamp":1711234567890}
  ```

### 3. Not Found Handling (`GET /invalid-route`)
* **Request:**
  ```bash
  curl -i http://localhost:8080/invalid-route
  ```
* **Response:**
  ```http
  HTTP/1.1 404 Not Found
  Content-Type: text/plain; charset=UTF-8
  Content-Length: 13
  Connection: close

  404 Not Found
  ```

---

## 🛠️ Extending the Server

You can easily register new custom routes inside the `registerRoutes()` method of `HttpServer.java`:

```java
router.get("/api/greet", (req, res) -> {
    res.setStatusCode(200);
    res.setHeader("Content-Type", "application/json; charset=UTF-8");
    res.setBody("{\"message\": \"Hello, World!\"}");
});
```

---
