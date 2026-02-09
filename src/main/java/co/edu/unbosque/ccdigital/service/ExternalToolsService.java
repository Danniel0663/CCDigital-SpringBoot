package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.config.ExternalToolsProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Servicio para ejecutar integraciones externas (Fabric e Indy) mediante comandos del sistema.
 *
 * <p>Los comandos se parametrizan por configuración a través de {@link ExternalToolsProperties}.</p>
 */
@Service
public class ExternalToolsService {

    private final ExternalToolsProperties props;

    /**
     * Crea el servicio.
     *
     * @param props propiedades de herramientas externas
     */
    public ExternalToolsService(ExternalToolsProperties props) {
        this.props = props;
    }

    /**
     * Ejecuta la sincronización de todos los registros hacia Fabric, usando el script configurado.
     *
     * @return resultado de ejecución del proceso
     */
    public ExecResult runFabricSyncAll() {
        String workdir = safe(props.getFabric().getWorkdir());
        String script = safe(props.getFabric().getScript());

        String cmd = "node " + shellQuote(script) + " --all";
        return runCommand(workdir, cmd);
    }

    /**
     * Ejecuta sincronización hacia Fabric para una persona específica.
     *
     * @param idType tipo de identificación
     * @param idNumber número de identificación
     * @return resultado de ejecución del proceso
     */
    public ExecResult runFabricSyncPerson(String idType, String idNumber) {
        String workdir = safe(props.getFabric().getWorkdir());
        String script = safe(props.getFabric().getScript());

        String cmd = "node " + shellQuote(script) + " --person " + shellQuote(idType) + " " + shellQuote(idNumber);
        return runCommand(workdir, cmd);
    }

    /**
     * Alias de compatibilidad: sincronización total Fabric.
     *
     * @return resultado de ejecución
     */
    public ExecResult runFabricAll() {
        return runFabricSyncAll();
    }

    /**
     * Alias de compatibilidad: sincronización de persona Fabric.
     *
     * @param idType tipo de identificación
     * @param idNumber número de identificación
     * @return resultado de ejecución
     */
    public ExecResult runFabricPerson(String idType, String idNumber) {
        return runFabricSyncPerson(idType, idNumber);
    }

    /**
     * Ejecuta la emisión de credenciales Indy a partir de la base de datos, usando el script configurado.
     *
     * @return resultado de ejecución del proceso
     */
    public ExecResult runIndyIssueFromDb() {
        String workdir = safe(props.getIndy().getWorkdir());
        String venvActivate = safe(props.getIndy().getVenvActivate());
        String script = safe(props.getIndy().getScript());

        String cmd = "cd " + shellQuote(workdir) + " && " + venvActivate + " && python3 " + shellQuote(script);
        return runCommand(workdir, cmd);
    }

    /**
     * Alias de compatibilidad: emisión Indy.
     *
     * @return resultado de ejecución
     */
    public ExecResult runIndyIssue() {
        return runIndyIssueFromDb();
    }

    /**
     * Ejecuta un comando del sistema usando {@code bash -lc} y captura stdout/stderr.
     *
     * @param workdir directorio de trabajo; si es nulo o vacío, se usa el directorio por defecto del proceso
     * @param cmd comando a ejecutar
     * @return resultado de ejecución con salida estándar y de error
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

            Thread tOut = new Thread(() -> readStream(p.getInputStream(), out));
            Thread tErr = new Thread(() -> readStream(p.getErrorStream(), err));
            tOut.start();
            tErr.start();

            code = p.waitFor();
            tOut.join();
            tErr.join();

            return new ExecResult(workdir, cmd, code, out.toString(), err.toString());

        } catch (Exception e) {
            err.append("Excepción ejecutando comando: ").append(e.getMessage()).append('\n');
            return new ExecResult(workdir, cmd, code, out.toString(), err.toString());
        }
    }

    /**
     * Lee un {@link java.io.InputStream} y acumula su contenido en un {@link StringBuilder}.
     *
     * @param is stream de entrada
     * @param sb acumulador de salida
     */
    private void readStream(java.io.InputStream is, StringBuilder sb) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception ex) {
            sb.append("Excepción leyendo stream: ").append(ex.getMessage()).append('\n');
        }
    }

    /**
     * Normaliza una cadena para evitar nulos y recortar espacios.
     *
     * @param s cadena original
     * @return cadena segura
     */
    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Realiza quoting seguro para argumentos en shell.
     *
     * @param s argumento
     * @return argumento entrecomillado para shell
     */
    private String shellQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    /**
     * Resultado de una ejecución externa.
     */
    public static class ExecResult {
        private final String workingDir;
        private final String command;
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        /**
         * Construye un resultado de ejecución.
         *
         * @param workingDir directorio de trabajo usado
         * @param command comando ejecutado
         * @param exitCode código de salida del proceso
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

        public String getWorkingDir() { return workingDir; }
        public String getCommand() { return command; }
        public int getExitCode() { return exitCode; }
        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
    }
}
