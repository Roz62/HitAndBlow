# HitAndBlow

Paper 1.21.8 用、2人対戦のヒット＆ブロー（ラージチェスト GUI）。仕様は [SPEC.md](SPEC.md)。

## ビルド
Gradle は不要。テストサーバー（`C:\paper-server`）の `libraries` にある Paper API を使って、javac で直接ビルドする。

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1           # build\libs\HitAndBlow-<version>.jar を作る
powershell -ExecutionPolicy Bypass -File build.ps1 -Deploy   # さらにテストサーバーの plugins に配置
```
- サーバーの場所が違うときは `-Server <パス>` か、環境変数 `PAPER_SERVER` で指定する。
- 配置した後は、**サーバーの再起動**で反映される（`/reload` は非推奨）。

## ファイル構成
| ファイル | 役割 |
|---|---|
| `HitAndBlowPlugin.java` | 本体。config の読み込み、コマンドとイベントの登録 |
| `HbCommand.java` | `/hb` の各サブコマンドとタブ補完 |
| `GameManager.java` | 募集（60秒で自動取り消し）と、進行中ゲームの管理 |
| `Game.java` | 1試合の進行（手番・タイマー・判定・終了）と GUI 描画。スロット配置の図あり |
| `GameView.java` | プレイヤー1人分の GUI（InventoryHolder） |
| `GameListener.java` | GUI クリック、ドラッグ禁止、ログアウト時の放棄負け |
| `Judge.java` | ヒット・ブローの判定（色の重複あり） |
| `HbColor.java` | 6色の定義（ブロック、ガラス、チャットの色） |
| `Items.java` / `Msg.java` | アイテム生成とメッセージ用の部品 |
