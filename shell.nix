{ sources ? import ./nix/sources.nix
, nixpkgs ? import sources.nixpkgs {}
}:

(nixpkgs.buildFHSUserEnv {
  name = "eln2-env";
  targetPkgs = pkgs: with pkgs; [ gradle_5 ];
}).env
