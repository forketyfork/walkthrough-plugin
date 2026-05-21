{
  description = "Walkthrough Plugin - IntelliJ IDEA plugin for inline walkthrough guidance";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    git-hooks = {
      url = "github:cachix/git-hooks.nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = {
    self,
    nixpkgs,
    flake-utils,
    git-hooks,
  }:
    flake-utils.lib.eachDefaultSystem (
      system: let
        pkgs = nixpkgs.legacyPackages.${system};

        # Hooks that run using Nix-provided binaries — safe in the Nix sandbox and in CI.
        # alejandra uses --check so it reports formatting issues without writing to the read-only sandbox.
        nix-native-hooks = {
          shellcheck = {
            enable = true;
            excludes = ["\\.envrc" "gradlew" "gradlew\\.bat"];
          };
          shfmt = {
            enable = true;
            excludes = ["\\.envrc" "gradlew" "gradlew\\.bat"];
          };
          actionlint.enable = true;
          alejandra = {
            enable = true;
            name = "alejandra";
            entry = "${pkgs.alejandra}/bin/alejandra --check";
            files = "\\.nix$";
            language = "system";
          };
          statix = {
            enable = true;
            name = "statix";
            entry = "${pkgs.statix}/bin/statix check";
            files = "\\.nix$";
            language = "system";
            pass_filenames = false;
          };
          markdownlint-cli2 = {
            enable = true;
            name = "markdownlint-cli2";
            entry = "${pkgs.markdownlint-cli2}/bin/markdownlint-cli2";
            files = "\\.md$";
            language = "system";
          };
          # Spell-check user-facing content (CHANGELOG, README, plugin.xml
          # <description>, KDoc, MCP @McpDescription strings, etc.).
          # `typos` accepts paths positionally, so passing only the changed
          # files keeps incremental pre-commit runs fast. CI invokes
          # `pre-commit run --all-files`, which still gives full-repo
          # coverage via `nix flake check`.
          typos = {
            enable = true;
            name = "typos";
            entry = "${pkgs.typos}/bin/typos";
            language = "system";
            pass_filenames = true;
          };
          # Security linter for GitHub Actions workflows: catches expression
          # injection, missing `permissions:`, untrusted pull_request_target,
          # mutable action refs, etc. Complements actionlint. Configuration
          # lives in .github/zizmor.yml and is auto-discovered.
          zizmor = {
            enable = true;
            name = "zizmor";
            entry = "${pkgs.zizmor}/bin/zizmor --no-online-audits .github/workflows";
            files = "^\\.github/(workflows/.*\\.ya?ml|zizmor\\.yml)$";
            language = "system";
            pass_filenames = false;
          };
        };

        # Exposed as flake checks — runs nix-native-hooks via nix flake check in CI.
        flake-check = git-hooks.lib.${system}.run {
          src = ./.;
          hooks = nix-native-hooks;
        };

        # Full pre-commit hook set for local dev — extends nix-native-hooks with Gradle-based
        # checks that require network access and JDK (not suitable for the Nix sandbox).
        pre-commit-check = git-hooks.lib.${system}.run {
          src = ./.;
          hooks =
            nix-native-hooks
            // {
              detekt = {
                enable = true;
                name = "detekt";
                entry = "./gradlew detekt";
                files = "\\.kt$";
                language = "system";
                pass_filenames = false;
              };
            };
        };
      in {
        checks = {
          inherit flake-check;
        };

        devShells.default = pkgs.mkShell {
          inherit (pre-commit-check) shellHook;
          buildInputs = with pkgs;
            [
              jdk21
              git
              just
              markdownlint-cli2
              alejandra
              statix
              typos
              zizmor
            ]
            ++ pre-commit-check.enabledPackages;
        };
      }
    );
}
