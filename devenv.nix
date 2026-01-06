{
  config,
  pkgs,
  ...
}: {
  packages = with pkgs; [
    git
    claude-code
    clojure
    clj-kondo
    babashka
  ];

  claude.code = {
    enable = true;
    mcpServers = {
      # Local devenv MCP server
      devenv = {
        type = "stdio";
        command = "devenv";
        args = ["mcp"];
        env = {
          DEVENV_ROOT = config.devenv.root;
        };
      };
    };
  };

  enterShell = ''
    echo ""
    echo "🧪 clj-transcript - Transcript-based testing for Clojure"
    echo ""
    echo "Commands:"
    echo "  transcript run           - Run all transcript tests"
    echo "  transcript run <file>    - Run a specific transcript"
    echo "  transcript accept <file> - Accept output for a transcript"
    echo "  transcript accept-all    - Accept all transcript outputs"
    echo ""
    echo "  clj -X:test              - Run unit tests"
    echo "  clj -M:repl              - Start a REPL"
    echo ""
  '';

  scripts.transcript.exec = ''
    clojure -M:cli "$@"
  '';

  git-hooks.hooks = {
    trim-trailing-whitespace.enable = true;
    end-of-file-fixer.enable = true;
  };
}
