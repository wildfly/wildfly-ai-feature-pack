/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai;

import java.io.IOException;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.password.interfaces.ClearPassword;

public final class CredentialReferenceUtil {

    private CredentialReferenceUtil() {
    }

    public static String getPassword(CredentialSource credentialSource) {
        try {
            return String.valueOf(credentialSource.getCredential(PasswordCredential.class).getPassword(ClearPassword.class).getPassword());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to obtain password credential", e);
        }
    }
}
