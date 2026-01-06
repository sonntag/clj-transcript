{
  config,
  pkgs,
  ...
}: {
  packages = with pkgs; [
    git
    claude-code
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
    echo "🚀 Welcome to your Claude Code development environment!"
    echo "📋 This template includes:"
    echo "   • Claude Code CLI ready to use"
    echo "   • Git with pre-commit hooks configured"
    echo "   • Clean development environment"
    echo ""
    echo "💡 Quick start: Run 'claude' to begin coding with AI assistance"
    echo ""
  '';

  git-hooks.hooks = {
    trim-trailing-whitespace.enable = true;
    end-of-file-fixer.enable = true;
  };
}
