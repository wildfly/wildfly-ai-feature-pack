/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.examples.wasm;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.nio.charset.StandardCharsets;
import org.wildfly.wasm.api.WasmInvoker;
import org.wildfly.wasm.api.WasmTool;

@Path("/wasm")
public class WasmResource {

    @Inject
    @WasmTool("countVowels")
    private WasmInvoker countVowels;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public String countVowels(String input) {
        byte[] result = countVowels.call("count_vowels", input.getBytes(StandardCharsets.UTF_8));
        return new String(result, StandardCharsets.UTF_8);
    }
}
