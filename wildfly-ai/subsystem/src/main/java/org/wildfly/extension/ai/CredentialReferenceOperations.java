/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai;

import java.util.function.UnaryOperator;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;

/** Management operation support for credential references. */
public final class CredentialReferenceOperations {
    private CredentialReferenceOperations() {
    }

    public static UnaryOperator<OperationStepHandler> add(AttributeDefinition attribute, String... parents) {
        return delegate -> (context, operation) -> {
            delegate.execute(context, operation);
            Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
            String[] containers = parents.length == 0 ? new String[] { null } : parents;
            for (String parent : containers) {
                ModelNode container = parent == null ? resource.getModel() : container(resource.getModel(), parent);
                if (container.hasDefined(attribute.getName())) {
                    CredentialReference.handleCredentialReferenceUpdate(context, container.get(attribute.getName()), attribute.getName());
                }
            }
            // A model-stage failure can occur before the store is touched.
            ModelNode result = context.getResult();
            context.completeStep((rollbackContext, rollbackOperation) -> {
                if (!result.hasDefined(CredentialReference.CREDENTIAL_STORE_UPDATE, CredentialReference.STATUS)) {
                    return;
                }
                for (String parent : containers) {
                    ModelNode container = parent == null ? resource.getModel() : container(resource.getModel(), parent);
                    try {
                        CredentialReference.rollbackCredentialStoreUpdate(attribute, rollbackContext, attribute.resolveModelAttribute(rollbackContext, container));
                    } catch (OperationFailedException e) {
                        throw new IllegalStateException(e);
                    }
                }
            });
        };
    }

    private static ModelNode container(ModelNode model, String... parents) {
        ModelNode result = model;
        for (String parent : parents) {
            if (!result.hasDefined(parent)) {
                return new ModelNode();
            }
            result = result.get(parent);
        }
        return result;
    }

    // ResourceDescriptor invokes custom write handlers while populating an add operation.
    private static boolean existsInOriginalModel(OperationContext context) {
        try {
            context.getOriginalRootResource().navigate(context.getCurrentAddress());
            return true;
        } catch (Resource.NoSuchResourceException e) {
            return false;
        }
    }

    public static OperationStepHandler write(AttributeDefinition credential, boolean nested) {
        return new AbstractWriteAttributeHandler<Void>() {
            @Override
            protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName,
                    ModelNode newValue, ModelNode oldValue, Resource resource) throws OperationFailedException {
                ModelNode value = resource.getModel().get(attributeName);
                ModelNode reference = nested ? (value.hasDefined(credential.getName()) ? value.get(credential.getName()) : new ModelNode()) : value;
                if (reference.isDefined()) {
                    CredentialReference.handleCredentialReferenceUpdate(context, reference, credential.getName());
                }
                if (!existsInOriginalModel(context) || !context.isDefaultRequiresRuntime() || context.isBooting()) {
                    return;
                }
                // Capture this operation's prepared value before later composite steps change the model.
                ModelNode prepared = reference.clone();
                ModelNode credentialOperation = operation.clone();
                if (nested) {
                    ModelNode original = operation.get("value");
                    credentialOperation.get("value").set(original.hasDefined(credential.getName()) ? original.get(credential.getName()) : new ModelNode());
                }
                context.addStep((runtimeContext, ignored) -> {
                    ModelNode resolved = runtimeContext.resolveExpressions(prepared);
                    CredentialReference.applyCredentialReferenceUpdateToRuntime(runtimeContext, credentialOperation, resolved, oldValue, credential.getName());
                    runtimeContext.completeStep((rollbackContext, rollbackOperation) ->
                            CredentialReference.rollbackCredentialStoreUpdate(credential, rollbackContext, resolved));
                }, OperationContext.Stage.RUNTIME);
            }

            @Override
            protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                    ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handback) {
                return existsInOriginalModel(context) && !operation.get("value").equals(currentValue);
            }

            @Override
            protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                    ModelNode valueToRestore, ModelNode resolvedValue, Void handback) {
            }
        };
    }
}
