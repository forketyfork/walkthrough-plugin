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
            ]
            ++ pre-commit-check.enabledPackages;
        };
      }
    );
}
