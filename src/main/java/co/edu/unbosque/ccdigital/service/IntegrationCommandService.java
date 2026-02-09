package co.edu.unbosque.ccdigital.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Servicio para ejecutar comandos de integración mediante shell.
 *
 * <p>Este servicio encapsula ejecución de procesos usando {@code /bin/bash -lc} y retorna
 * la salida como texto. Está orientado a integraciones puntuales con Fabric e Indy.</p>
 */
@Service
public class IntegrationCommandService {

    /**
     * Ejecuta un comando en shell y retorna la salida combinada.
     *
     * @param command comando a ejecutar
     * @return salida del proceso junto con el exit code
     */
    private String runCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-lc", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            output.append("\nExit code: ").append(exitCode);
            return output.toString();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error ejecutando comando: " + e.getMessage();
        }
    }

    /**
     * Sincroniza todos los registros hacia Fabric.
     *
     * @return salida del proceso
     */
    public String syncFabricAll() {
        String cmd = "cd /home/ccdigital/fabric/fabric-samples/test-network/client&& node sync-db-to-ledger.js --all";
        return runCommand(cmd);
    }

    /**
     * Sincroniza una persona específica hacia Fabric.
     *
     * @param idType tipo de identificación
     * @param idNumber número de identificación
     * @return salida del proceso
     */
    public String syncFabricPerson(String idType, String idNumber) {
        String cmd = "cd /home/ccdigital/fabric/fabric-samples/test-network/client && node sync-db-to-ledger.js --person "
                + idType + " " + idNumber;
        return runCommand(cmd);
    }

    /**
     * Emite credenciales Indy para todos los registros desde base de datos.
     *
     * @return salida del proceso
     */
    public String syncIndyIssueAll() {
        String cmd = "cd /home/ccdigital/cdigital-indy-python && " +
                "source venv/bin/activate && " +
                "python3 issue_credentials_from_db.py";
        return runCommand(cmd);
    }
}
