#!/bin/bash
# Freenux: εγκατάσταση Claude Code (επίσημος installer, linux-arm64)
set -e
echo ">>> Εγκατάσταση Claude Code"
curl -fsSL https://claude.ai/install.sh | bash
export PATH="$HOME/.local/bin:$PATH"
claude --version || true

cat > /usr/share/applications/claude-code.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Claude Code
Exec=xfce4-terminal --title="Claude Code" -x bash -lc "claude; exec bash"
Icon=utilities-terminal
Categories=Development;
EOF

mkdir -p /root/.freenux && touch /root/.freenux/claude.done
echo ">>> Claude Code έτοιμο. Στο desktop: Μενού > Development > Claude Code (ή γράψε claude στο τερματικό)."
