#!/usr/bin/env bash
# Generates every Sistrum icon resource from the owner's sources (ImageMagick 7).
#   branding/logo.png  transparent gold logo (foreground, monochrome and notification silhouettes)
#   branding/icon.png  full app icon artwork (About screen)
# The sources are not kept in the repo (owner 2026-10-13). To regenerate, put them back first, e.g.
#   mkdir -p branding && git show ea34914:branding/logo.png > branding/logo.png
#   git show ea34914:branding/icon.png > branding/icon.png
# then run: bash tools/brand/generate_icons.sh  (and delete branding/ again).
set -euo pipefail
cd "$(dirname "$0")/../.."
RES=androidApp/src/main/res
BG='#19140F'  # icon background, sampled from branding/icon.png
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT

# Logo trimmed to its visible pixels, on a square transparent canvas.
magick branding/logo.png -trim +repage -background none -gravity center \
  -extent "%[fx:max(w,h)]x%[fx:max(w,h)]" "$tmp/logo_sq.png"
# White silhouette (alpha >= 50 %) for monochrome/themed and notification icons.
magick "$tmp/logo_sq.png" -alpha extract -threshold 50% "$tmp/mask.png"
magick "$tmp/mask.png" -background white -alpha shape "$tmp/silhouette.png"

declare -A DENS=([mdpi]=1 [hdpi]=1.5 [xhdpi]=2 [xxhdpi]=3 [xxxhdpi]=4)
for d in "${!DENS[@]}"; do
  s=${DENS[$d]}
  canvas=$(python3 -c "print(round(108*$s))")      # adaptive layer: 108 dp
  inner=$(python3 -c "print(round(108*$s*0.60))")   # logo inside the 66 dp safe zone
  legacy=$(python3 -c "print(round(48*$s))")        # legacy launcher: 48 dp
  notif=$(python3 -c "print(round(24*$s))")         # status bar icon: 24 dp
  notifInner=$(python3 -c "print(round(22*$s))")
  mkdir -p "$RES/mipmap-$d" "$RES/drawable-$d"
  # Adaptive foreground: icon-coloured rounded square (69 % of the canvas, as the original
  # Airmedy foreground) with the logo inside it, on a transparent 108 dp canvas.
  sq=$(python3 -c "print(round(108*$s*0.69))"); off=$(( (canvas-sq)/2 )); rad=$(( sq*15/100 ))
  logoIn=$(python3 -c "print(round(108*$s*0.56))")
  magick -size "${canvas}x${canvas}" xc:none -fill "$BG" \
    -draw "roundrectangle $off,$off $((off+sq-1)),$((off+sq-1)) $rad,$rad" \
    \( "$tmp/logo_sq.png" -resize "${logoIn}x${logoIn}" \) -gravity center -composite \
    -quality 95 "$RES/mipmap-$d/ic_launcher_foreground.webp"
  # Monochrome (themed icons): white silhouette on a transparent 108 dp canvas.
  magick "$tmp/silhouette.png" -resize "${inner}x${inner}" -background none -gravity center \
    -extent "${canvas}x${canvas}" "$RES/drawable-$d/ic_launcher_monochrome.png"
  # Notification small icon (white on transparent, tight).
  magick "$tmp/silhouette.png" -resize "${notifInner}x${notifInner}" -background none -gravity center \
    -extent "${notif}x${notif}" "$RES/drawable-$d/ic_notification.png"
  # Legacy square and round launcher icons (background + logo).
  big=$(python3 -c "print(round(48*$s*0.70))")
  magick -size "${legacy}x${legacy}" xc:none -fill "$BG" \
    -draw "roundrectangle 0,0 $((legacy-1)),$((legacy-1)) $((legacy/6)),$((legacy/6))" \
    \( "$tmp/logo_sq.png" -resize "${big}x${big}" \) -gravity center -composite \
    -quality 95 "$RES/mipmap-$d/ic_launcher.webp"
  magick -size "${legacy}x${legacy}" xc:none -fill "$BG" \
    -draw "circle $((legacy/2)),$((legacy/2)) $((legacy/2)),0" \
    \( "$tmp/logo_sq.png" -resize "${big}x${big}" \) -gravity center -composite \
    -quality 95 "$RES/mipmap-$d/ic_launcher_round.webp"
done

# Play Store icon: 512 px, opaque, full bleed (the store applies its own mask).
magick -size 512x512 "xc:$BG" \( "$tmp/logo_sq.png" -resize 380x380 \) -gravity center -composite \
  -alpha off androidApp/src/main/ic_launcher-playstore.png
# About screen: the icon's rounded square only (outer background cut away), 1024 px like the original.
magick branding/icon.png -crop 1034x1034+101+95 +repage \
  \( -size 1034x1034 xc:none -fill white -draw 'roundrectangle 0,0 1033,1033 215,215' \) \
  -alpha set -compose DstIn -composite -resize 1024x1024 "$RES/drawable-nodpi/sistrum_about_app_icon.png"
echo "icons generated"
