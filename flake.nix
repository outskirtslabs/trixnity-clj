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
  };

  outputs =
    inputs@{ self
    , devenv
    , flakelight
    , ...
    }:
    let
      jdk = "jdk25";
    in
    flakelight ./. {
      inherit inputs;
      pname = "trixnity-clj";

      withOverlays = [
        inputs.devshell.overlays.default
        devenv.overlays.default
      ];

      packages = {
        locker =
          pkgs:
          let
            clojure = pkgs.clojure.override { jdk = pkgs.${jdk}; };
            clojureLocker = devenv.clojure.mkLockfile {
              inherit pkgs;
              jdk = pkgs.${jdk};
              src = ./.;
              lockfile = "./deps-lock.json";
            };
          in
          clojureLocker.commandLocker ''
            export HOME="$tmp/home"
            unset CLJ_CACHE CLJ_CONFIG XDG_CACHE_HOME XDG_CONFIG_HOME XDG_DATA_HOME
            ${clojure}/bin/clojure -Srepro -X:deps prep
            ${clojure}/bin/clojure -Srepro -P -M:dev:kaocha
            ${clojure}/bin/clojure -Srepro -P -T:build jar
          '';
      };

      package =
        pkgs:
        let
          clojure = pkgs.clojure.override { jdk = pkgs.${jdk}; };
          sqlite4cljRev = "8b9234061a033c06438b3a0542d987046abf06db";
          clojureLocker = devenv.clojure.mkLockfile {
            inherit pkgs;
            jdk = pkgs.${jdk};
            src = ./.;
            lockfile = "./deps-lock.json";
          };
          bridge-package = pkgs.maven.buildMavenPackage {
            pname = "trixnity-clj-bridge";
            version = "0.0.1";
            src = ./.;
            mvnJdk = pkgs.${jdk};
            buildOffline = true;
            mvnHash = "sha256-wsULsWywuZyWlWaZ33WuAVpzJAtsmcbBHSuuR87AZ5Q=";
            manualMvnArtifacts = [
              "org.jetbrains:annotations:13.0:jar"
              "org.apache.maven.surefire:surefire-junit-platform:3.5.5:jar"
            ];
            mvnParameters = "-Dsqlite4clj.gitlib=${clojureLocker.homeDirectory}/.gitlibs/libs/andersmurphy/sqlite4clj/${sqlite4cljRev}";
            preBuild = ''
              export HOME="$TMPDIR/home"
              mkdir -p "$HOME"
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
            pkgs.git
            pkgs.${jdk}
          ];
          TRIXNITY_CLJ_GIT_SHA =
            if self ? rev then
              self.rev
            else if self ? dirtyRev then
              self.dirtyRev
            else
              "dirty";
          JAVA_HOME = pkgs.${jdk}.home;
          buildPhase = ''
            runHook preBuild

            source ${clojureLocker.shellEnv}
            export JAVA_TOOL_OPTIONS="-Duser.home=$HOME -Djava.io.tmpdir=$TMPDIR -Djna.tmpdir=$TMPDIR"
            export JAVA_CMD="${pkgs.${jdk}}/bin/java"
            mkdir -p kotlin/build
            if [ -e target/classes ]; then
              chmod -R u+w target/classes
              rm -rf target/classes
            fi
            mkdir -p target/classes
            cp -R ${bridge-classes}/. target/classes/

            clojure -Srepro -M:dev:kaocha
            clojure -Srepro -T:build jar

            runHook postBuild
          '';
          installPhase = ''
            runHook preInstall

            mkdir -p $out
            cp "$(find target -type f -name '*.jar' -print | head -n 1)" $out/

            runHook postInstall
          '';
        };

      checks = {
        package = pkgs: self.packages.${pkgs.system}.default;
      };

      devShell =
        pkgs:
        pkgs.devshell.mkShell {
          imports = [
            devenv.capsules.base
            devenv.capsules.clojure
          ];
          env = [
            {
              name = "JAVA_HOME";
              value = pkgs.${jdk}.home;
            }
          ];
          packages = [
            pkgs.${jdk}
            pkgs.maven
            self.packages.${pkgs.system}.locker
          ];
        };
    };
}
