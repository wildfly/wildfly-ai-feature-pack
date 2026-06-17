/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.deployment;

import static org.wildfly.extension.ai.AILogger.ROOT_LOGGER;
import static org.wildfly.extension.ai.Capabilities.CHAT_MODEL_PROVIDER_CAPABILITY;
import static org.wildfly.extension.ai.Capabilities.EMBEDDING_MODEL_PROVIDER_CAPABILITY;
import static org.wildfly.extension.ai.Capabilities.EMBEDDING_STORE_PROVIDER_CAPABILITY;

import dev.langchain4j.cdi.spi.RegisterAIService;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.JandexReflection;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.wildfly.extension.ai.Capabilities;

/**
 * Deployment processor for AI module dependencies and service discovery.
 *
 * <p>
 * This processor runs during the {@code DEPENDENCIES} phase of deployment processing
 * and performs two main functions:</p>
 *
 * <h3>1. Module Dependency Management</h3>
 * <p>
 * Automatically adds LangChain4j and AI-related module dependencies to deployments:</p>
 * <ul>
 * <li><b>Exported modules</b> - Always added and re-exported to deployments:
 * <ul>
 * <li>{@code dev.langchain4j} - Core LangChain4j API</li>
 * <li>{@code dev.langchain4j.cdi} - CDI integration</li>
 * <li>{@code org.wildfly.extension.ai.injection} - WildFly AI injection support</li>
 * </ul>
 * </li>
 * <li><b>Optional modules</b> - Added as optional dependencies (only loaded if needed):
 * <ul>
 * <li>{@code dev.langchain4j.chroma} - ChromaDB integration</li>
 * <li>{@code dev.langchain4j.gemini} - Google Gemini models</li>
 * <li>{@code dev.langchain4j.github-models} - GitHub Models marketplace</li>
 * <li>{@code dev.langchain4j.ollama} - Ollama local LLM runtime</li>
 * <li>{@code dev.langchain4j.openai} - OpenAI GPT models</li>
 * <li>{@code dev.langchain4j.mcp-client} - Model Context Protocol</li>
 * <li>{@code dev.langchain4j.mistral-ai} - Mistral AI models</li>
 * <li>{@code dev.langchain4j.neo4j} - Neo4j graph database</li>
 * <li>{@code dev.langchain4j.weaviate} - Weaviate vector database</li>
 * <li>{@code dev.langchain4j.web-search-engines} - Web search integration</li>
 * </ul>
 * </li>
 * </ul>
 *
 * <h3>2. AI Service Discovery</h3>
 * <p>
 * Scans deployment classes for AI service usage via annotations:</p>
 * <ul>
 * <li>{@code @Named} - CDI field injection (e.g., {@code @Inject @Named("ollama") ChatModel model})</li>
 * <li>{@code @RegisterAIService} - LangChain4j AI service registration</li>
 * </ul>
 *
 * <p>
 * When AI services are detected, the processor:</p>
 * <ol>
 * <li>Identifies required service types (chat models, embeddings, stores, etc.)</li>
 * <li>Extracts bean names from annotations</li>
 * <li>Detects CDI-provided services (via {@code @Produces} methods or {@code @Named} classes)</li>
 * <li>Filters out CDI-provided services from subsystem dependency requirements</li>
 * <li>Adds deployment dependencies only for subsystem-provided capability services</li>
 * <li>Attaches service keys to the deployment unit for later processing</li>
 * </ol>
 *
 * <h3>3. CDI Producer Detection</h3>
 * <p>
 * To avoid conflicts between application-provided services and subsystem-provided services,
 * the processor identifies services that the application provides via CDI:</p>
 * <ul>
 * <li><b>Producer methods</b> - Methods annotated with {@code @Produces @Named("name")} that return AI service types</li>
 * <li><b>Named classes</b> - Classes annotated with {@code @Named("name")} that implement AI service interfaces</li>
 * </ul>
 *
 * <p>
 * CDI-provided services are excluded from subsystem dependency resolution, allowing applications
 * to supply their own service implementations without requiring subsystem configuration.</p>
 *
 * <p>
 * This ensures that:</p>
 * <ul>
 * <li>Required AI services are started before the deployment</li>
 * <li>Service availability is validated at deployment time</li>
 * <li>Application-provided CDI beans take precedence over subsystem services</li>
 * <li>Proper dependency injection can occur in {@link AIDeploymentProcessor}</li>
 * </ul>
 *
 * @see AIDeploymentProcessor
 * @see AIAttachments
 * @see Capabilities
 */
public class AIDependencyProcessor implements DeploymentUnitProcessor {

    /**
     * Optional AI provider modules loaded only when referenced by deployments.
     * These modules are added with {@code setOptional(true)} to avoid deployment
     * failures if specific providers are not installed.
     */
    public static final String[] OPTIONAL_MODULES = {
        "dev.langchain4j.chroma",
        "dev.langchain4j.gemini",
        "dev.langchain4j.github-models",
        "dev.langchain4j.ollama",
        "dev.langchain4j.openai",
        "dev.langchain4j.mcp-client",
        "dev.langchain4j.mistral-ai",
        "dev.langchain4j.neo4j",
        "dev.langchain4j.weaviate",
        "dev.langchain4j.web-search-engines"
    };

    /**
     * Core AI modules that are always added and re-exported to deployments.
     * These modules provide the base API and CDI integration required for all
     * AI functionality in applications.
     */
    public static final String[] EXPORTED_MODULES = {
        "dev.langchain4j",
        "dev.langchain4j.cdi",
        "org.wildfly.extension.ai.injection"
    };

    private static final DotName CHAT_MEMORY_PROVIDER_DOT_NAME = DotName.createSimple(ChatMemoryProvider.class);
    private static final DotName CHAT_MODEL_DOT_NAME = DotName.createSimple(ChatModel.class);
    private static final DotName CONTENT_RETRIEVER_DOT_NAME = DotName.createSimple(ContentRetriever.class);
    private static final DotName EMBEDDING_MODEL_DOT_NAME = DotName.createSimple(EmbeddingModel.class);
    private static final DotName EMBEDDING_STORE_DOT_NAME = DotName.createSimple(EmbeddingStore.class);
    private static final DotName NAMED_DOT_NAME = DotName.createSimple(Named.class);
    private static final DotName PRODUCES_DOT_NAME = DotName.createSimple(Produces.class);
    private static final DotName REGISTER_AI_SERVICE_DOT_NAME = DotName.createSimple(RegisterAIService.class);
    private static final DotName STREAMING_CHAT_MODEL_DOT_NAME = DotName.createSimple(StreamingChatModel.class);
    private static final DotName TOOL_PROVIDER_DOT_NAME = DotName.createSimple(ToolProvider.class);

    /**
     * Service type metadata for reducing code duplication.
     * Provides record-like accessors for encapsulated properties.
     */
    enum ServiceType {
        CHAT_MODEL("ChatModel",
                dev.langchain4j.model.chat.ChatModel.class,
                AIAttachments.CHAT_MODEL_KEYS,
                AIAttachments.CHAT_MODELS,
                CHAT_MODEL_PROVIDER_CAPABILITY,
                CHAT_MODEL_DOT_NAME),
        STREAMING_CHAT_MODEL("StreamingChatModel",
                dev.langchain4j.model.chat.StreamingChatModel.class,
                AIAttachments.CHAT_MODEL_KEYS,
                AIAttachments.CHAT_MODELS,
                CHAT_MODEL_PROVIDER_CAPABILITY,
                STREAMING_CHAT_MODEL_DOT_NAME),
        EMBEDDING_MODEL("EmbeddingModel",
                dev.langchain4j.model.embedding.EmbeddingModel.class,
                AIAttachments.EMBEDDING_MODEL_KEYS,
                AIAttachments.EMBEDDING_MODELS,
                EMBEDDING_MODEL_PROVIDER_CAPABILITY,
                EMBEDDING_MODEL_DOT_NAME),
        EMBEDDING_STORE("EmbeddingStore",
                dev.langchain4j.store.embedding.EmbeddingStore.class,
                AIAttachments.EMBEDDING_STORE_KEYS,
                AIAttachments.EMBEDDING_STORES,
                EMBEDDING_STORE_PROVIDER_CAPABILITY,
                EMBEDDING_STORE_DOT_NAME),
        CONTENT_RETRIEVER("ContentRetriever",
                dev.langchain4j.rag.content.retriever.ContentRetriever.class,
                AIAttachments.CONTENT_RETRIEVER_KEYS,
                AIAttachments.CONTENT_RETRIEVERS,
                Capabilities.CONTENT_RETRIEVER_PROVIDER_CAPABILITY,
                CONTENT_RETRIEVER_DOT_NAME),
        TOOL_PROVIDER("ToolProvider",
                dev.langchain4j.service.tool.ToolProvider.class,
                AIAttachments.TOOL_PROVIDER_KEYS,
                AIAttachments.TOOL_PROVIDERS,
                Capabilities.TOOL_PROVIDER_CAPABILITY,
                TOOL_PROVIDER_DOT_NAME),
        CHAT_MEMORY_PROVIDER("ChatMemoryProvider",
                dev.langchain4j.memory.chat.ChatMemoryProvider.class,
                AIAttachments.CHAT_MEMORY_PROVIDER_KEYS,
                AIAttachments.CHAT_MEMORY_PROVIDERS,
                Capabilities.CHAT_MEMORY_PROVIDER_CAPABILITY,
                CHAT_MEMORY_PROVIDER_DOT_NAME);

        private final String serviceName;
        private final Class<?> serviceClass;
        private final org.jboss.as.server.deployment.AttachmentKey<AttachmentList<String>> keysAttachment;
        private final org.jboss.as.server.deployment.AttachmentKey<?> serviceAttachment;
        private final org.jboss.as.controller.capability.RuntimeCapability<?> capability;
        private final DotName interfaceName;

        ServiceType(String serviceName, Class<?> serviceClass,
                org.jboss.as.server.deployment.AttachmentKey<AttachmentList<String>> keysAttachment,
                org.jboss.as.server.deployment.AttachmentKey<?> serviceAttachment,
                org.jboss.as.controller.capability.RuntimeCapability<?> capability,
                DotName interfaceName) {
            this.serviceName = serviceName;
            this.serviceClass = serviceClass;
            this.keysAttachment = keysAttachment;
            this.serviceAttachment = serviceAttachment;
            this.capability = capability;
            this.interfaceName = interfaceName;
        }

        public String serviceName() {
            return serviceName;
        }

        public Class<?> serviceClass() {
            return serviceClass;
        }

        public org.jboss.as.server.deployment.AttachmentKey<AttachmentList<String>> keysAttachment() {
            return keysAttachment;
        }

        public org.jboss.as.server.deployment.AttachmentKey<?> serviceAttachment() {
            return serviceAttachment;
        }

        public org.jboss.as.controller.capability.RuntimeCapability<?> capability() {
            return capability;
        }

        public DotName interfaceName() {
            return interfaceName;
        }
    }

    /**
     * Processes a deployment to add AI module dependencies and discover required services.
     *
     * <p>
     * This method performs the following operations:</p>
     * <ol>
     * <li>Adds core and optional LangChain4j module dependencies</li>
     * <li>Scans for {@code @Named} injection points on AI service fields</li>
     * <li>Scans for {@code @RegisterAIService} annotations</li>
     * <li>Detects CDI-provided services (producer methods and named classes)</li>
     * <li>Collects required service names for each AI service type</li>
     * <li>Removes CDI-provided services from subsystem dependency requirements</li>
     * <li>Adds deployment dependencies only on subsystem-provided capability services</li>
     * <li>Attaches service keys to deployment unit for {@link AIDeploymentProcessor}</li>
     * </ol>
     *
     * @param deploymentPhaseContext the deployment phase context
     * @throws DeploymentUnitProcessingException if annotation index cannot be resolved
     */
    @Override
    public void deploy(DeploymentPhaseContext deploymentPhaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = deploymentPhaseContext.getDeploymentUnit();
        ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        ModuleLoader moduleLoader = Module.getBootModuleLoader();
        for (String module : OPTIONAL_MODULES) {
            moduleSpecification.addSystemDependency(ModuleDependency.Builder.of(moduleLoader, module).setOptional(true).setImportServices(true).build());
        }
        for (String module : EXPORTED_MODULES) {
            ModuleDependency modDep = ModuleDependency.Builder.of(moduleLoader, module).setExport(true).setImportServices(true).build();
            modDep.addImportFilter(s -> s.equals("META-INF"), true);
            moduleSpecification.addSystemDependency(modDep);
        }
        final CompositeIndex index = deploymentUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        if (index == null) {
            throw ROOT_LOGGER.unableToResolveAnnotationIndex(deploymentUnit);
        }
        List<AnnotationInstance> annotations = index.getAnnotations(NAMED_DOT_NAME);
        List<AnnotationInstance> serviceAnnotations = index.getAnnotations(REGISTER_AI_SERVICE_DOT_NAME);
        if ((annotations == null || annotations.isEmpty()) && (serviceAnnotations == null || serviceAnnotations.isEmpty())) {
            return;
        }
        // Use map to store required services by type
        java.util.Map<ServiceType, Set<String>> requiredServices = new java.util.EnumMap<>(ServiceType.class);
        for (ServiceType type : ServiceType.values()) {
            requiredServices.put(type, new HashSet<>());
        }

        // Track CDI-provided services to remove from required services after all processing
        java.util.Map<ServiceType, Set<String>> cdiProvidedServices = new java.util.EnumMap<>(ServiceType.class);
        for (ServiceType type : ServiceType.values()) {
            cdiProvidedServices.put(type, new HashSet<>());
        }

        // Process @RegisterAIService annotations
        for (AnnotationInstance annotation : serviceAnnotations) {
            processAnnotationValue(annotation, "chatModelName", ServiceType.CHAT_MODEL, requiredServices);
            processAnnotationValue(annotation, "chatLanguageModelName", ServiceType.CHAT_MODEL, requiredServices);
            processAnnotationValue(annotation, "streamingChatModelName", ServiceType.STREAMING_CHAT_MODEL, requiredServices);
            processAnnotationValue(annotation, "streamingChatLanguageModelName", ServiceType.STREAMING_CHAT_MODEL, requiredServices);
            processAnnotationValue(annotation, "contentRetrieverName", ServiceType.CONTENT_RETRIEVER, requiredServices);
            processAnnotationValue(annotation, "toolProviderName", ServiceType.TOOL_PROVIDER, requiredServices);
            processAnnotationValue(annotation, "chatMemoryProviderName", ServiceType.CHAT_MEMORY_PROVIDER, requiredServices);
        }

        // Process @Named annotations
        for (AnnotationInstance annotation : annotations) {
            if (null != annotation.target().kind()) switch (annotation.target().kind()) {
                case FIELD:
                    processFieldInjection(annotation, requiredServices);
                    break;
                case METHOD:
                    processCDIMethodProvidedService(annotation, cdiProvidedServices);
                    break;
                case CLASS:
                    processCDIProvidedService(annotation, cdiProvidedServices);
                    break;
                default:
                    break;
            }
        }

        // Remove CDI-provided services from required services
        for (ServiceType type : ServiceType.values()) {
            requiredServices.get(type).removeAll(cdiProvidedServices.get(type));
        }

        // Add deployment dependencies for all required services
        for (ServiceType type : ServiceType.values()) {
            Set<String> serviceNames = requiredServices.get(type);
            if (!serviceNames.isEmpty()) {
                addDeploymentDependencies(deploymentUnit, deploymentPhaseContext, type, serviceNames);
            }
        }
    }

    /**
     * Safely extracts a string value from an annotation attribute.
     *
     * @param annotation the annotation instance
     * @param name the attribute name
     * @return attribute value as string, or empty string if attribute is not present
     */
    private String getAnnotationValue(AnnotationInstance annotation, String name) {
        AnnotationValue value = annotation.value(name);
        if (value == null) {
            return "";
        }
        return value.asString();
    }

    /**
     * Processes an annotation value and adds it to the required services set if present.
     *
     * @param annotation the annotation instance
     * @param attributeName the annotation attribute name
     * @param serviceType the service type
     * @param requiredServices map of required services by type
     */
    private void processAnnotationValue(AnnotationInstance annotation, String attributeName,
            ServiceType serviceType, java.util.Map<ServiceType, Set<String>> requiredServices) {
        String value = getAnnotationValue(annotation, attributeName);
        if (!value.isBlank()) {
            ROOT_LOGGER.debugf("We need the %s in the class %s", serviceType.serviceName(), annotation.target());
            ROOT_LOGGER.debugf("We need the %s called %s", serviceType.serviceName(), value);
            requiredServices.get(serviceType).add(value);
        }
    }

    /**
     * Processes a @Named field injection to determine required services.
     *
     * @param annotation the @Named annotation
     * @param requiredServices map of required services by type
     */
    private void processFieldInjection(AnnotationInstance annotation, java.util.Map<ServiceType, Set<String>> requiredServices) {
        FieldInfo field = annotation.target().asField();
        String className;
        if (field.type().kind() == Type.Kind.CLASS) {
            className = field.type().asClassType().name().toString();
        } else if (field.type().kind() == Type.Kind.PARAMETERIZED_TYPE) {
            className = field.type().asParameterizedType().name().toString();
        } else {
            return;
        }

        String serviceName = annotation.value().asString();

        try {
            Class<?> fieldClass = Class.forName(className);

            for (ServiceType serviceType : ServiceType.values()) {
                if (serviceType.serviceClass().isAssignableFrom(fieldClass)) {
                    ROOT_LOGGER.debugf("We need the %s in the class %s", serviceType.serviceName(), field.declaringClass());
                    ROOT_LOGGER.debugf("We need the %s called %s", serviceType.serviceName(), serviceName);
                    requiredServices.get(serviceType).add(serviceName);
                    return; // Found a match, no need to check other types
                }
            }
        } catch (ClassNotFoundException ex) {
            ROOT_LOGGER.errorf(ex, "Couldn't get the class type for %s to be able to check what to inject", className);
        }
    }

    /**
     * Processes CDI bean classes ({@code @Named}) that implement AI service interfaces
     * and tracks them for later removal from required subsystem dependencies.
     *
     * <p>
     * Detects classes annotated with {@code @Named} that directly implement one of the
     * supported AI service interfaces (ChatModel, StreamingChatModel, EmbeddingModel,
     * EmbeddingStore, ContentRetriever, ToolProvider, ChatMemoryProvider).</p>
     *
     * @param annotation the @Named annotation on a class
     * @param cdiProvidedServices map to collect CDI-provided services by type
     */
    private void processCDIProvidedService(AnnotationInstance annotation, java.util.Map<ServiceType, Set<String>> cdiProvidedServices) {
        ClassInfo classInfo = annotation.target().asClass();
        String serviceName = annotation.value().asString();

        for (ServiceType serviceType : ServiceType.values()) {
            if (serviceType.interfaceName() != null && classInfo.interfaceNames().contains(serviceType.interfaceName())) {
                cdiProvidedServices.get(serviceType).add(serviceName);
                ROOT_LOGGER.debugf("The %s called %s is provided via CDI", serviceType.serviceName(), serviceName);
                return;
            }
        }
    }

    /**
     * Processes CDI producer methods ({@code @Produces @Named}) that provide AI services
     * and tracks them for later removal from required subsystem dependencies.
     *
     * <p>
     * Detects methods annotated with both {@code @Produces} and {@code @Named} whose return type
     * is assignable to one of the supported AI service interfaces.</p>
     *
     * @param annotation the @Named annotation on a producer method
     * @param cdiProvidedServices map to collect CDI-provided services by type
     */
    private void processCDIMethodProvidedService(AnnotationInstance annotation, java.util.Map<ServiceType, Set<String>> cdiProvidedServices) {
        MethodInfo methodInfo = annotation.target().asMethod();
        String serviceName = annotation.value().asString();

        for (ServiceType serviceType : ServiceType.values()) {
            if (methodInfo.hasAnnotation(PRODUCES_DOT_NAME)) {
                Type returnType = methodInfo.returnType();
                Class<?> returnClass = null;

                // Handle both CLASS and PARAMETERIZED_TYPE (e.g., EmbeddingStore<?>)
                if (returnType.kind() == Type.Kind.CLASS) {
                    returnClass = (Class<?>) JandexReflection.loadType(returnType);
                } else if (returnType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                    java.lang.reflect.Type loadedType = JandexReflection.loadType(returnType);
                    if (loadedType instanceof java.lang.reflect.ParameterizedType parameterizedType) {
                        java.lang.reflect.Type rawType = parameterizedType.getRawType();
                        if (rawType instanceof Class<?> rawClass) {
                            returnClass = rawClass;
                        }
                    }
                }

                if (returnClass != null && serviceType.serviceClass().isAssignableFrom(returnClass)) {
                    cdiProvidedServices.get(serviceType).add(serviceName);
                    ROOT_LOGGER.debugf("The %s called %s is provided via CDI", serviceType.serviceName(), serviceName);
                    return;
                }
            }
        }
    }

    /**
     * Adds deployment dependencies for a set of required services of a given type.
     *
     * @param deploymentUnit the deployment unit
     * @param deploymentPhaseContext the deployment phase context
     * @param serviceType the service type
     * @param serviceNames set of required service names
     */
    private void addDeploymentDependencies(DeploymentUnit deploymentUnit,
            DeploymentPhaseContext deploymentPhaseContext, ServiceType serviceType, Set<String> serviceNames) {
        for (String serviceName : serviceNames) {
            deploymentUnit.addToAttachmentList(serviceType.keysAttachment(), serviceName);
            deploymentPhaseContext.addDeploymentDependency(
                    serviceType.capability().getCapabilityServiceName(serviceName),
                    serviceType.serviceAttachment());
        }
    }
}
