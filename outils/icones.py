"""Importe des icônes Material Symbols Outlined (Apache 2.0) en drawables vectoriels Android.

    python3 outils/icones.py

Une seule famille, une seule graisse (600, taille optique 24) : un trait cohérent sur tout
l'écran. Les SVG viennent de fonts.gstatic.com ; chaque fichier produit s'appelle
`ic_<nom>.xml` (contour) ou `ic_<nom>_plein.xml` (rempli, pour l'onglet actif).
"""
import re
import sys
import urllib.request
from pathlib import Path

GRAISSE = 600
# (nom Material Symbols, variantes) ; « plein » ajoute la version remplie.
ICONES = {
    "calendar_view_week": True,
    "menu_book": True,
    "skillet": True,
    "settings": True,
    "add_to_drive": False,
    "cloud_done": False,
    "cloud_off": False,
    "sync_problem": False,
    "error": False,
    "system_update": False,
    "folder": False,
    "account_circle": False,
    "group": False,
    "blender": False,
    "description": False,
    "schedule": False,
    "refresh": False,
    "info": False,
}
BASE = "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/{nom}/{variante}/24px.svg"
SORTIE = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"


def vecteur(svg: str) -> str:
    vue = re.search(r'viewBox="([-\d.\s]+)"', svg).group(1).split()
    x, y, l, h = (float(v) for v in vue)
    chemins = re.findall(r'<path[^>]*\sd="([^"]+)"', svg)
    if not chemins:
        raise ValueError("aucun chemin")
    corps = "\n".join(
        f'        <path\n            android:fillColor="@android:color/white"\n            android:pathData="{d}" />'
        for d in chemins
    )
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Outlined, graisse {GRAISSE}, Apache 2.0 (assets/licences). Généré par outils/icones.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="{l:g}"
    android:viewportHeight="{h:g}">
    <group android:translateX="{-x:g}" android:translateY="{-y:g}">
{corps}
    </group>
</vector>
"""


def main() -> int:
    for nom, plein in ICONES.items():
        for remplie in ([False, True] if plein else [False]):
            variante = f"wght{GRAISSE}" + ("fill1" if remplie else "")
            svg = urllib.request.urlopen(BASE.format(nom=nom, variante=variante), timeout=30).read().decode()
            fichier = SORTIE / f"ic_{nom}{'_plein' if remplie else ''}.xml"
            fichier.write_text(vecteur(svg), encoding="utf-8")
            print(fichier.name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
