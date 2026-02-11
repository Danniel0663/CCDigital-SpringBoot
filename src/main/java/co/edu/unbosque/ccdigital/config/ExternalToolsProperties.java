package co.edu.unbosque.ccdigital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Representa las propiedades de configuración para la ejecución de herramientas externas
 * utilizadas por CCDigital ntegraciones con Hyperledger Fabric e Indy.
 *
 * <p>Estas propiedades se cargan automáticamente desde el archivo de configuración de
 * Spring {@code application.properties} o {@code application.yml})
 * usando el prefijo {@code ccdigital.tools}.</p>
 *
 * <p>Se recomienda validar que las rutas existan y que el usuario del proceso de la aplicación
 * tenga permisos de lectura y ejecución sobre los scripts.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 1.0
 */
@Component
@ConfigurationProperties(prefix = "ccdigital.tools")
public class ExternalToolsProperties {

    /**
     * Configuración del bloque de herramientas relacionadas con Hyperledger Fabric.
     */
    private Fabric fabric = new Fabric();

    /**
     * Configuración del bloque de herramientas relacionadas con Hyperledger Indy.
     */
    private Indy indy = new Indy();

    /**
     * Retorna la configuración de herramientas para Fabric.
     *
     * @return configuración de Fabric (rutas y script)
     */
    public Fabric getFabric() {
        return fabric;
    }

    /**
     * Establece la configuración de herramientas para Fabric.
     *
     * @param fabric configuración de Fabric
     */
    public void setFabric(Fabric fabric) {
        this.fabric = fabric;
    }

    /**
     * Retorna la configuración de herramientas para Indy.
     *
     * @return configuración de Indy (rutas, comando de venv y script)
     */
    public Indy getIndy() {
        return indy;
    }

    /**
     * Establece la configuración de herramientas para Indy.
     *
     * @param indy configuración de Indy
     */
    public void setIndy(Indy indy) {
        this.indy = indy;
    }

    /**
     * Agrupa las propiedades necesarias para ejecutar procesos asociados a Hyperledger Fabric.
     *
     * <p>Normalmente se utiliza para ejecutar el script que sincroniza la base de datos con el ledger,
     * por ejemplo {@code sync-db-to-ledger.js}.</p>
     */
    public static class Fabric {

        /**
         * Directorio de trabajo donde se encuentra el script (work directory).
         *
         * <p>Ejemplo:
         * {@code /home/ccdigital/fabric/fabric-samples/test-network/client}</p>
         */
        private String workdir;

        /**
         * Nombre del script a ejecutar dentro de {@link #workdir}.
         *
         * <p>Ejemplo: {@code sync-db-to-ledger.js}</p>
         */
        private String script;

        /**
         * Retorna el directorio de trabajo donde se encuentra el script.
         *
         * @return ruta del directorio de trabajo
         */
        public String getWorkdir() {
            return workdir;
        }

        /**
         * Establece el directorio de trabajo donde se encuentra el script.
         *
         * @param workdir ruta del directorio de trabajo
         */
        public void setWorkdir(String workdir) {
            this.workdir = workdir;
        }

        /**
         * Retorna el nombre del script a ejecutar.
         *
         * @return nombre del script
         */
        public String getScript() {
            return script;
        }

        /**
         * Establece el nombre del script a ejecutar.
         *
         * @param script nombre del script
         */
        public void setScript(String script) {
            this.script = script;
        }
    }

    /**
     * Agrupa las propiedades necesarias para ejecutar procesos asociados a Hyperledger Indy.
     *
     * <p>Normalmente se utiliza para lanzar un script Python que emite credenciales a partir
     * de información almacenada en base de datos.</p>
     */
    public static class Indy {

        /**
         * Directorio de trabajo del proyecto Python relacionado con Indy.
         *
         * <p>Ejemplo: {@code /home/ccdigital/cdigital-indy-python}</p>
         */
        private String workdir;

        /**
         * Comando utilizado para activar el entorno virtual (venv) antes de ejecutar el script.
         *
         * <p>Ejemplo: {@code source venv/bin/activate}</p>
         *
         * <p><b>Nota:</b> en muchos casos este comando se ejecuta dentro de un shell,
         * por lo que la implementación que consuma esta propiedad debe considerar el
         * intérprete (bash/sh) utilizado.</p>
         */
        private String venvActivate;

        /**
         * Nombre del script Python a ejecutar dentro de {@link #workdir}.
         *
         * <p>Ejemplo: {@code issue_credentials_from_db.py}</p>
         */
        private String script;

        /**
         * Retorna el directorio de trabajo del proyecto Python.
         *
         * @return ruta del directorio de trabajo
         */
        public String getWorkdir() {
            return workdir;
        }

        /**
         * Establece el directorio de trabajo del proyecto Python.
         *
         * @param workdir ruta del directorio de trabajo
         */
        public void setWorkdir(String workdir) {
            this.workdir = workdir;
        }

        /**
         * Retorna el comando de activación del entorno virtual (venv).
         *
         * @return comando para activar venv
         */
        public String getVenvActivate() {
            return venvActivate;
        }

        /**
         * Establece el comando de activación del entorno virtual (venv).
         *
         * @param venvActivate comando para activar venv
         */
        public void setVenvActivate(String venvActivate) {
            this.venvActivate = venvActivate;
        }

        /**
         * Retorna el nombre del script Python a ejecutar.
         *
         * @return nombre del script
         */
        public String getScript() {
            return script;
        }

        /**
         * Establece el nombre del script Python a ejecutar.
         *
         * @param script nombre del script
         */
        public void setScript(String script) {
            this.script = script;
        }
    }
}
