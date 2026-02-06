package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.config.ExternalToolsProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ExternalToolsService {

    private final ExternalToolsProperties props;

    public ExternalToolsService(ExternalToolsProperties props) {
        this.props = props;
    }

    // =======================
    // FABRIC
    // =======================

    /** Método principal (el que ya tenías) */
    public ExecResult runFabricSyncAll() {
        String workdir = safe(props.getFabric().getWorkdir());
        String script = safe(props.getFabric().getScript());

        // node sync-db-to-ledger.js --all
        String cmd = "node " + script + " --all";
        return runCommand(workdir, cmd);
    }

    /** Método principal (el que ya tenías) */
    public ExecResult runFabricSyncPerson(String idType, String idNumber) {
        String workdir = safe(props.getFabric().getWorkdir());
        String script = safe(props.getFabric().getScript());

        // node sync-db-to-ledger.js --person CC 1019983896
        String cmd = "node " + script + " --person " + safe(idType) + " " + safe(idNumber);
        return runCommand(workdir, cmd);
    }

    // ✅ Aliases para compatibilidad con tu AdminController (errores: runFabricAll/runFabricPerson)
    public ExecResult runFabricAll() {
        return runFabricSyncAll();
    }

    public ExecResult runFabricPerson(String idType, String idNumber) {
        return runFabricSyncPerson(idType, idNumber);
    }

    // =======================
    // INDY
    // =======================

    public ExecResult runIndyIssueFromDb() {
        String workdir = safe(props.getIndy().getWorkdir());
        String venvActivate = safe(props.getIndy().getVenvActivate());
        String script = safe(props.getIndy().getScript());

        // cd workdir && source venv/bin/activate && python3 issue_credentials_from_db.py
        // Nota: corremos en bash -lc para que "source" funcione
        String cmd = "cd " + shellQuote(workdir) + " && " + venvActivate + " && python3 " + script;
        return runCommand(workdir, cmd);
    }

    // ✅ Alias por si en tu controller está como runIndyIssue()
    public ExecResult runIndyIssue() {
        return runIndyIssueFromDb();
    }

    // =======================
    // EJECUCIÓN GENÉRICA
    // =======================

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

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    // Para rutas con espacios cuando construimos "cd ..."
    private String shellQuote(String s) {
        if (s == null) return "";
        // envuelve en comillas simples y escapa comillas simples internas
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    // =======================
    // DTO RESULTADO (Thymeleaf friendly)
    // =======================
    public static class ExecResult {
        private final String workingDir;
        private final String command;
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public ExecResult(String workingDir, String command, int exitCode, String stdout, String stderr) {
            this.workingDir = workingDir;
            this.command = command;
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public String getWorkingDir() {
            return workingDir;
        }

        public String getCommand() {
            return command;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }
    }
}
