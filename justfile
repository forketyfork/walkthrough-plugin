# Walkthrough Plugin - Development Commands

# List all available commands
default:
    @just --list

# Build the plugin
build:
    ./gradlew buildPlugin

# Run the plugin in a sandboxed IDE instance
run:
    ./gradlew runIde

# Verify the plugin (compatibility checks)
verify:
    ./gradlew verifyPlugin

# Run unit tests
test:
    ./gradlew test

# Run all static analysis CI runs: nix flake check (markdownlint, shellcheck, actionlint, alejandra, statix) and Detekt
lint:
    nix flake check
    ./gradlew detekt

# Clean build artifacts
clean:
    ./gradlew clean

# Publish plugin to JetBrains Marketplace (requires signing and publish env vars)
publish:
    ./gradlew publishPlugin

# Install pre-commit hooks (requires Nix dev shell)
hooks:
    pre-commit install
