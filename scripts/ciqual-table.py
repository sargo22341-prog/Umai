#!/usr/bin/env python3
"""
Builds app/src/main/assets/ciqual.tsv, the table of basic foods umai looks
up when a food is typed in the plan, from the XML release of the Ciqual
table (ANSES, Licence Ouverte / Etalab 2.0): https://ciqual.anses.fr/
The 2025 release is the archive
https://ciqual.anses.fr/cms/sites/default/files/inline-files/2025_11_03.7z
(SHA-256 b3b34e58890263d0c5959a157de4470f1638271a73235b77750941d96979ae27).

    python3 scripts/ciqual-table.py alim_2025_11_03.xml compo_2025_11_03.xml > app/src/main/assets/ciqual.tsv

Only the nutrients of a nutrition label are kept, for 100 g. A value below
the limit of quantification ("< 0,5") or in traces counts as 0; an unknown
one ("-") is left empty. A food without its energy is left out.
"""

import re
import sys

# Ciqual constituent codes, in the order of the Nutrient enum of the app.
ENERGY_EU = "328"
ENERGY_JONES = "333"
NUTRIENTS = ["40000", "40302", "31000", "32000", "34100", "25000", "10004"]
BEVERAGES = "06"

ALIM = re.compile(
    r"<alim_code>\s*(\d+)\s*</alim_code>\s*"
    r"<alim_nom_fr>([^<]*)</alim_nom_fr>\s*"
    r"<alim_nom_eng>([^<]*)</alim_nom_eng>.*?"
    r"<alim_grp_code>\s*(\d+)\s*</alim_grp_code>",
    re.S,
)
COMPO = re.compile(
    r"<alim_code>\s*(\d+)\s*</alim_code>\s*"
    r"<const_code>\s*(\d+)\s*</const_code>\s*"
    r"<teneur>([^<]*)</teneur>"
)


def unescape(text):
    # A name may run over several lines in the XML.
    return (
        " ".join(text.split())
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&apos;", "'")
        .replace("&quot;", '"')
        .replace("&amp;", "&")
    )


def value(text):
    text = unescape(text)
    if text in ("", "-"):
        return ""
    if text == "traces" or text.startswith("<"):
        return "0"
    number = float(text.replace(",", "."))
    return f"{number:.3f}".rstrip("0").rstrip(".")


def main(alim_path, compo_path):
    with open(alim_path, encoding="utf-8-sig") as file:
        foods = ALIM.findall(file.read())
    wanted = {ENERGY_EU, ENERGY_JONES, *NUTRIENTS}
    values = {}
    with open(compo_path, encoding="utf-8-sig") as file:
        for code, constituent, content in COMPO.findall(file.read()):
            if constituent in wanted:
                values.setdefault(code, {})[constituent] = value(content)

    print("code\tname_fr\tname_en\tdrink\tkcal\tfat\tsaturated\tcarbohydrates\tsugars\tfiber\tprotein\tsalt")
    for code, name_fr, name_en, group in sorted(foods, key=lambda food: int(food[0])):
        food = values.get(code, {})
        energy = food.get(ENERGY_EU) or food.get(ENERGY_JONES)
        if not energy:
            continue
        drink = "1" if group == BEVERAGES else "0"
        columns = [code, unescape(name_fr), unescape(name_en), drink, energy]
        columns += [food.get(nutrient, "") for nutrient in NUTRIENTS]
        print("\t".join(columns))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("usage: ciqual-table.py alim.xml compo.xml > ciqual.tsv")
    # Windows would otherwise write the names in its ANSI code page.
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    main(sys.argv[1], sys.argv[2])
