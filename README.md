# Pokp Downloader

Aplicativo Android (uso pessoal) para baixar mídia a partir de um link colado:

- **Vídeos do YouTube** → **MP4**, escolhendo a qualidade (360p / 480p / 720p / 1080p / melhor).
- **Áudio do YouTube** → **MP3**.
- **Links do Spotify** (faixa / álbum / playlist) → **MP3** (veja a observação sobre DRM abaixo).

Você também pode usar **"Compartilhar → Pokp Downloader"** a partir dos apps do YouTube/Spotify
para mandar o link direto para o app.

> ⚠️ **Uso pessoal.** Baixar conteúdo do YouTube/Spotify pode violar os Termos de Uso desses
> serviços e leis de direitos autorais. Use apenas com conteúdo que você tem direito de baixar.
> Não publique este app nem credenciais embutidas.

## Como funciona

- O download é feito **no próprio aparelho** usando [`yt-dlp`](https://github.com/yt-dlp/yt-dlp)
  empacotado pela biblioteca
  [`youtubedl-android`](https://github.com/JunkFood02/youtubedl-android) (inclui Python + ffmpeg).
- **Spotify usa DRM** — não é possível baixar o áudio diretamente dele. Como o spotDL/SpotiFlyer,
  o app lê os **metadados** do link (título, artista) pela Spotify Web API, procura a faixa
  equivalente no **YouTube Music** e baixa o áudio de lá.

## Obtendo o APK (GitHub Actions)

Não é necessário Android Studio para gerar o APK:

1. Faça push para a branch (ou rode o workflow **Build APK** manualmente em *Actions → Build APK →
   Run workflow*).
2. Ao terminar, baixe o artefato **`pokp-downloader-debug-apks`** — ele contém os APKs por ABI
   (`arm64-v8a` para a maioria dos celulares modernos, `armeabi-v7a` e um `universal`).
3. Transfira para o celular e instale (habilite "instalar de fontes desconhecidas").

Criar uma **tag** (`v*`) anexa os APKs a um Release do GitHub automaticamente.

> O APK é grande (~150–250 MB no universal) por embutir Python + ffmpeg. Prefira o
> `arm64-v8a` para instalar.

## Configurando o Spotify

Necessário **apenas** para baixar links do Spotify (YouTube funciona sem isso).

1. Crie um app em <https://developer.spotify.com/dashboard> e pegue o **Client ID** e **Client Secret**.
2. **Build no CI:** adicione os secrets do repositório
   `SPOTIFY_CLIENT_ID` e `SPOTIFY_CLIENT_SECRET`
   (*Settings → Secrets and variables → Actions*).
3. **Build local:** copie `local.properties.example` para `local.properties` e preencha os valores.

As credenciais entram via `BuildConfig` e **não** são commitadas (`local.properties` está no
`.gitignore`).

## Build local (opcional)

Requer Android Studio (ou Android SDK + JDK 17):

```bash
cp local.properties.example local.properties   # preencha as credenciais do Spotify (opcional)
./gradlew assembleDebug
# APKs em app/build/outputs/apk/debug/
```

## Stack

- Kotlin + Jetpack Compose (Material 3)
- `io.github.junkfood02.youtubedl-android` (library + ffmpeg + aria2c) `0.18.1`
- OkHttp + kotlinx.serialization (Spotify Web API)
- minSdk 24 · targetSdk 35 · splits por ABI (`arm64-v8a`, `armeabi-v7a`)

## Estrutura

```
app/src/main/java/com/pokp/app/
  MainApplication.kt · MainActivity.kt
  domain/      → UrlClassifier, DownloadFormat, DownloadTask
  data/        → InitManager, YoutubeDlDownloader, MediaStoreSaver
  spotify/     → SpotifyAuth, SpotifyResolver, SpotifyToYoutubeBridge
  viewmodel/   → DownloadViewModel
  ui/          → DownloadScreen, FormatPicker, DownloadItemRow, theme/
```
