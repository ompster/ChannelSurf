# 📺 ChannelSurf

A zero-dependency web app that randomly surfs through a YouTube playlist — playing short clips from random points in each video with retro TV-style transitions between them.

Perfect for ambient background vibes on a TV, Chromecast, or second monitor.

## Features

- **Shuffle play** — randomly picks videos from any YouTube playlist
- **Random seek** — jumps to a random point in each video, not just the start
- **Configurable clip length** — play 10 seconds or 5 minutes of each clip
- **Transition effects:**
  - 📺 TV Static — classic white noise
  - ⬛ Fade to Black
  - ⚡ Glitch — random coloured artifacts
  - 🔵 No Signal — blue screen with "NO SIGNAL"
  - 🎲 Random — different transition each time
- **Now playing** — shows video title (toggleable)
- **Auto-hide UI** — controls disappear during playback, tap to reveal
- **Remembers settings** — playlist ID, clip length, and preferences saved in localStorage
- **No repeats** — avoids recently played videos before cycling back

## Usage

1. Host `index.html` anywhere — a local web server, Nginx, or even GitHub Pages
2. Open it in a browser
3. Enter a YouTube playlist ID (or use the default)
4. Hit **Start**
5. Cast the tab to a Chromecast, or just fullscreen it

### Getting a Playlist ID

From a YouTube playlist URL like:
```
https://youtube.com/playlist?list=PLCgY5X6keprecXzwA9jsi92DhiNZ-_KoY
```
The playlist ID is: `PLCgY5X6keprecXzwA9jsi92DhiNZ-_KoY`

## Self-Hosting

It's a single HTML file. No build step, no dependencies, no npm install. Just serve it:

```bash
# Python
python -m http.server 8888

# Or drop it in any web server's www folder
cp index.html /var/www/html/channelsurf/
```

## Requirements

- A modern browser with JavaScript enabled
- Internet connection (loads YouTube iframe API)
- That's it

## License

MIT
