---
title: Getting Started
description: Get started with the WildFly AI Feature Pack -- overview, provisioning, and first deployment.
layout: page
---

# Getting Started

The WildFly AI Feature Pack is a [Galleon](https://docs.wildfly.org/galleon/) feature pack that brings AI capabilities to [WildFly](https://www.wildfly.org/). It provides subsystem extensions for connecting to LLM providers, building RAG pipelines, exposing MCP servers, and running WebAssembly tools.

## What You Can Build

- **LLM-powered applications** -- Connect to OpenAI, Ollama, Mistral, Gemini, or Groq from your Jakarta EE application.
- **RAG pipelines** -- Use embedding models, embedding stores (in-memory, Neo4j, Weaviate, Chroma), and content retrievers.
- **MCP servers** -- Expose your CDI beans as Model Context Protocol servers with tools, prompts, and resources.
- **MCP clients** -- Connect to external MCP servers via SSE, stdio, or Streamable HTTP transports.
- **WASM tools** -- Run WebAssembly modules as MCP tools in a sandboxed environment.

## Prerequisites

- Java 17+
- Maven 3.8+
- WildFly 39.0.0.Final or WildFly Preview

## Stability Level

The feature pack is at the **experimental** stability level and must be explicitly provisioned at that level. When running the server:

```bash
./bin/standalone.sh --stability experimental
```

## Provisioning

The Galleon layers defined in this feature pack are decorator layers -- they must be provisioned alongside a WildFly base layer. The WildFly [Installation Guide](https://docs.wildfly.org/33/#installation-guides) covers the [base layers](https://docs.wildfly.org/33/Galleon_Guide.html#wildfly_foundational_galleon_layers).

### Using the WildFly Maven Plugin

Include the AI feature pack and desired layers in your Maven Plugin configuration:

```xml
<plugin>
    <groupId>org.wildfly.plugins</groupId>
    <artifactId>wildfly-maven-plugin</artifactId>
    <version>$\{version.wildfly.maven.plugin}</version>
    <configuration>
        <stability>experimental</stability>
        <feature-packs>
            <feature-pack>
                <location>org.wildfly:wildfly-galleon-pack:39.0.0.Final</location>
            </feature-pack>
            <feature-pack>
                <location>org.wildfly.generative-ai:wildfly-ai-feature-pack:0.20.2-SNAPSHOT</location>
            </feature-pack>
        </feature-packs>
        <layers>
            <layer>cloud-server</layer>
            <layer>ollama-chat-model</layer>
            <layer>default-embedding-content-retriever</layer>
        </layers>
    </configuration>
</plugin>
```

### Using the WildFly Maven Plugin with Glow

[WildFly Glow](https://docs.wildfly.org/glow/) can auto-discover the layers your application needs:

```xml
<plugin>
    <groupId>org.wildfly.plugins</groupId>
    <artifactId>wildfly-maven-plugin</artifactId>
    <version>$\{version.wildfly.maven.plugin}</version>
    <configuration>
        <stability>experimental</stability>
        <discoverProvisioningInfo>
            <spaces>
                <space>incubating</space>
            </spaces>
            <version>$\{version.wildfly.server}</version>
        </discoverProvisioningInfo>
        <name>ROOT.war</name>
    </configuration>
</plugin>
```

### Using the Galleon CLI

Create a provisioning configuration file:

```xml
<?xml version="1.0" ?>
<installation xmlns="urn:jboss:galleon:provisioning:3.0">
  <feature-pack location="org.wildfly:wildfly-galleon-pack:39.0.0.Final">
    <default-configs inherit="false"/>
    <packages inherit="false"/>
  </feature-pack>
  <feature-pack location="org.wildfly.generative-ai:wildfly-ai-feature-pack:0.20.2-SNAPSHOT">
    <default-configs inherit="false"/>
    <packages inherit="false"/>
  </feature-pack>
  <config model="standalone" name="standalone.xml">
    <layers>
      <include name="cloud-server"/>
      <include name="ollama-chat-model"/>
      <include name="default-embedding-content-retriever"/>
    </layers>
  </config>
  <options>
    <option name="optional-packages" value="passive+"/>
    <option name="jboss-fork-embedded" value="true"/>
  </options>
</installation>
```

Then provision:

```bash
galleon.sh provision provisioning.xml --dir=my-wildfly-server
```

## Your First AI Application

Once provisioned, deploy a Jakarta EE application that uses AI services. For example, using the Ollama chat model:

1. Set the environment variables for your Ollama instance:

```bash
export OLLAMA_CHAT_URL=http://localhost:11434
export OLLAMA_CHAT_MODEL_NAME=llama3
```

2. Start the server:

```bash
./bin/standalone.sh --stability experimental
```

3. Deploy your application WAR.

For a complete working example, see the [webchat](https://github.com/ehsavoie/webchat/) project.

## Next Steps

- [Chat Models](chat-models) -- Configure LLM providers
- [Embedding & RAG](embedding) -- Set up embedding stores and content retrievers
- [MCP Overview](mcp) -- Build MCP servers and clients
- [Configuration](configuration) -- Full environment variable reference
