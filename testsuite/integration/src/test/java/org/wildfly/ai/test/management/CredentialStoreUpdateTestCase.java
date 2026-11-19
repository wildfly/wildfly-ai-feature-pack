/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.wildfly.ai.test.util.DeploymentFactory;

@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class CredentialStoreUpdateTestCase {
    @Deployment
    public static WebArchive deployment() {
        return DeploymentFactory.createBaseDeployment("credential-store-update.war");
    }

    @Test
    public void storeUpdatesAndCompositeRollback() throws Exception {
        try (ModelControllerClient client = ModelControllerClient.Factory.create(InetAddress.getByName("127.0.0.1"), 9990)) {
            String name = "ai-test-" + UUID.randomUUID();
            ModelNode store = address("elytron", "credential-store", name);
            ModelNode model = address("ai", "openai-chat-model", name);
            ModelNode addStore = operation("add", store);
            addStore.get("location").set(name + ".cs");
            addStore.get("relative-to").set("jboss.server.data.dir");
            addStore.get("create").set(true);
            addStore.get("credential-reference", "clear-text").set("test-store-password");
            execute(client, addStore);
            try {
                ModelNode add = operation("add", model);
                add.get("model-name").set("test-model");
                add.get("executor-service").set("default");
                add.get("base-url").set("http://localhost:8080");
                reference(add.get("credential-reference"), name, "first", "initial-secret");
                execute(client, add);
                assertTrue(aliases(client, store).toString().contains("first"));
                ModelNode read = operation("read-attribute", model);
                read.get("name").set("credential-reference");
                ModelNode saved = execute(client, read).get("result");
                assertEquals("first", saved.get("alias").asString());
                assertFalse(saved.hasDefined("clear-text"));

                ModelNode generatedModel = address("ai", "openai-chat-model", name + "-generated");
                ModelNode generatedAdd = operation("add", generatedModel);
                generatedAdd.get("model-name").set("test-model");
                generatedAdd.get("executor-service").set("default");
                generatedAdd.get("base-url").set("http://localhost:8080");
                generatedAdd.get("credential-reference", "store").set(name);
                generatedAdd.get("credential-reference", "clear-text").set("generated-secret");
                execute(client, generatedAdd);
                ModelNode generatedRead = operation("read-attribute", generatedModel);
                generatedRead.get("name").set("credential-reference");
                ModelNode generatedReference = execute(client, generatedRead).get("result");
                assertTrue(generatedReference.hasDefined("alias"));
                assertFalse(generatedReference.hasDefined("clear-text"));
                assertTrue(aliases(client, store).toString().contains(generatedReference.get("alias").asString()));
                execute(client, operation("remove", generatedModel));

                ModelNode modelFailureAdd = operation("add", address("ai", "openai-chat-model", name + "-model-failure"));
                modelFailureAdd.get("model-name").set("test-model");
                modelFailureAdd.get("executor-service").set("default");
                modelFailureAdd.get("base-url").set("http://localhost:8080");
                reference(modelFailureAdd.get("credential-reference"), name, "first", "unused-secret");
                ModelNode invalid = operation("write-attribute", model);
                invalid.get("name").set("does-not-exist");
                invalid.get("value").set("invalid");
                ModelNode modelFailure = operation("composite", new ModelNode().setEmptyList());
                modelFailure.get("steps").add(modelFailureAdd);
                modelFailure.get("steps").add(invalid);
                assertEquals("failed", client.execute(modelFailure).get("outcome").asString());
                assertTrue(aliases(client, store).toString().contains("first"));

                ModelNode rollbackModel = address("ai", "openai-chat-model", name + "-rollback");
                ModelNode rollbackAdd = operation("add", rollbackModel);
                rollbackAdd.get("model-name").set("test-model");
                rollbackAdd.get("executor-service").set("default");
                rollbackAdd.get("base-url").set("http://localhost:8080");
                reference(rollbackAdd.get("credential-reference"), name, "rolled-back-add", "rollback-secret");
                ModelNode failAdd = operation("remove-alias", store);
                failAdd.get("alias").set("missing-alias");
                ModelNode addComposite = operation("composite", new ModelNode().setEmptyList());
                addComposite.get("steps").add(rollbackAdd);
                addComposite.get("steps").add(failAdd);
                ModelNode addFailure = client.execute(addComposite);
                assertEquals("failed", addFailure.get("outcome").asString(), addFailure.toString());
                assertTrue(addFailure.toString().contains("update-rolled-back"), addFailure.toString());
                assertFalse(aliases(client, store).toString().contains("rolled-back-add"));
                assertEquals("failed", client.execute(operation("read-resource", rollbackModel)).get("outcome").asString());

                ModelNode rollbackWrite = operation("write-attribute", model);
                rollbackWrite.get("name").set("credential-reference");
                reference(rollbackWrite.get("value"), name, "rolled-back", "rollback-secret");
                ModelNode fail = operation("remove-alias", store);
                fail.get("alias").set("missing-alias");
                ModelNode composite = operation("composite", new ModelNode().setEmptyList());
                composite.get("steps").add(rollbackWrite);
                composite.get("steps").add(fail);
                ModelNode writeFailure = client.execute(composite);
                assertEquals("failed", writeFailure.get("outcome").asString(), writeFailure.toString());
                assertTrue(writeFailure.toString().contains("update-rolled-back"), writeFailure.toString());
                assertFalse(aliases(client, store).toString().contains("rolled-back"));
                assertEquals("first", execute(client, read).get("result", "alias").asString());

                ModelNode write = operation("write-attribute", model);
                write.get("name").set("credential-reference");
                reference(write.get("value"), name, "second", "updated-secret");
                ModelNode writeResult = execute(client, write);
                ModelNode currentAliases = aliases(client, store);
                assertTrue(currentAliases.toString().contains("second"), "aliases=" + currentAliases + " write=" + writeResult + " model=" + execute(client, read));

            } finally {
                client.execute(operation("remove", model));
                client.execute(operation("remove", store));
            }
        }
    }

    private static void reference(ModelNode reference, String store, String alias, String secret) {
        reference.get("store").set(store);
        reference.get("alias").set(alias);
        reference.get("clear-text").set(secret);
    }

    private static ModelNode address(String subsystem, String type, String name) {
        ModelNode address = new ModelNode().setEmptyList();
        address.add("subsystem", subsystem);
        address.add(type, name);
        return address;
    }

    private static ModelNode operation(String name, ModelNode address) {
        ModelNode operation = new ModelNode();
        operation.get("operation").set(name);
        operation.get("address").set(address);
        return operation;
    }

    private static ModelNode execute(ModelControllerClient client, ModelNode operation) throws IOException {
        ModelNode response = client.execute(operation);
        assertEquals("success", response.get("outcome").asString(), response.toString());
        return response;
    }

    private static ModelNode aliases(ModelControllerClient client, ModelNode store) throws IOException {
        return execute(client, operation("read-aliases", store)).get("result");
    }
}
