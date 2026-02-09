package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.config.ExternalToolsProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Servicio encargado de ejecutar herramientas externas requeridas por el proyecto CCDigital.
 *
 * <p>Actualmente soporta ejecución de comandos para:</p>
 *
 * <p>Los parámetros de ejecución (directorios, scripts, activación de venv) se cargan desde configuración
 * usando {@link ExternalToolsProperties} (por ejemplo en {@code application.properties} con prefijo
 * {@code ccdigital.tools}).</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Service
public class ExternalToolsService {

    /**
     * Propiedades externas para ejecución de herramientas (Fabric/Indy).
     */
    private final ExternalToolsProperties props;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param props propiedades de herramientas externas
     */
    public ExternalToolsService(ExternalToolsProperties props) {
        this.props = props;
    }
    
    /**
     * Ejecuta la sincronización completa hacia Hyperledger Fabric.
     *
     * <p>El directorio de trabajo y el nombre del script se toman de:
     * {@code ccdigital.tools.fabric.workdir} y {@code ccdigital.tools.fabric.script}.</p>
     *
     * @return resultado de ejecución con código de salida y salida estándar/error
     */
    public ExecResult runFabricSyncAll() {
        String workdir = safe(props.getFabric().getWorkdir());
        String script = safe(props.getFabric().getScript());

        // node sync-db-to-ledger.js --all
        String cmd = "node " + script + " --all";
        return runCommand(workdir, cmd);
    }

    /**
     * Ejecuta la sincronización hacia Hyperledger Fabric para una persona específica.
     * 
     * @param idType tipo de identificación (por ejemplo: {@code CC}, {@code TI}, etc.)
     * @param idNumber número de identificación
     * @return resultado de ejecución con código de salida y salida estándar/error
     */
    public ExecResult runFabricSyncPerson(String idType, String idNumber) {
        String workdir = safe(props.getFabric().getWorkdir());
        String script = safe(props.getFabric().getScript());

        // node sync-db-to-ledger.js --person CC 1019983896
        String cmd = "node " + script + " --person " + safe(idType) + " " + safe(idNumber);
        return runCommand(workdir, cmd);
    }

    /**
     * Alias de compatibilidad para controladores antiguos.
     *
     * <p>Equivale a {@link #runFabricSyncAll()}.</p>
     *
     * @return resultado de ejecución
     */
    public ExecResult runFabricAll() {
        return runFabricSyncAll();
    }

    /**
     * Alias de compatibilidad para controladores antiguos.
     *
     * <p>Equivale a {@link #runFabricSyncPerson(String, String)}.</p>
     *
     * @param idType tipo de identificación
     * @param idNumber número de identificación
     * @return resultado de ejecución
     */
    public ExecResult runFabricPerson(String idType, String idNumber) {
        return runFabricSyncPerson(idType, idNumber);
    }

    /**
     * Ejecuta el proceso de emisión de credenciales en Indy tomando datos desde base de datos.
     *
     * <p>Se ejecuta usando {@code bash -lc} para que {@code source} y variables de entorno funcionen.</p>
     *
     * @return resultado de ejecución con código de salida y salida estándar/error
     */
    public ExecResult runIndyIssueFromDb() {
        String workdir = safe(props.getIndy().getWorkdir());
        String venvActivate = safe(props.getIndy().getVenvActivate());
        String script = safe(props.getIndy().getScript());

        // cd workdir && source venv/bin/activate && python3 issue_credentials_from_db.py
        // Nota: corremos en bash -lc para que "source" funcione
        String cmd = "cd " + shellQuote(workdir) + " && " + venvActivate + " && python3 " + script;
        return runCommand(workdir, cmd);
    }

    /**
     * Alias de compatibilidad para controladores que llamen a {@code runIndyIssue()}.
     *
     * <p>Equivale a {@link #runIndyIssueFromDb()}.</p>
     *
     * @return resultado de ejecución
     */
    public ExecResult runIndyIssue() {
        return runIndyIssueFromDb();
    }

    /**
     * Ejecuta un comando en un directorio de trabajo opcional usando {@code bash -lc}.
     *
     * <p>Se capturan stdout y stderr en paralelo para evitar bloqueos por buffers.</p>
     *
     * @param workdir directorio donde se ejecutará el comando (puede ser vacío/nulo)
     * @param cmd comando a ejecutar (string que se pasa a {@code bash -lc})
     * @return resultado de ejecución con salida y código de retorno
     */
    private ExecResult runCommand(String workdir, String cmd) {
        List<String> command = List.of("bash", "-lc", cmd);

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workdir != null && !workdir.isBlank()) {
            pb.directory(new File(workdir));
        }
        pb.redirectErrorStream(false);

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = -1;

        try {
            Process p = pb.start();

            // Leer stdout/stderr en paralelo para evitar bloqueos
            Thread tOut = new Thread(() -> readStream(p.getInputStream(), out));
            Thread tErr = new Thread(() -> readStream(p.getErrorStream(), err));
            tOut.start();
            tErr.start();

            code = p.waitFor();
            tOut.join();
            tErr.join();

            return new ExecResult(workdir, cmd, code, out.toString(), err.toString());

        } catch (Exception e) {
            err.append("Exception ejecutando comando: ").append(e.getMessage()).append('\n');
            return new ExecResult(workdir, cmd, code, out.toString(), err.toString());
        }
    }

    /**
     * Lee un {@link java.io.InputStream} línea por línea y lo acumula en un {@link StringBuilder}.
     *
     * @param is stream de entrada
     * @param sb acumulador donde se agregan las líneas
     */
    private void readStream(java.io.InputStream is, StringBuilder sb) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception ex) {
            sb.append("Exception leyendo stream: ").append(ex.getMessage()).append('\n');
        }
    }

    /**
     * Normaliza un string para evitar {@code null} y espacios extremos.
     *
     * @param s texto de entrada
     * @return string sin {@code null} y con {@code trim()}
     */
    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Encapsula un texto en comillas simples para uso en comandos de shell (por ejemplo en {@code cd}),
     * escapando comillas simples internas.
     *
     * @param s texto a encapsular
     * @return texto seguro para usar en shell dentro de comillas simples
     */
    private String shellQuote(String s) {
        if (s == null) return "";
        // envuelve en comillas simples y escapa comillas simples internas
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    /**
     * DTO que representa el resultado de ejecutar un comando externo.
     *
     * <p>Contiene información útil para depuración</p>
     */
    public static class ExecResult {

        /**
         * Directorio de trabajo donde se ejecutó el comando.
         */
        private final String workingDir;

        /**
         * Comando ejecutado (string pasado a {@code bash -lc}).
         */
        private final String command;

        /**
         * Código de salida del proceso. Por convención, {@code 0} indica éxito.
         */
        private final int exitCode;

        /**
         * Salida estándar del proceso.
         */
        private final String stdout;

        /**
         * Salida de error del proceso.
         */
        private final String stderr;

        /**
         * Crea un resultado de ejecución.
         *
         * @param workingDir directorio de trabajo
         * @param command comando ejecutado
         * @param exitCode código de salida
         * @param stdout salida estándar
         * @param stderr salida de error
         */
        public ExecResult(String workingDir, String command, int exitCode, String stdout, String stderr) {
            this.workingDir = workingDir;
            this.command = command;
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        /**
         * Retorna el directorio de trabajo.
         *
         * @return directorio de trabajo
         */
        public String getWorkingDir() {
            return workingDir;
        }

        /**
         * Retorna el comando ejecutado.
         *
         * @return comando ejecutado
         */
        public String getCommand() {
            return command;
        }

        /**
         * Retorna el código de salida.
         *
         * @return código de salida
         */
        public int getExitCode() {
            return exitCode;
        }

        /**
         * Retorna la salida estándar.
         *
         * @return stdout
         */
        public String getStdout() {
            return stdout;
        }

        /**
         * Retorna la salida de error.
         *
         * @return stderr
         */
        public String getStderr() {
            return stderr;
        }
    }
}
