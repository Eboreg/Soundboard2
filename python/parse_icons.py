import yaml


def make_string_elem(style: str, name: str, unicode: str) -> str:
    return '    <string name="' + style + "_" + name.replace("-", "_") + '">&#x' + unicode + ";</string>"


with open("icons.yml") as f:
    icons = yaml.safe_load(f)

strings = []

for name, icon in icons.items():
    for style in icon.get("styles", []):
        if style == "regular":
            strings.append(make_string_elem("fa", name, icon["unicode"]))
        elif style == "solid":
            strings.append(make_string_elem("fas", name, icon["unicode"]))
        elif style == "brands":
            strings.append(make_string_elem("fab", name, icon["unicode"]))

xml = "<resources>\n" + "\n".join(sorted(strings)) + "\n</resources>"

with open("strings.xml", "w") as f:
    f.write(xml)
