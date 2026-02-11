package co.edu.unbosque.ccdigital.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Servicio de integración que ejecuta comandos del sistema operativo para invocar herramientas externas.
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Service
public class IntegrationCommandService {

    /**
     * Ejecuta un comando del sistema usando {@code /bin/bash -lc} y retorna su salida en texto.
     *
     * <p>La salida incluye todas las líneas generadas por el proceso y al final agrega el
     * código de salida (exit code).</p>
     *
     * <p><b>Implementación:</b> combina stdout+stderr en un mismo stream
     * ({@code pb.redirectErrorStream(true)}).</p>
     *
     * @param command comando completo a ejecutar en bash
     * @return salida del comando (incluye exit code) o un mensaje de error si falla la ejecución
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
     * Ejecuta la sincronización completa hacia Hyperledger Fabric.
     *
     * <p>Comando ejecutado (hardcoded):</p>
     * <pre>
	 * cd /home/ccdigital/fabric/fabric-samples/test-network/client &amp;&amp; node sync-db-to-ledger.js --all
	 * </pre>
     *
     * @return salida del comando y código de salida
     */
    public String syncFabricAll() {
        String cmd = "cd /home/ccdigital/fabric/fabric-samples/test-network/client&& node sync-db-to-ledger.js --all";
        return runCommand(cmd);
    }

    /**
     * Ejecuta la sincronización hacia Hyperledger Fabric para una persona específica.
     *
     * @param idType tipo de identificación (ej. {@code CC}, {@code TI})
     * @param idNumber número de identificación
     * @return salida del comando y código de salida
     */
    public String syncFabricPerson(String idType, String idNumber) {
        String cmd = "cd /home/ccdigital/fabric/fabric-samples/test-network/client && node sync-db-to-ledger.js --person "
                + idType + " " + idNumber;
        return runCommand(cmd);
    }

    /**
     * Ejecuta la emisión de credenciales Indy desde base de datos.
     *
     * @return salida del comando y código de salida
     */
    public String syncIndyIssueAll() {
        String cmd = "cd /home/ccdigital/cdigital-indy-python && " +
                "source venv/bin/activate && " +
                "python3 issue_credentials_from_db.py";
        return runCommand(cmd);
    }
}
