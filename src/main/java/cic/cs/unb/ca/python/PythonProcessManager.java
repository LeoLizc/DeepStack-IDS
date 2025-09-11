package cic.cs.unb.ca.python;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class PythonProcessManager {
    private static final Logger logger = LoggerFactory.getLogger(PythonProcessManager.class);
    private static PythonProcessManager instance;

    private Process process;
    private BufferedWriter writer;
    private final ExecutorService executor;
    private BufferedReader reader;
    private BufferedReader errorReader;
    private final List<Consumer<String>> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object writeLock = new Object();
    private final Thread shutdownHook;

    private PythonProcessManager() throws IOException {
        try {
            // Registrar shutdown hook ANTES de iniciar el proceso
            shutdownHook = new Thread(this::gracefulShutdown);
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            startPythonProcess();
            executor = Executors.newCachedThreadPool();
            running.set(true);
            startResponseReader();
            startErrorReader();
            logger.info("Python process manager initialized successfully");
        } catch (IOException e) {
            logger.error("Failed to initialize Python process manager", e);
            throw e;
        }
    }

    public static synchronized PythonProcessManager getInstance() {
        return instance;
    }

    public static synchronized void init() throws IOException {
        if (instance == null) {
            instance = new PythonProcessManager();
        }
    }

    private String getPythonExecutablePath() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        Path venvPath = Paths.get("src").toAbsolutePath()
            .resolve("main")
            .resolve("frida-ddos-models")
            .resolve(".venv");

        Path pythonPath;
        if (os.contains("win")) {
            // Windows: venv\Scripts\python.exe
            pythonPath = venvPath.resolve("Scripts").resolve("python.exe");
        } else {
            // Linux/Mac: venv/bin/python
            pythonPath = venvPath.resolve("bin").resolve("python");
        }

        // Verificar que el ejecutable existe
        if (!Files.exists(pythonPath)) {
            throw new IOException("Python executable not found at: " + pythonPath);
        }

        return pythonPath.toString();
    }

    private void startPythonProcess() throws IOException {
        try {
            // Obtener ruta absoluta del script Python
            Path projectDir = Paths.get(System.getProperty("user.dir"));
            Path pythonScript = projectDir.resolve("src/main/frida-ddos-models/main.py");
            String pythonPath = getPythonExecutablePath();

            // Verificar que el archivo existe
            if (!Files.exists(pythonScript)) {
                throw new FileNotFoundException("Script Python no encontrado: " + pythonScript);
            }

            /*ProcessBuilder pb = new ProcessBuilder(
                    "C:\\Users\\leoli\\anaconda3\\Scripts\\conda", "run", "--no-capture-output", "-n", "cicflowmeter",
                    "python", "-u", pythonScript.toString()
            );*/
            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, "-u", pythonScript.toString()
            );

            // Redirigir errores a un stream separado
            pb.redirectErrorStream(false);
            process = pb.start();

            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            logger.info("Python process started");
        } catch (IOException e) {
            logger.error("Failed to start Python process", e);
            throw e;
        }
    }

    private void startResponseReader() {
        executor.submit(() -> {
            try {
                logger.info("Response reader thread started");
                while (running.get()) {
                    try {
                        String response = reader.readLine();
                        if (response == null) {
                            logger.info("Python process stream closed");
                            break;
                        }
                        logger.info("Received response: {}", response);
                        if (response.startsWith("[INFO]")) {
                            // Ignore mensajes de info
                            continue;
                        }
                        notifySubscribers(response.split(";")[0].trim());
                    } catch (IOException e) {
                        if (running.get()) {
                            logger.error("Error reading from Python process", e);
                        }
                        break;
                    }
                }
            } finally {
                logger.info("Response reader thread exiting");
            }
        });
    }

    private void startErrorReader() {
        executor.submit(() -> {
            try {
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    System.err.println("[PYTHON ERROR] " + errorLine);
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Error leyendo errores: " + e.getMessage());
                }
            }
        });
    }

    public void subscribe(Consumer<String> subscriber) {
        subscribers.add(subscriber);
        logger.debug("New subscriber added. Total subscribers: {}", subscribers.size());
    }

    public void unsubscribe(Consumer<String> subscriber) {
        subscribers.remove(subscriber);
        logger.debug("Subscriber removed. Total subscribers: {}", subscribers.size());
    }

    private void notifySubscribers(String response) {
        for (Consumer<String> subscriber : subscribers) {
            try {
                subscriber.accept(response);
            } catch (Exception e) {
                logger.error("Subscriber notification failed", e);
            }
        }
    }

    public void sendMsg(String jsonInput) {
        synchronized (writeLock) {
            try {
                logger.info("Sending JSON data: {}", jsonInput);
                writer.write(jsonInput);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                logger.error("Failed to send data to Python process", e);
                // Opcional: reiniciar el proceso
                restartProcess();
            }
        }
    }

    private void restartProcess() {
        logger.warn("Attempting to restart Python process");
        gracefulShutdown();
        try {
            startPythonProcess();
            startResponseReader();
            startErrorReader();
            logger.info("Python process restarted successfully");
        } catch (IOException e) {
            logger.error("Critical failure: Unable to restart Python process", e);
            running.set(false);
        }
    }

    private void sendTerminationSignal() {
        try {
            // Enviar señal especial de terminación
            writer.write("__TERMINATE__");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            // Ignorar si ya está cerrado
        }
    }

    public synchronized void gracefulShutdown() {
        if (!running.getAndSet(false)) return;

        try {
            // 1. Enviar señal de terminación
            sendTerminationSignal();

            // 2. Cerrar streams de escritura
            closeResource(writer, "Writer");

            // 3. Esperar terminación pacífica con timeout
            if (process != null && process.isAlive()) {
                boolean exited = process.waitFor(3, TimeUnit.SECONDS);
                if (!exited) {
                    System.err.println("Forzando terminación del proceso Python");
                    process.destroyForcibly().waitFor(1, TimeUnit.SECONDS);
                }
            }

            // 4. Cerrar streams de lectura
            closeResource(reader, "Reader");
            closeResource(errorReader, "Error Reader");

            // 5. Apagar executor
            if (executor != null) {
                executor.shutdown();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Shutdown interrumpido: " + e.getMessage());
        } finally {
            // 6. Eliminar shutdown hook para evitar duplicados
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // Ya en proceso de shutdown
            }

            // 7. Confirmar terminación
            System.out.println("Python manager shutdown completo");
        }
    }

    private void closeResource(Closeable resource, String name) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                System.err.println("Error cerrando " + name + ": " + e.getMessage());
            }
        }
    }

    public static void shutdown() {
        if (instance != null) {
            instance.gracefulShutdown();
            instance = null;
            logger.info("PythonProcessManager shutdown complete");
        } else {
            logger.warn("PythonProcessManager was not initialized or already shut down");
        }
    }
}