<div align="center"><img src="https://hub.despical.dev/plugin-icons/content/portbridge/cover-18954feade6c.png" alt="Port Bridge promotional cover"><br>
<br>
<h1><strong>Port Bridge</strong></h1><br>
<h3><strong>Host locally. Play together.</strong></h3><br>
<br>
<strong>Managed tunnels  ·  Copyable addresses  ·  Automatic reconnection</strong><br>
Keep your Minecraft world on your computer. Give friends a way in.<br>
<br>
<a href="https://docs.despical.dev/port-bridge/"><strong>Explore the documentation</strong></a>  ·  <a href="https://docs.despical.dev/port-bridge/getting-started/"><strong>Open the installation guide</strong></a></div><br>
<br>
Keep your world on your own computer and give friends an address they can use in ordinary Minecraft Java. <strong>Port Bridge connects your local server to a Bore relay, so you can share it without setting up router port forwarding.</strong><br>
<br>
Start a small survival world, test a build with friends or open a temporary development server. The Minecraft server, your computer and the tunnel must remain running while people play.<br>
<br>
<hr><br>
<br>
<div align="center"><span><span><strong>01  /  A SIMPLE CONNECTION</strong></span></span><br>
<h2><strong>From your local world to their server list.</strong></h2></div><br>
<br>
Your Minecraft server stays where it is. Port Bridge detects its bind address and port, starts the Bore client and connects to your chosen relay. The relay allocates a public port that carries players' connections back to your local server.<br>
<br>
<div align="center"><img src="https://hub.despical.dev/plugin-icons/content/portbridge/flow-c4b6fe539313.png" alt="Port Bridge"><br>
<span>Your server connects to the relay. Friends join using the public address.</span></div><br>
<br>
<ol><li><strong>Run your server.</strong> Port Bridge detects the running server's bind address and Minecraft port.</li><li><strong>Open a tunnel.</strong> The managed Bore client connects to the relay and receives a public port.</li><li><strong>Share the address.</strong> Friends enter it in Minecraft Java's Multiplayer > Direct Connection screen. They need no tunnel client or additional software.</li></ol><br>
<br>
Port Bridge checks Minecraft's status response and matching ping through the local listener and, by default, the public route. A relay allocation alone does not mean the route is verified.<br>
<br>
<hr><br>
<br>
<div align="center"><span><span><strong>02  /  MANAGED FOR YOU</strong></span></span><br>
<h2><strong>A connection you can see and control.</strong></h2></div><br>
<br>
<strong>Connect when you are ready.</strong> Open the tunnel shortly after Paper starts, or disable automatic startup and connect with <strong>/port start</strong>. The pinned <strong>Bore v0.6.0</strong> client is downloaded automatically with SHA-256 archive and cached-executable verification. A custom executable path is also supported.<br>
<br>
<strong>Keep the address close.</strong> Run <strong>/port address</strong>, copy it from the console or click <strong>Copy Address</strong> in chat. The panels include permission-aware command buttons, measured ping latency, retry timing and the latest diagnostic.<br>
<br>
<strong>Recover from interruptions.</strong> Configure retry delays, route failure thresholds and attempt limits. Automatic reconnection helps restore the connection, while an explicit <strong>/port stop</strong> closes the tunnel and cancels retries.<br>
<br>
<strong>Choose your public port.</strong> Request a port or let the relay allocate one. Optional fallback tries a random port when your requested port is occupied. Port availability depends on the relay, and an address can change after a restart or reconnection.<br>
<br>
<strong>Make it fit your server.</strong> Use a private Bore relay with an optional shared secret and displayed hostname. Customize command replies, labels, buttons, hover text, connection details, timing values and ACF permission/error replies in <strong>messages.yml</strong>.<br>
<br>
Downloads, process output and network checks run away from the server thread. The managed Bore process is closed when the plugin shuts down.<br>
<br>
<hr><br>
<br>
<div align="center"><span><span><strong>03  /  YOUR FIRST TUNNEL</strong></span></span><br>
<h2><strong>Install. Start. Share.</strong></h2></div><br>
<br>
<strong>Requirements:</strong> Paper for Minecraft <strong>1.21.11 or later</strong>, <strong>Java 25 or newer</strong> and an outbound connection to your chosen Bore relay. Keep <strong>enable-status=true</strong> in server.properties. Folia and Spigot are not supported.<br>
<br>
<ol><li>Download <strong>port-bridge-1.0.0.jar</strong> and place it in your server's <strong>plugins</strong> folder.</li><li>Start the server. Default config.yml and messages.yml files are installed when they are missing.</li><li>The default configuration starts a tunnel automatically. On the first connection, the supported Bore client is downloaded if needed.</li><li>Run <strong>/port address</strong>, copy the address and send it to your friends.</li></ol><br>
<br>
<div align="center"><strong>/port help</strong><br>
Find your controls.<br>
<br>
<strong>/port status</strong><br>
Check the connection.<br>
<br>
<strong>/port address</strong><br>
Copy the address and invite your friends.<br>
</div><br>
<br>
If automatic startup is disabled, use <strong>/port start</strong>. The whitelist, account authentication and Minecraft client-version requirements still apply.<br>
<br>
<a href="https://docs.despical.dev/port-bridge/getting-started/"><strong>Follow the installation guide</strong></a>  ·  <a href="https://docs.despical.dev/port-bridge/compatibility/"><strong>Check compatibility</strong></a><br>
<br>
<hr><br>
<br>
<div align="center"><span><span><strong>04  /  COMMANDS THAT STAY CLOSE</strong></span></span><br>
<h2><strong>Manage the tunnel from chat or console.</strong></h2></div><br>
<br>
<strong>/port</strong>, <strong>/pb</strong> and <strong>/portbridge</strong> share the same commands.<br>
<br>
<ul><li><strong>/port status</strong> - View the state, local target, relay, latency, retries and latest error. The base command also shows status.</li><li><strong>/port help</strong> - View available commands with clickable suggestions.</li><li><strong>/port address</strong> - View and copy the public connection address. <strong>/port ip</strong> is an alias.</li><li><strong>/port start</strong> - Start a tunnel in the background.</li><li><strong>/port stop</strong> - Close the tunnel and cancel automatic retries.</li><li><strong>/port restart</strong> - Start a new tunnel session; the public address may change.</li><li><strong>/port reload</strong> - Validate settings and messages, then reconnect an active tunnel. An explicitly stopped tunnel stays stopped.</li></ul><br>
<br>
<details><summary><strong>Permissions for administrators and trusted players</strong></summary><br>
<strong>portbridge.admin</strong> grants every command permission. All permissions default to operators.<br>
<ul><li><strong>portbridge.status</strong> - Status and help.</li><li><strong>portbridge.address</strong> - View/copy the address, receive connection announcements and optional join notifications.</li><li><strong>portbridge.manage</strong> - Start, stop and restart.</li><li><strong>portbridge.reload</strong> - Reload settings and messages.</li></ul><br>
Chat controls appear only when the sender has the corresponding permission.<br>
</details><br>
<br>
<a href="https://docs.despical.dev/port-bridge/commands/"><strong>Command reference</strong></a>  ·  <a href="https://docs.despical.dev/port-bridge/permissions/"><strong>Permission reference</strong></a><br>
<br>
<hr><br>
<br>
<div align="center"><span><span><strong>05  /  MAKE IT YOURS</strong></span></span><br>
<h2><strong>Choose your relay. Tune your connection.</strong></h2></div><br>
<br>
<strong>config.yml</strong> controls startup, local port detection, relay settings, Bore installation, reconnection, health checks and joining-player notifications.<br>
<br>
<strong>messages.yml</strong> controls the chat design and every plugin command reply. Use literal text, {name} placeholders and quoted #RRGGBB theme colors. Missing keys fall back to bundled defaults; existing files are preserved. External diagnostic values keep their original text.<br>
<br>
Edit either file and run <strong>/port reload</strong>. Invalid settings or messages reject the reload and retain the previous configuration.<br>
<br>
<details><summary><strong>Example: connect through your own Bore relay</strong></summary><br>
Run a compatible Bore relay on a reachable server, then set your relay hostname and secret in config.yml:<br>
<pre><code>relay:
  host: relay.example.com
  public-host: ''
  port: 0
  fallback-to-random: true
  secret: 'your-private-secret'</code></pre><br>
The relay's control port is TCP 7835; allocated public ports must also be reachable. The shared secret authenticates tunnel creation and does not encrypt the forwarded traffic.<br>
</details><br>
<br>
<a href="https://docs.despical.dev/port-bridge/configuration/config/"><strong>Complete config.yml reference</strong></a>  ·  <a href="https://docs.despical.dev/port-bridge/configuration/messages/"><strong>Complete messages.yml reference</strong></a><br>
<br>
<hr><br>
<br>
<div align="center"><span><span><strong>06  /  BEFORE YOU CONNECT</strong></span></span><br>
<h2><strong>Know what your address represents.</strong></h2></div><br>
<br>
<details><summary><strong>Does it change my Minecraft port or router settings?</strong></summary><br>
No. Automatic detection reads the existing Minecraft listener. The public port belongs to the relay; Port Bridge does not change your server's local port or router configuration.<br>
</details><br>
<details><summary><strong>Will the address stay the same?</strong></summary><br>
It can change after a restart or reconnection. A requested public port remains subject to relay availability and policy. The default public bore.pub relay is operated independently and can be unavailable.<br>
</details><br>
<details><summary><strong>Does it forward Bedrock, UDP query or real player IP addresses?</strong></summary><br>
Port Bridge forwards Minecraft Java TCP traffic. It does not expose Bedrock or UDP services, and forwarded connections do not preserve real player IP addresses. A relay can add latency.<br>
</details><br>
<details><summary><strong>Which operating systems support automatic downloads?</strong></summary><br>
Windows x64/x86 and Linux/macOS x64/ARM64. Other platforms need a compatible executable configured in bore.executable.<br>
</details><br>
<details><summary><strong>What if the address is not reachable?</strong></summary><br>
Check /port status, keep enable-status=true, confirm your local Minecraft listener is running and check the relay's availability. By default, public route failures trigger reconnection after the configured failure threshold. Disabling public health checks leaves the route unverified; local listener checks still run.<br>
</details><br>
<br>
<a href="https://docs.despical.dev/port-bridge/troubleshooting/"><strong>Open the troubleshooting guide</strong></a><br>
<br>
<hr><br>
<br>
<div align="center"><span><span><strong>07  /  BSTATS METRICS</strong></span></span><br>
<h2><strong>bStats Metrics</strong></h2></div><br>
<br>
Port Bridge collects <strong>anonymous usage statistics</strong> through <strong>bStats</strong>. These aggregate statistics show server counts and version adoption and help guide future development. Server owners can disable collection in <strong>plugins/bStats/config.yml</strong>.<br>
<br>
<div align="center"><a href="https://bstats.org/plugin/bukkit/Port%20Bridge/34669"><img src="https://bstats.org/signatures/bukkit/Port%20Bridge.svg" alt="Port Bridge"></a></div><br>
<br>
<hr><br>
<br>
<div align="center"><h3><strong>Your next game starts with one address.</strong></h3><br>
Free and open source under GPL-3.0-or-later. Created by <strong>Despical</strong>.<br>
<br>
<a href="https://docs.despical.dev/port-bridge/getting-started/"><strong>Installation guide</strong></a><br>
<a href="https://docs.despical.dev/port-bridge/"><strong>Documentation</strong></a>  ·  <a href="https://github.com/Despical/PortBridge"><strong>Source code</strong></a>  ·  <a href="https://discord.gg/uXVU8jmtpU"><strong>Discord support</strong></a><br>
<a href="https://javadoc.despical.dev/portbridge/"><strong>Javadocs</strong></a><br>
</div>
<br>
<details><summary><strong>AI disclosure</strong></summary>Minimal AI assistance during development; the developer subsequently rewrote most of the implementation. AI assisted with the cover artwork and this description.</details>