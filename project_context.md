# PROJECT_CONTEXT.md

## Project Overview

ShaderPatcher is a client-side Minecraft mod intended to patch and improve shader compatibility in large modpacks and heavily modded rendering environments.

The mod is designed as a lightweight Java application hosted inside Minecraft rather than a traditional deeply-integrated Minecraft mod.

Primary goals:

* Shader compatibility patching
* Large modpack support
* Long-term maintainability
* Minimal Minecraft API surface
* Multi-loader compatibility
* Future Minecraft version survivability

---

# Supported Loaders and Versions

## Fabric

* 1.17+
* Primary/canonical implementation

## Forge

* 1.17 - 1.20.6

## NeoForge

* 1.20+

---

# Core Architectural Principles

## Minecraft is treated as a host environment

The majority of the project should remain pure Java and loader-agnostic.

Minecraft-specific logic should be minimized and isolated.

---

## Minimal Minecraft API usage

Avoid unnecessary dependencies on:

* Minecraft internals
* Fabric API
* Forge APIs
* NeoForge APIs

Current Minecraft integration requirements are intentionally extremely small.

---

## No Mixins unless absolutely necessary

Mixins are intentionally avoided because they:

* increase maintenance burden
* reduce future compatibility
* complicate multi-loader support
* increase fragility across Minecraft versions

Use Mixins only when no clean alternative exists.

---

## Avoid leaking Minecraft types into core logic

Minecraft classes should remain isolated near platform boundaries.

Bad:

```java id="x4p7mz"
ResourceLocation
```

Preferred:

```java id="c9n2wr"
Identifier
```

Core logic should not depend directly on:

```java id="v5m8tx"
net.minecraft.*
```

---

## Prefer handwritten abstractions

Avoid large abstraction frameworks unless they solve a concrete problem.

Currently avoiding:

* Architectury
* large compatibility frameworks
* unnecessary platform abstraction libraries

Small handwritten interfaces are preferred.

---

## Fabric is the canonical implementation

Fabric is used as the primary development environment because:

* cleaner bootstrap
* smaller API surface
* faster update cadence
* simpler architecture

Forge and NeoForge are treated as compatibility hosts/adapters.

---

# Current Technical Stack

## Minecraft Version

* Primary development target: 1.20.1

## Java Compatibility Strategy

### Runtime/Development JDK

* JDK 25

### Bytecode/Language Target

* Java 17

Reasoning:

* future Minecraft compatibility
* modern tooling/runtime environment
* backwards compatibility with older supported versions

The project intentionally targets Java 17 bytecode while using newer JVMs for:

* IntelliJ
* Gradle
* Loom
* development tooling

---

## Gradle Java Configuration

Current preferred configuration:

```gradle id="u3r9vy"
java {
	withSourcesJar()

	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}
```

Important:

* produced bytecode must remain Java 17 compatible
* avoid Java 21+/25 language features
* avoid Java 21+/25 standard library APIs in common code

---

## IntelliJ Setup

Recommended configuration:

* IntelliJ runtime JDK: 25
* Gradle JVM: 25
* Java language level: 17
* Gradle toolchain target: 17

---

## Mappings

* Mojang mappings

Reasoning:

* cross-loader consistency
* future compatibility
* NeoForge parity
* reduced mapping fragmentation

---

# Current Build System

## Gradle

* Gradle-based build system
* Fabric Loom

---

## Gradle Performance Settings

Recommended gradle.properties settings:

```properties id="f1q8tp"
org.gradle.jvmargs=-Xmx4G
org.gradle.parallel=true
org.gradle.caching=true
```

---

## Dependency Inclusion Strategy

Third-party libraries are currently bundled using:

```gradle id="r8m2wx"
include(implementation(...))
```

instead of Shadow/shading.

Reason:

* simpler Loom compatibility
* fewer build tool conflicts
* simpler architecture during early development

Current included dependencies appear in:

```text id="k4v9zn"
META-INF/jars/
```

inside the final mod jar.

---

# Current Dependencies

## Included Libraries

* SnakeYAML Engine

Included using:

```gradle id="b2n7qy"
include(implementation("org.snakeyaml:snakeyaml-engine:2.9"))
```

Current behavior:

* packaged into META-INF/jars/
* loaded automatically by Fabric Loader

---

# Dependency Policy

## Current policy

Prefer:

* lightweight dependencies
* isolated libraries
* pure Java libraries

Avoid:

* large runtime ecosystems
* unnecessary helper frameworks
* loader-coupled utility libraries

---

## Relocation policy

Relocation/shading is expected to become important later due to:

* large modpack environments
* potential dependency conflicts
* Forge/NeoForge classpath complexity

However:

* relocation is intentionally postponed during early architecture development
* current builds use Loom include()

Relocation will likely be revisited once:

* Forge support is added
* NeoForge support is added
* dependency graph grows larger

---

# Current Project Structure

Current structure is intentionally simple and single-module.

Planned package structure:

```text id="n7p4tv"
bootstrap/
core/
platform/
resources/
patching/
util/
api/
```

Current recommendation:

* no multi-module Gradle setup yet
* no separate client/common source sets yet

Reason:

* architecture is still stabilizing
* premature modularization adds unnecessary complexity

---

# Platform Abstraction Philosophy

Platform-specific code should remain extremely small.

Expected platform responsibilities:

* game directory access
* resource listing/access
* loader detection
* bootstrap lifecycle

Everything else should remain platform-agnostic.

---

# Resource Handling Philosophy

Resources are one of the primary Minecraft integration points.

Resource access should eventually be abstracted behind a small interface.

Example concept:

```java id="x5t8wr"
public interface ResourceProvider {
    InputStream open(String path);

    Collection<Identifier> list(String root);

    boolean exists(String path);
}
```

---

# Long-Term Goals

## Single distributable jar

Long-term goal is a single distributable jar supporting:

* multiple loaders
* multiple Minecraft versions

---

## Future-proof architecture

The project should survive future Minecraft updates with minimal changes.

Priority:

1. architectural cleanliness
2. abstraction correctness
3. platform isolation
4. packaging complexity

---

## Large modpack compatibility

The project is specifically intended for large modpacks and heavily modded environments.

Compatibility and robustness are prioritized over:

* rapid feature development
* deep engine integration
* invasive techniques

---

# Explicit Non-Goals

Currently avoiding:

* server-side support
* networking systems
* registry-heavy systems
* worldgen
* gameplay systems
* Fabric API lock-in
* invasive rendering hooks unless necessary

---

# IntelliJ and Loom Notes

## Gradle delegated builds

Use Gradle delegated builds instead of IntelliJ internal builds.

Recommended IntelliJ settings:

```text id="w1q7mx"
Build and run using: Gradle
Run tests using: Gradle
```

---

## Resource filtering warning

The warning:

```text id="c6n2vy"
Cannot resolve resource filtering of MatchingCopyAction
```

is expected in Loom projects and is generally harmless when using delegated Gradle builds.

Usually caused by:

```gradle id="p9m4tw"
processResources {
	filesMatching("fabric.mod.json") {
		expand(...)
	}
}
```

This warning can generally be ignored safely.

---

# Current Repository Hygiene

Repository intentionally excludes:

```text id="r2v8zp"
.gradle/
build/
bin/
.idea/
run/
```

Project uses:

* .gitignore
* .gitattributes
* UTF-8 encoding
* Gradle caching
* Java toolchains

---

# Development Philosophy

The project should remain:

* minimal
* understandable
* maintainable
* loader-agnostic where possible

Avoid overengineering early.

Prefer:

* simple abstractions
* explicit architecture
* incremental complexity

over:

* giant frameworks
* premature compatibility systems
* speculative abstractions
