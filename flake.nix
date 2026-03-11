{
  description = "trixnity-clj";

  inputs = {
    nixpkgs.url = "https://flakehub.com/f/NixOS/nixpkgs/0.1";
    flakelight = {
      url = "github:nix-community/flakelight";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    devshell = {
      url = "github:numtide/devshell";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    devenv = {
      url = "https://flakehub.com/f/ramblurr/nix-devenv/*";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    clj-nix = {
      url = "github:jlesquembre/clj-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = inputs@{ flakelight, ... }:
    flakelight ./. {
      inherit inputs;
      pname = "trixnity-clj";

      withOverlays = [
        inputs.devshell.overlays.default
        inputs.devenv.overlays.default
        inputs.clj-nix.overlays.default
      ];

      package = pkgs:
        let
          gradle = pkgs.gradle.override {
            java = pkgs.jdk21;
          };
        in
        pkgs.mkCljLib {
          projectSrc = ./.;
          name = "com.outskirtslabs/trixnity-clj";
          version = "0.0.1";
          nativeBuildInputs = [
            pkgs.babashka
            gradle
          ];
          JAVA_HOME = pkgs.jdk25.home;
          GRADLE_OPTS =
            "-Dorg.gradle.java.installations.auto-download=false "
            + "-Dorg.gradle.java.installations.paths=${pkgs.jdk25.home}";
          buildCommand = ''
            cljnix_home="$HOME"
            export HOME="$TMPDIR/home"
            mkdir -p "$HOME"
            ln -s "$cljnix_home/.m2" "$HOME/.m2"
            ln -s "$cljnix_home/.gitlibs" "$HOME/.gitlibs"
            export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"
            export TRIXNITY_GRADLE_ARGS="--offline"
            export TRIXNITY_GRADLE_JAVA_HOME="${pkgs.jdk21.home}"
            export GRADLE_USER_HOME="$HOME/.gradle"

            bb test
            bb jar
          '';
        };

      devShell = pkgs:
        pkgs.devshell.mkShell {
          imports = [
            inputs.devenv.capsules.base
            inputs.devenv.capsules.clojure
          ];
          env = [
            {
              name = "JAVA_HOME";
              value = pkgs.jdk25.home;
            }
            {
              name = "GRADLE_OPTS";
              value =
                "-Dorg.gradle.java.installations.auto-download=false "
                + "-Dorg.gradle.java.installations.paths=${pkgs.jdk25.home}";
            }
            {
              name = "TRIXNITY_GRADLE_JAVA_HOME";
              value = pkgs.jdk21.home;
            }
          ];
          commands = [
          ];
          packages = [
            pkgs.jdk21
            pkgs.jdk25
            (pkgs.gradle.override {
              java = pkgs.jdk21;
            })
          ];
        };
    };
}
