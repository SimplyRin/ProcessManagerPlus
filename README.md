# ProcessManagerPlus
Java や他のサーバー向けソフトウェアのコンソールを Discord で扱えるようにする物

いちいち SSH にアクセスしたりするのが面倒な人や管理者同士でコンソールアクセスを共有したい人におすすめです。

コマンド送信にも対応してます

![console](/images/console.png)

# Config
BungeeCord や一部の Java プロセスでは `-Djline.terminal=jline.UnsupportedTerminal` をつけないと動きません。

Discord Developers のサイトにて、Bot 設定を開き `PRESENCE INTENT` と `SERVER MEMBERS INTENT` をオンにする必要があるかもしれません。

`Discord.AdminList` に登録されているユーザーからのみコマンド送信を許可するようになっているので、万が一第三者がチャットへのアクセス権限を手に入れたとしても多分安心です多分ね。

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
ConsoleMute:
- 'equals|%20'
- 'equals|>>'
- 'equals|>'
Discord:
  Token: DISCORD_BOT_TOKEN
  AdminList:
  - '管理者の Discord ID'
  MuteList:
  - 'equals|%20'
  - 'equals|>>'
  - 'equals|>'
  - 'contains|AutoSaveWorld'
  - "contains|Can't find method saveLevel"
  Channel-ID: 100000000000000000
```
