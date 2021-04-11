# ProcessManagerPlus
Java や他のサーバー向けソフトウェアのコンソールを Discord で扱えるようにする物

いちいち SSH にアクセスしたりするのが面倒な人や管理者同士でコンソールアクセスを共有したい人におすすめです。

コマンド送信にも対応してます

![console](/images/console.png)

# Config
BungeeCord や一部の Java プロセスでは `-Djline.terminal=jline.UnsupportedTerminal` をつけないと動きません。

```Yaml
WorkingDirectory: bungee_server
CloseCommand: stop
ExecuteCommand:
- java
- -jar
- -Xms1G
- -Xmx2G
- -server
- -Djline.terminal=jline.UnsupportedTerminal
- BungeeCord.jar
Discord:
  Token: DISCORD_BOT_TOKEN
  AdminList:
  - '管理者の Discord ID'
  Channel-ID: 100000000000000000
```
