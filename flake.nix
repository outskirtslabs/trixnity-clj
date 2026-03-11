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

  outputs =
    inputs@{ flakelight, ... }:
    flakelight ./. {
      inherit inputs;
      pname = "trixnity-clj";

      withOverlays = [
        inputs.devshell.overlays.default
        inputs.devenv.overlays.default
        inputs.clj-nix.overlays.default
      ];

      package =
        pkgs:
        let
          deps-cache = pkgs.mk-deps-cache {
            lockfile = ./deps-lock.json;
          };
          bridge-classes = "${bridge-package}/share/trixnity-clj-bridge/target/classes";
          bridge-package = pkgs.maven.buildMavenPackage {
            pname = "trixnity-clj-bridge";
            version = "0.0.1";
            src = ./.;
            mvnJdk = pkgs.jdk25;
            buildOffline = true;
            mvnHash = "sha256-wsULsWywuZyWlWaZ33WuAVpzJAtsmcbBHSuuR87AZ5Q=";
            manualMvnArtifacts = [
              "org.jetbrains:annotations:13.0:jar"
              "org.apache.maven.surefire:surefire-junit-platform:3.5.5:jar"
            ];
            preBuild = ''
              export HOME="$TMPDIR/home"
              mkdir -p "$HOME"
              ln -s ${deps-cache}/.gitlibs "$HOME/.gitlibs"
              export JAVA_TOOL_OPTIONS="-Duser.home=$HOME -Djava.io.tmpdir=$TMPDIR -Djna.tmpdir=$TMPDIR"
            '';
            installPhase = ''
              runHook preInstall

              mkdir -p $out/share/trixnity-clj-bridge
              cp -r target $out/share/trixnity-clj-bridge/

              runHook postInstall
            '';
          };
        in
        pkgs.mkCljLib {
          projectSrc = ./.;
          name = "com.outskirtslabs/trixnity-clj";
          version = "0.0.1";
          nativeBuildInputs = [
            pkgs.coreutils
          ];
          JAVA_HOME = pkgs.jdk25.home;
          buildCommand = ''
            cljnix_home="$HOME"
            export HOME="$TMPDIR/home"
            mkdir -p "$HOME"
            ln -s "$cljnix_home/.m2" "$HOME/.m2"
            ln -s "$cljnix_home/.gitlibs" "$HOME/.gitlibs"
            export JAVA_TOOL_OPTIONS="-Duser.home=$HOME -Djava.io.tmpdir=$TMPDIR -Djna.tmpdir=$TMPDIR"
            export JAVA_CMD="${pkgs.jdk25}/bin/java"
            mkdir -p kotlin/build
            if [ -e target/classes ]; then
              chmod -R u+w target/classes
              rm -rf target/classes
            fi
            mkdir -p target/classes
            cp -R ${bridge-classes}/. target/classes/

            clojure -M:dev:kaocha
            clojure -T:build jar
          '';
        };

      devShell =
        pkgs:
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
          ];
          packages = [
            pkgs.deps-lock
            pkgs.jdk25
            pkgs.maven
          ];
        };
    };
}
