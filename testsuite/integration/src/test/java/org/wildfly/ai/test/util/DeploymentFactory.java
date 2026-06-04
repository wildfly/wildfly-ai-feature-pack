package org.wildfly.ai.test.util;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.wildfly.ai.test.container.OllamaContainerManager;

import java.io.File;

/**
 * Factory for creating standardized Arquillian test deployments.
 *
 * <p>This utility class provides factory methods for creating {@link WebArchive} deployments
 * with common configurations needed for WildFly AI integration tests. It reduces code duplication
 * across test classes and ensures consistent deployment structure.</p>
 *
 * <p>The factory provides two main deployment types:</p>
 * <ul>
 *   <li><b>Base deployment</b> - Includes {@link OllamaContainerManager} for tests requiring Ollama</li>
 *   <li><b>Minimal deployment</b> - Excludes Ollama dependencies for local model tests</li>
 * </ul>
 *
 * <p>All deployments include:</p>
 * <ul>
 *   <li>CDI beans.xml descriptor for dependency injection</li>
 *   <li>Test libraries (AssertJ, Hamcrest) for fluent assertions</li>
 *   <li>Optional additional classes specified by test cases</li>
 * </ul>
 *
 * <p><b>Note:</b> Test libraries are copied to {@code target/test-libs} by the
 * maven-dependency-plugin during the build process.</p>
 *
 * @see WebArchive
 * @see OllamaContainerManager
 */
public final class DeploymentFactory {

    private static final String TEST_LIBS_DIR = "target/test-libs";

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private DeploymentFactory() {
        // Utility class, no instances
    }

    /**
     * Locates test library JAR files from the maven-dependency-plugin output directory.
     *
     * <p>The maven-dependency-plugin copies required test dependencies to
     * {@code target/test-libs} during the {@code process-test-classes} phase.
     * These libraries are then included in Arquillian deployments for in-container testing.</p>
     *
     * @return array of File references to all JAR files found in the test-libs directory
     */
    private static File[] getTestLibraries() {
        File dir = new File(TEST_LIBS_DIR);
        File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
        return jars != null ? jars : new File[0];
    }

    /**
     * Creates a base deployment archive for tests requiring Ollama integration.
     *
     * <p>This method creates a {@link WebArchive} configured with:</p>
     * <ul>
     *   <li>{@link OllamaContainerManager} for Ollama container lifecycle management</li>
     *   <li>Test assertion libraries (AssertJ, Hamcrest)</li>
     *   <li>CDI beans.xml descriptor for dependency injection</li>
     *   <li>Any additional classes specified by the caller</li>
     * </ul>
     *
     * <p>Use this deployment type for tests that interact with Ollama-based models
     * (chat models, streaming models, Ollama embeddings).</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * @Deployment
     * public static WebArchive createDeployment() {
     *     return DeploymentFactory.createBaseDeployment("ollama-chat-test.war");
     * }
     * }</pre>
     *
     * @param archiveName the name of the WAR file to create (e.g., "my-test.war")
     * @param additionalClasses optional additional classes to include in the deployment
     * @return a configured WebArchive ready for Arquillian deployment
     * @see #createMinimalDeployment(String, Class[])
     */
    public static WebArchive createBaseDeployment(String archiveName, Class<?>... additionalClasses) {
        WebArchive archive = ShrinkWrap.create(WebArchive.class, archiveName)
                .addClass(OllamaContainerManager.class)
                .addAsLibraries(getTestLibraries())
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");

        // Add any additional classes provided
        if (additionalClasses != null && additionalClasses.length > 0) {
            archive.addClasses(additionalClasses);
        }

        return archive;
    }

    /**
     * Creates a minimal deployment archive for tests not requiring Ollama.
     *
     * <p>This method creates a lightweight {@link WebArchive} configured with:</p>
     * <ul>
     *   <li>Test assertion libraries (AssertJ, Hamcrest)</li>
     *   <li>CDI beans.xml descriptor for dependency injection</li>
     *   <li>Any additional classes specified by the caller</li>
     * </ul>
     *
     * <p>Unlike {@link #createBaseDeployment}, this method does NOT include
     * {@link OllamaContainerManager}, making it suitable for tests that use
     * local models or don't require external AI services.</p>
     *
     * <p>Use this deployment type for tests involving:</p>
     * <ul>
     *   <li>All-MiniLM-L6-v2 local embedding model</li>
     *   <li>In-memory embedding store</li>
     *   <li>Content retrievers with local components</li>
     * </ul>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * @Deployment
     * public static WebArchive createDeployment() {
     *     return DeploymentFactory.createMinimalDeployment(
     *         "embedding-test.war",
     *         MyTestBean.class
     *     );
     * }
     * }</pre>
     *
     * @param archiveName the name of the WAR file to create (e.g., "my-test.war")
     * @param additionalClasses optional additional classes to include in the deployment
     * @return a configured WebArchive ready for Arquillian deployment
     * @see #createBaseDeployment(String, Class[])
     */
    public static WebArchive createMinimalDeployment(String archiveName, Class<?>... additionalClasses) {
        WebArchive archive = ShrinkWrap.create(WebArchive.class, archiveName)
                .addAsLibraries(getTestLibraries())
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");

        // Add any additional classes provided
        if (additionalClasses != null && additionalClasses.length > 0) {
            archive.addClasses(additionalClasses);
        }

        return archive;
    }
}
