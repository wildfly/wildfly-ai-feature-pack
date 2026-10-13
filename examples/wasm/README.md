WASM Example (Proof of Concept)
===============================

Demonstrates WebAssembly WASI module invocation using the WildFly AI Feature Pack. This example uses the Extism [count_vowels](https://github.com/extism/plugins) plugin, which counts vowels in a given string.

The WASM binary is included in the project under `src/main/resources/wasm/`

Prerequisites
-------------

- Java 17+
- Maven 3.9+

Build
-----

```bash
mvn clean package
```

This will:
1. Provision a WildFly server with the `wasm` layer
2. Configure the included `count_vowels.wasm` module in the subsystem via CLI script

Run
---

```bash
./target/server/bin/standalone.sh --stability experimental
```

Try It
------

```bash
curl -X POST http://localhost:8080/api/wasm -H "Content-Type: text/plain" -d 'Hello, WildFly!'
```

Returns a JSON response like:

```json
{"count": 3, "total": 3, "vowels": "aeiouAEIOU"}
```

How It Works
------------

The `wasm` Galleon layer enables WebAssembly support in WildFly using the Chicory Java WASM runtime and Extism SDK.

During the build, a CLI script registers the included `count_vowels.wasm` binary in the WASM subsystem as `countVowels`. At runtime, the module is injected via CDI using `@WasmTool("countVowels") WasmInvoker`, and the exported `count_vowels` function is called with the input string.
