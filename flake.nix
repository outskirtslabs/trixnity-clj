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
    clojure-nix-locker = {
      url = "github:bevuta/clojure-nix-locker";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    inputs@{ self, flakelight, ... }:
    flakelight ./. {
      inherit inputs;
      pname = "trixnity-clj";

      withOverlays = [
        inputs.devshell.overlays.default
        inputs.devenv.overlays.default
      ];

      packages = {
        locker =
          pkgs:
          let
            clojure = pkgs.clojure.override { jdk = pkgs.jdk25; };
          in
          (inputs.clojure-nix-locker.lib.customLocker {
            inherit pkgs;
            src = ./.;
            lockfile = "./deps-lock.json";
            command = ''
              ${clojure}/bin/clojure -P -M:dev:kaocha
              ${clojure}/bin/clojure -P -T:build jar
            '';
          }).locker;
      };

      package =
        pkgs:
        let
          clojure = pkgs.clojure.override { jdk = pkgs.jdk25; };
          clojureLocker = inputs.clojure-nix-locker.lib.customLocker {
            inherit pkgs;
            src = ./.;
            lockfile = "./deps-lock.json";
            command = ''
              ${clojure}/bin/clojure -P -M:dev:kaocha
              ${clojure}/bin/clojure -P -T:build jar
            '';
          };
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
              ln -s ${clojureLocker.homeDirectory}/.gitlibs "$HOME/.gitlibs"
              export JAVA_TOOL_OPTIONS="-Duser.home=$HOME -Djava.io.tmpdir=$TMPDIR -Djna.tmpdir=$TMPDIR"
            '';
            installPhase = ''
              runHook preInstall

              mkdir -p $out/share/trixnity-clj-bridge
              cp -r target $out/share/trixnity-clj-bridge/

              runHook postInstall
            '';
          };
          bridge-classes = "${bridge-package}/share/trixnity-clj-bridge/target/classes";
        in
        pkgs.stdenv.mkDerivation {
          pname = "trixnity-clj";
          version = "0.0.1";
          src = ./.;
          nativeBuildInputs = [
            clojure
            pkgs.coreutils
            pkgs.findutils
            pkgs.jdk25
          ];
          TRIXNITY_CLJ_GIT_SHA =
            if self ? rev then
              self.rev
            else if self ? dirtyRev then
              self.dirtyRev
            else
              "dirty";
          JAVA_HOME = pkgs.jdk25.home;
          buildPhase = ''
            runHook preBuild

            source ${clojureLocker.shellEnv}
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

            runHook postBuild
          '';
          installPhase = ''
            runHook preInstall

            mkdir -p $out
            cp "$(find target -type f -name '*.jar' -print | head -n 1)" $out/

            runHook postInstall
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
            pkgs.jdk25
            pkgs.maven
            self.packages.${pkgs.system}.locker
          ];
        };
    };
}
