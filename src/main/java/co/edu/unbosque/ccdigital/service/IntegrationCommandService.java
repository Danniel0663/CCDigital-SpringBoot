package co.edu.unbosque.ccdigital.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Service
public class IntegrationCommandService {

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

    public String syncFabricAll() {
        String cmd = "cd /home/ccdigital/fabric/fabric-samples/test-network/client&& node sync-db-to-ledger.js --all";
        return runCommand(cmd);
    }

    public String syncFabricPerson(String idType, String idNumber) {
        String cmd = "cd /home/ccdigital/fabric/fabric-samples/test-network/client && node sync-db-to-ledger.js --person "
                + idType + " " + idNumber;
        return runCommand(cmd);
    }

    public String syncIndyIssueAll() {
        String cmd = "cd /home/ccdigital/cdigital-indy-python && " +
                "source venv/bin/activate && " +
                "python3 issue_credentials_from_db.py";
        return runCommand(cmd);
    }
}
