#!/usr/bin/env python3
"""Rysuje zrzuty ekranu do README (docs/podglad-*.png) z plików target/podglady/*.tsv.

Pliki .tsv zapisuje test PodgladyEkranuTest (zawartość ekranu gry: znak, kolor, tło, pogrubienie
każdej komórki). Skrypt rysuje je z kolorowymi emoji (czcionka Segoe UI Emoji na Windows).

Użycie:
    mvn test -Dtest=PodgladyEkranuTest -Dpodglady=true
    python narzedzia/zrob-podglady.py
Wymaga: Pillow, fontTools, czcionki Consolas, Segoe UI Emoji i Segoe UI Symbol (Windows).
"""
import sys
from pathlib import Path

from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

KORZEN = Path(__file__).resolve().parent.parent
FONTY = Path("C:/Windows/Fonts")
KOMORKA_W, KOMORKA_H = 10, 21

TEKST = ImageFont.truetype(str(FONTY / "consola.ttf"), 17)
TEKST_GRUBY = ImageFont.truetype(str(FONTY / "consolab.ttf"), 17)
SYMBOLE = ImageFont.truetype(str(FONTY / "seguisym.ttf"), 14)
EMOJI = ImageFont.truetype(str(FONTY / "seguiemj.ttf"), 18)
ZNAKI_TEKSTU = set(TTFont(str(FONTY / "consola.ttf")).getBestCmap() or {})
# Linia bazowa tekstu w komórce: wszystkie czcionki stawiamy na tej samej wysokości.
BAZA = 16


def kolor(hex_):
    v = int(hex_, 16)
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


def blok(d, znak, x, y, rgb):
    """Bloki rysujemy prostokątami, żeby sceneria nie miała szczelin między komórkami."""
    px, py = x * KOMORKA_W, y * KOMORKA_H
    w, h = KOMORKA_W, KOMORKA_H
    prostokaty = {
        "█": (0, 0, w, h), "▀": (0, 0, w, h // 2), "▄": (0, h // 2, w, h),
        "▌": (0, 0, w // 2, h), "▐": (w // 2, 0, w, h),
        # Paski życia i morale: ciągła kreska bez przerw między komórkami.
        "─": (0, h // 2 - 1, w, h // 2 + 1), "━": (0, h // 2 - 1, w, h // 2 + 2),
    }
    if znak not in prostokaty:
        return False
    x0, y0, x1, y1 = prostokaty[znak]
    d.rectangle([px + x0, py + y0, px + x1 - 1, py + y1 - 1], fill=rgb)
    return True


def rysuj(tsv, png):
    linie = tsv.read_text(encoding="utf-8").split("\n")
    szer, wys = map(int, linie[0].split("\t"))
    komorki = []
    for linia in linie[1:]:
        if not linia:
            continue
        x, y, znak, fg, bg, gruby = linia.split("\t")
        komorki.append((int(x), int(y), znak, kolor(fg), kolor(bg), gruby == "1"))
    obraz = Image.new("RGB", (szer * KOMORKA_W, wys * KOMORKA_H))
    d = ImageDraw.Draw(obraz)
    for x, y, _, _, bg, _ in komorki:
        d.rectangle([x * KOMORKA_W, y * KOMORKA_H, (x + 1) * KOMORKA_W - 1, (y + 1) * KOMORKA_H - 1], fill=bg)
    for x, y, znak, fg, _, gruby in komorki:
        if not znak.strip():
            continue
        if blok(d, znak, x, y, fg):
            continue
        px, py = x * KOMORKA_W, y * KOMORKA_H
        if all(ord(c) in ZNAKI_TEKSTU for c in znak):
            d.text((px, py + BAZA), znak, font=TEKST_GRUBY if gruby else TEKST, fill=fg, anchor="ls")
        elif any(ord(c) > 0x2FFF for c in znak) or "\ufe0f" in znak:
            # Emoji zajmuje dwie komórki: środek emoji na środku tych dwóch komórek, w kolorze.
            d.text((px + KOMORKA_W, py + KOMORKA_H // 2), znak, font=EMOJI, embedded_color=True, anchor="mm")
        else:
            d.text((px + KOMORKA_W // 2, py + KOMORKA_H // 2), znak, font=SYMBOLE, fill=fg, anchor="mm")
    obraz.save(png)
    print("zapisano", png.relative_to(KORZEN))


def main():
    zrodla = sorted((KORZEN / "target" / "podglady").glob("*.tsv"))
    if not zrodla:
        sys.exit("Brak target/podglady/*.tsv. Najpierw: mvn test -Dtest=PodgladyEkranuTest -Dpodglady=true")
    for tsv in zrodla:
        rysuj(tsv, KORZEN / "docs" / (tsv.stem + ".png"))


if __name__ == "__main__":
    main()
