package co.edu.unbosque.ccdigital.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de integración para ejecutar herramientas externas (por ejemplo, scripts de Fabric/Indy)
 * y capturar un resultado estructurado.
 *
 * <p>Este servicio centraliza:</p>
 * <ul>
 *   <li>La construcción y ejecución de comandos mediante {@link ProcessBuilder}.</li>
 *   <li>La captura de stdout y stderr en paralelo para evitar bloqueos por buffers.</li>
 *   <li>El control de timeout para procesos que no finalizan.</li>
 *   <li>La configuración vía properties (workdir, binarios, scripts y timeout).</li>
 * </ul>
 *
 * <p>Las rutas y nombres de scripts se inyectan con {@link Value} a partir del archivo de configuración
 * (por ejemplo, {@code application.properties} / {@code application.yml}).</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Service
public class ExternalToolsService {

    /**
     * Resultado estándar para ejecución de comandos externos.
     *
     * <p>Se usa como DTO para devolver:
     * código de salida, salida estándar, error estándar y metadatos del comando.</p>
     *
     * <p>Controladores/servicios consumidores lo referencian como
     * {@code ExternalToolsService.ExecResult}.</p>
     */
    public static class ExecResult {

        /**
         * Código de salida del proceso (0 indica éxito por convención).
         */
        private final int exitCode;

        /**
         * Salida estándar (stdout) capturada del proceso.
         */
        private final String stdout;

        /**
         * Salida de error (stderr) capturada del proceso.
         */
        private final String stderr;

        /**
         * Instante en que se inició la ejecución del proceso.
         */
        private final Instant startedAt;

        /**
         * Instante en que finalizó la ejecución del proceso (o se interrumpió por timeout/error).
         */
        private final Instant finishedAt;

        /**
         * Comando ejecutado, representado como lista de tokens.
         */
        private final List<String> command;

        /**
         * Construye un resultado de ejecución.
         *
         * @param exitCode código de salida
         * @param stdout salida estándar
         * @param stderr salida de error
         * @param startedAt instante de inicio
         * @param finishedAt instante de fin
         * @param command comando ejecutado (tokens)
         */
        public ExecResult(int exitCode, String stdout, String stderr,
                          Instant startedAt, Instant finishedAt, List<String> command) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.command = command;
        }

        /**
         * @return código de salida del proceso
         */
        public int getExitCode() { return exitCode; }

        /**
         * @return stdout capturado
         */
        public String getStdout() { return stdout; }

        /**
         * @return stderr capturado
         */
        public String getStderr() { return stderr; }

        /**
         * @return instante de inicio
         */
        public Instant getStartedAt() { return startedAt; }

        /**
         * @return instante de fin
         */
        public Instant getFinishedAt() { return finishedAt; }

        /**
         * @return comando ejecutado como lista de tokens
         */
        public List<String> getCommand() { return command; }

        /**
         * Indica si la ejecución fue exitosa según la convención de exitCode 0.
         *
         * @return {@code true} si {@code exitCode == 0}, en caso contrario {@code false}
         */
        public boolean isOk() { return exitCode == 0; }
    }

    // ============================
    // Properties para scripts externos
    // ============================

    /**
     * Directorio de trabajo (workdir) donde se ejecutan los scripts de Fabric.
     * Si está vacío, la ejecución retorna un error controlado.
     */
    @Value("${external-tools.fabric.workdir:}")
    private String fabricWorkdir;

    /**
     * Binario de Node.js. Por defecto se usa {@code node}.
     */
    @Value("${external-tools.fabric.node-bin:node}")
    private String nodeBin;

    /**
     * Script para sincronización global hacia Fabric.
     * Debe ser una ruta relativa a {@code fabricWorkdir} (recomendado) o una ruta absoluta.
     */
    @Value("${external-tools.fabric.sync-all-script:}")
    private String fabricSyncAllScript;

    /**
     * Script para sincronización por persona hacia Fabric.
     * Debe ser una ruta relativa a {@code fabricWorkdir} (recomendado) o una ruta absoluta.
     */
    @Value("${external-tools.fabric.sync-person-script:}")
    private String fabricSyncPersonScript;

    /**
     * Directorio de trabajo (workdir) donde se ejecutan scripts de Indy (Python).
     */
    @Value("${external-tools.indy.workdir:}")
    private String indyWorkdir;

    /**
     * Comando para activar el entorno virtual de Python (venv).
     * Se ejecuta dentro de {@code bash -lc ...}.
     */
    @Value("${external-tools.indy.venv-activate:source venv/bin/activate}")
    private String indyVenvActivate;

    /**
     * Script de emisión (issuer) para Indy (Python).
     */
    @Value("${external-tools.indy.script:issue_credentials_from_db.py}")
    private String indyScript;

    /**
     * Timeout global en segundos para la ejecución de comandos externos.
     */
    @Value("${external-tools.default-timeout-seconds:180}")
    private long timeoutSeconds;

    // ============================
    // API expuesta para invocación
    // ============================

    /**
     * Ejecuta la sincronización global hacia Fabric.
     *
     * <p>Forma esperada del comando:</p>
     * <pre>
     * node &lt;sync-all-script&gt; --all
     * </pre>
     *
     * @return {@link ExecResult} con stdout/stderr y exitCode
     */
    public ExecResult runFabricSyncAll() {
        if (isBlank(fabricWorkdir)) {
            return controlledError("Falta configurar external-tools.fabric.workdir");
        }
        if (isBlank(fabricSyncAllScript)) {
            return controlledError("Falta configurar external-tools.fabric.sync-all-script");
        }

        return exec(List.of(nodeBin, fabricSyncAllScript, "--all"), fabricWorkdir, Map.of());
    }

    /**
     * Ejecuta la sincronización hacia Fabric para una persona.
     *
     * <p>Forma esperada del comando:</p>
     * <pre>
     * node &lt;sync-person-script&gt; --person &lt;idType&gt; &lt;idNumber&gt;
     * </pre>
     *
     * @param idType tipo de identificación (ej. {@code CC}, {@code TI})
     * @param idNumber número de identificación
     * @return {@link ExecResult} con stdout/stderr y exitCode
     */
    public ExecResult runFabricSyncPerson(String idType, String idNumber) {
        if (isBlank(fabricWorkdir)) {
            return controlledError("Falta configurar external-tools.fabric.workdir");
        }
        if (isBlank(fabricSyncPersonScript)) {
            return controlledError("Falta configurar external-tools.fabric.sync-person-script");
        }
        if (isBlank(idType) || isBlank(idNumber)) {
            return controlledError("idType e idNumber son obligatorios para runFabricSyncPerson");
        }

        return exec(
                List.of(nodeBin, fabricSyncPersonScript, "--person", safe(idType), safe(idNumber)),
                fabricWorkdir,
                Map.of()
        );
    }

    /**
     * Ejecuta la emisión de credenciales Indy (issuer) desde un script Python.
     *
     * <p>Forma esperada del comando (ejecutado en bash):</p>
     * <pre>
     * cd &lt;indyWorkdir&gt; &amp;&amp; source venv/bin/activate &amp;&amp; python3 issue_credentials_from_db.py
     * </pre>
     *
     * @return {@link ExecResult} con stdout/stderr y exitCode
     */
    public ExecResult runIndyIssueFromDb() {
        if (isBlank(indyWorkdir)) {
            return controlledError("Falta configurar external-tools.indy.workdir");
        }
        if (isBlank(indyScript)) {
            return controlledError("Falta configurar external-tools.indy.script");
        }

        String cmd = safe(indyVenvActivate) + " && python3 " + indyScript;
        return exec(List.of("bash", "-lc", cmd), indyWorkdir, Map.of());
    }

    // ============================
    // Ejecución genérica de comandos
    // ============================

    /**
     * Ejecuta un comando externo y retorna un {@link ExecResult} estructurado.
     *
     * <p>Consideraciones de ejecución:</p>
     * <ul>
     *   <li>Si {@code workdir} no está en blanco, se usa como directorio de trabajo del proceso.</li>
     *   <li>{@code extraEnv} se mezcla sobre el entorno del proceso (si aplica).</li>
     *   <li>stdout y stderr se consumen en paralelo.</li>
     *   <li>Si no finaliza en {@code timeoutSeconds}, se fuerza terminación y se retorna exitCode 124.</li>
     * </ul>
     *
     * @param command tokens del comando, por ejemplo {@code List.of("node","script.js","--all")}
     * @param workdir directorio de trabajo (puede ser vacío)
     * @param extraEnv variables de entorno adicionales (puede ser vacío)
     * @return resultado de ejecución con metadatos y salidas
     */
    public ExecResult exec(List<String> command, String workdir, Map<String, String> extraEnv) {
        Instant start = Instant.now();
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();

        Process p = null;

        try {
            ProcessBuilder pb = new ProcessBuilder(command);

            if (!isBlank(workdir)) {
                pb.directory(new File(workdir));
            }

            Map<String, String> env = pb.environment();
            if (extraEnv != null && !extraEnv.isEmpty()) {
                env.putAll(extraEnv);
            }

            p = pb.start();

            Thread tOut = streamToStringBuilder(p.getInputStream(), out);
            Thread tErr = streamToStringBuilder(p.getErrorStream(), err);

            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                joinQuietly(tOut, 1000);
                joinQuietly(tErr, 1000);

                Instant end = Instant.now();
                return new ExecResult(
                        124,
                        out.toString(),
                        err + "\nTimeout ejecutando comando (>" + timeoutSeconds + "s)\n",
                        start,
                        end,
                        new ArrayList<>(command)
                );
            }

            int code = p.exitValue();

            joinQuietly(tOut, 2000);
            joinQuietly(tErr, 2000);

            Instant end = Instant.now();
            return new ExecResult(code, out.toString(), err.toString(), start, end, new ArrayList<>(command));

        } catch (Exception ex) {
            Instant end = Instant.now();
            return new ExecResult(
                    1,
                    out.toString(),
                    err + "\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\n",
                    start,
                    end,
                    command != null ? new ArrayList<>(command) : List.of()
            );
        } finally {
            if (p != null) {
                try { p.getInputStream().close(); } catch (Exception ignored) {}
                try { p.getErrorStream().close(); } catch (Exception ignored) {}
                try { p.getOutputStream().close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Construye un {@link ExecResult} de error controlado (sin ejecutar proceso).
     *
     * @param message detalle del error de configuración/validación
     * @return resultado con exitCode 2 y stderr con el mensaje
     */
    private ExecResult controlledError(String message) {
        Instant now = Instant.now();
        return new ExecResult(2, "", message, now, now, List.of());
    }

    /**
     * Consume un {@link java.io.InputStream} y lo agrega línea por línea a un {@link StringBuilder}.
     *
     * <p>Se ejecuta en un hilo daemon para evitar bloqueo del proceso por buffers llenos.</p>
     *
     * @param is stream a consumir
     * @param sb acumulador de texto
     * @return hilo iniciado que realiza la lectura
     */
    private static Thread streamToStringBuilder(java.io.InputStream is, StringBuilder sb) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } catch (Exception ignored) {
                // No se interrumpe la ejecución principal por fallas de lectura del stream
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Espera un tiempo limitado a que un hilo finalice sin propagar excepción.
     *
     * @param t hilo a esperar (puede ser null)
     * @param millis milisegundos máximos de espera
     */
    private static void joinQuietly(Thread t, long millis) {
        if (t == null) return;
        try { t.join(millis); } catch (Exception ignored) {}
    }

    /**
     * Indica si una cadena es {@code null} o está en blanco.
     *
     * @param s texto a evaluar
     * @return {@code true} si es null o blank; en caso contrario {@code false}
     */
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Normaliza una cadena para uso como token de comando.
     *
     * @param s texto
     * @return texto recortado o cadena vacía si {@code s} es null
     */
    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
