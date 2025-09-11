package cic.cs.unb.ca.notify;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HttpClientUtil {
    private static final Logger logger = LogManager.getLogger(HttpClientUtil.class);
    private static final int TIMEOUT_MILLIS = 5000;

    public static String sendGet(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MILLIS);
            conn.setReadTimeout(TIMEOUT_MILLIS);

            int status = conn.getResponseCode();
            String response = readStream(status >= 200 && status < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream());

            if (status < 200 || status >= 300) {
                logger.error("GET {} → HTTP {}: {}", urlStr, status, response);
                throw new HttpRequestException(status, response);
            }

            logger.debug("GET {} → HTTP {}: {}", urlStr, status, response);
            return response;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static String sendPost(String urlStr, String body) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MILLIS);
            conn.setReadTimeout(TIMEOUT_MILLIS);

            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", Integer.toString(bytes.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }

            int status = conn.getResponseCode();
            String response = readStream(status >= 200 && status < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream());

            if (status < 200 || status >= 300) {
                logger.error("POST {} → HTTP {}: {}", urlStr, status, response);
                throw new HttpRequestException(status, response);
            }

            logger.debug("POST {} → HTTP {}: {}", urlStr, status, response);
            return response;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * Excepción personalizada para peticiones HTTP fallidas.
     */
    public static class HttpRequestException extends Exception {
        private final int statusCode;
        private final String errorBody;

        public HttpRequestException(int statusCode, String errorBody) {
            super("HTTP " + statusCode + " → " + errorBody);
            this.statusCode = statusCode;
            this.errorBody = errorBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getErrorBody() {
            return errorBody;
        }
    }
}
