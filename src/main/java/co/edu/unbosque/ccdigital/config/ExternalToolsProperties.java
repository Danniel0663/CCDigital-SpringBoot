package co.edu.unbosque.ccdigital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Propiedades de configuración para la ejecución de herramientas externas.
 *
 * <p>
 * Mapea la configuración bajo el prefijo {@code external-tools}. Su objetivo es centralizar rutas,
 * ejecutables y scripts requeridos para interactuar con componentes externos (por ejemplo, CLI de Fabric).
 * </p>
 *
 * <h2>Notas</h2>
 * <ul>
 *   <li>Esta clase se registra vía {@code @ConfigurationPropertiesScan} (no requiere {@code @Component}).</li>
 *   <li>La sub-sección {@code external-tools.fabric} agrupa los parámetros de Fabric.</li>
 * </ul>
 *
 * @since 1.0
 */
@ConfigurationProperties(prefix = "external-tools")
public class ExternalToolsProperties {

    private final Fabric fabric = new Fabric();

    /**
     * Retorna la configuración asociada a Fabric.
     *
     * @return configuración de Fabric
     */
    public Fabric getFabric() {
        return fabric;
    }

    /**
     * Configuración específica para ejecución de scripts/CLI asociados a Fabric.
     *
     * <p>
     * Permite definir el directorio de trabajo, el ejecutable de Node y rutas de scripts.
     * </p>
     *
     * @since 1.0
     */
    public static class Fabric {

        /**
         * Directorio base para ejecutar comandos.
         *
         * <p>Por defecto se toma {@code user.dir}.</p>
         */
        private String workdir = System.getProperty("user.dir");

        /**
         * Script genérico asociado a la integración (si aplica).
         */
        private String script = "fabric-cli.js";

        /**
         * Ejecutable de Node.js utilizado para lanzar scripts.
         */
        private String nodeBin = "node";

        /**
         * Script específico para listar documentos.
         *
         * <p>Puede ser una ruta relativa al {@link #workdir} o una ruta absoluta.</p>
         */
        private String listDocsScript =
                "/home/ccdigital/fabric/fabric-samples/test-network/client/list-docs.js";

        public String getWorkdir() {
            return workdir;
        }

        public void setWorkdir(String workdir) {
            this.workdir = workdir;
        }

        public String getScript() {
            return script;
        }

        public void setScript(String script) {
            this.script = script;
        }

        public String getNodeBin() {
            return nodeBin;
        }

        public void setNodeBin(String nodeBin) {
            this.nodeBin = nodeBin;
        }

        public String getListDocsScript() {
            return listDocsScript;
        }

        public void setListDocsScript(String listDocsScript) {
            this.listDocsScript = listDocsScript;
        }

        /**
         * Retorna el {@link Path} normalizado del directorio de trabajo configurado.
         *
         * @return ruta absoluta y normalizada del {@code workdir}
         */
        public Path workdirPath() {
            return Paths.get(workdir).toAbsolutePath().normalize();
        }
    }
}
