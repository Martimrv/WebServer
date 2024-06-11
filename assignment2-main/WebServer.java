import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Represents a simple web server.
 */
public class WebServer {

    /**
     * Main Method.
     * @param args
     */
    public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("Usage: java ConcurrentWebServer <port> <public_folder>");
			System.exit(1);
		}

		int port = Integer.parseInt(args[0]);
		String publicFolder = args[1];

		try (ServerSocket serverSocket = new ServerSocket(port)) {
			System.out.println("Server is listening on port " + port);

			while (true) {
				Socket clientSocket = serverSocket.accept();
				System.out.println("Connection established with " + clientSocket.getInetAddress());

				// Create a new thread to handle the client request
				Thread clientThread = new Thread(() -> handleClientRequest(clientSocket, publicFolder));
				clientThread.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    /**
     * Method to check if request = GET or POST.
     * Type of file for each request. 
     * @param clientSocket the socket.
     * @param publicFolder we add this when we want to run the program.
     */
    private static void handleClientRequest(Socket clientSocket, String publicFolder) {
		try (
				InputStreamReader readsRequest = new InputStreamReader(clientSocket.getInputStream());
				BufferedReader clientInput = new BufferedReader(readsRequest);
				BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
			String request = in.readLine();
			System.out.println("Received request: " + request);

			if (request != null && request.startsWith("GET")) { // GET Request
				String[] requestParts = request.split("\\s+");
				String method = requestParts[0];
				String requestedPath = requestParts[1];
				String htmlPath = null;
				String pngPath = null;

				// If the path ends with "/", append "index.html"
				if (requestedPath.endsWith("/")) {
					requestedPath += "index.html";
				}

				// if path ends with .html
				if (requestedPath.endsWith(".html")) {
					htmlPath += requestedPath; // Add query parameters back to the
				}

				// if path ends with .png
				if (requestedPath.endsWith(".png")) {
					pngPath += requestedPath;
				}

				if (requestedPath.contains("..")) {
					// Prevent directory traversal attacks
					sendErrorResponse(out, 403, "Forbidden");
					return;
				}

				String filePath = publicFolder + requestedPath;
				System.out.println("Method: " + method);
				System.out.println("Requested Path: " + requestedPath);
				System.out.println("Resolved File Path: " + filePath);

				File file = new File(filePath);

				if (file.exists()) {
					if (file.isFile()) {
						System.out.println("Requested resource is a file.");
						serveFile(out, filePath);
					} else if (file.isDirectory()) {
						System.out.println("Requested resource is a directory.");
						// Check if the directory contains an index.html file
						File indexFile = new File(file, "index.html");
						if (indexFile.exists() && indexFile.isFile()) {
							System.out.println("Serving index.html from the directory.");
							serveFile(out, indexFile.getAbsolutePath());
						} else {
							// Directory doesn't contain index.html, you may handle this case accordingly
							System.out.println("Directory doesn't contain index.html.");
							sendErrorResponse(out, 404, "Not Found");
						}
					}
				} else {
					if (requestedPath.equals("/redirect")) {
						// For 302 response, redirect to a specific URL
						sendErrorResponse(out, 302, "Found");
					} else {
						System.out.println("Requested resource does not exist.");
						sendErrorResponse(out, 404, "Not Found");
					}
				}
			} else if (request != null && request.startsWith("POST")) { // Handling POST request
				String[] requestParts = request.split("\\s+");
				String method = requestParts[0];
				String requestedPath = requestParts[1];
				System.out.println("Received request: " + request);

				if (requestedPath.equals("/login.html")) {
					// It's login request ----- handleLogin
					handleLoginRequest(in, out);

				} else if (requestedPath.equals("/upload.html")) {
					// It's upload request ----- handleUpload
				}

			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				clientSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
    /**
     * Method to 
     * @param out writes data to Client.
     * @param filePath path.
     * Reads one chunck of data at the time.
     */
	private static void serveFile(DataOutputStream out, String filePath) {
		try (FileInputStream fileInputStream = new FileInputStream(filePath)) {
			byte[] buffer = new byte[20000];
			byte[] fullContent = new byte[0];
			int bytesRead;

			out.writeBytes("HTTP/1.1 200 OK\r\n");
			out.writeBytes("Content-Type: " + getContentType(filePath) + "\r\n");
			

			// Full Content will give us the length of the content.
			while ((bytesRead = fileInputStream.read(buffer)) != -1) {
				byte[] newContent = new byte[bytesRead + fullContent.length]; // Byte array with byReads+fullContent size.
				System.arraycopy(fullContent, 0, newContent, 0, fullContent.length); // copies fullcontent to newContent
				System.arraycopy(buffer, 0, newContent, fullContent.length, bytesRead); // copies buffer to newContent
				fullContent = newContent;
			}
			
			out.writeBytes("Content-Length: " + fullContent.length + "\r\n\r\n");

			out.write(fullContent);

		} catch (FileNotFoundException e) {
			// File not found
			System.out.println("File not found: " + filePath);
			sendErrorResponse(out, 404, "Not Found");
		} catch (IOException e) {
			System.out.println("Internal Server Error: " + e.getMessage());
			sendErrorResponse(out, 500, "Internal Server Error");
		}
	}
    /**
     * Deals to Error responses.
     * @param out data written to Client.
     * @param statusCode
     * @param statusText
     */
	private static void sendErrorResponse(DataOutputStream out, int statusCode, String statusText) {
		try {
			if (statusCode == 404) {
				// For 404 response, provide a custom error page or message
				out.writeBytes("HTTP/1.1 404 Not Found\r\n");
				out.writeBytes("Content-Type: text/html\r\n\r\n");
				out.writeBytes(
						"<html><body><h1>404 Not Found</h1><p>The requested resource was not found.</p></body></html>");
			} else if (statusCode == 500) {
				// For 500 response, provide a custom error page or message
				out.writeBytes("HTTP/1.1 500 Internal Server Error\r\n");
				out.writeBytes("Content-Type: text/html\r\n\r\n");
				out.writeBytes(
						"<html><body><h1>500 Internal Server Error</h1><p>An internal server error occurred.</p></body></html>");
			} else if (statusCode == 302) {
				// For 302 response, redirect to a specific URL
				out.writeBytes("HTTP/1.1 302 Found\r\n");
				out.writeBytes("Location: http://example.com/redirected-page.html\r\n\r\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    /**
     * Gets Content Type.
     * @param filePath path of file.
     * @return
     */
	private static String getContentType(String filePath) {
		if (filePath.endsWith(".html")) {
			return "text/html";
		} else if (filePath.endsWith(".png")) {
			return "image/png";
		}
		return "application/octet-stream";
	}

	/**
	 * Handles the POST request --- Login
	 */
	private static void handleLoginRequest(BufferedReader clientInput, DataOutputStream out) throws IOException {

		// Read the request body to extract login credentials
		StringBuilder requestBodyBuilder = new StringBuilder();
		String line;
		while (clientInput.ready()){
			requestBodyBuilder.append((char) clientInput.read());
		}
		String requestBody = requestBodyBuilder.toString();

		// Parse login credentials from the request body
		String[] requestParts = requestBody.toString().split("\r\n\r\n"); // identify what we actually want from the request.
		String[] credentials = requestParts[1].toString().split("&"); // & separates username of password
		System.out.println(credentials[1]);
		String username = null;
		String password = null;
		for (String credential : credentials) {
			String[] keyValuePair = credential.split("="); 
			if (keyValuePair.length == 2) {
				if (keyValuePair[0].equals("username")) {
					username = keyValuePair[1];
				} else if (keyValuePair[0].equals("password")) {
					password = keyValuePair[1];
				}
			}
		}

		// Perform login verification
		boolean isAuthenticated = verifyLogin(username, password);

		// Send a response based on login verification result
		if (isAuthenticated) {
			out.writeBytes("HTTP/1.1 200 OK\r\n");
			out.writeBytes("Content-Type: text/plain " + "\r\n\r\n");
		} else {
			System.out.println("Login failed");
			sendErrorResponse(out, 404, "Not Found");
		}
		out.flush();
	}

    /**
     * Verifies Login.
     * @param username the username.
     * @param password the password.
     * @return
     */
	private static boolean verifyLogin(String username, String password) {
		// Implement logic to verify login credentials
		// Read login information from LOGIN_FILE_PATH
		String loginFilePath = "public\\login.txt";
		try (BufferedReader reader = new BufferedReader(new FileReader(loginFilePath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split(":"); // separator of both values.
				if (parts.length == 2 && parts[0].equals(username) && parts[1].equals(password)) {
					System.out.println("Login Successful");
					return true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
}
