#!/usr/bin/env python3
# Copyright 2026 Darshan <darshan@alchiemy.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Branding resource regression checks; requires Pillow and ImageMagick."""
import argparse
import re
import subprocess
import zipfile
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "androidApp/src/main/res"
A = "{http://schemas.android.com/apk/res/android}"
S = "{http://www.w3.org/2000/svg}"
APK = None


class AndroidBrandingTest(unittest.TestCase):
    def test_canonical_vector_and_monochrome(self):
        canonical = ET.parse(ROOT / "assets/brand/logo/rastrio-symbol.svg").getroot().find(S + "path").get("d")
        for name, color in (("foreground", "#FFFDF7"), ("monochrome", "#FFFFFF")):
            vector = ET.parse(RES / f"drawable-v24/ic_launcher_{name}.xml").getroot()
            self.assertEqual(vector.get(A + "viewportWidth"), "108")
            group = vector.find("group")
            self.assertEqual([group.get(A + key) for key in ("translateX", "translateY", "scaleX", "scaleY")], ["18", "18", "3", "3"])
            self.assertEqual(group.find("path").get(A + "pathData"), canonical)
            self.assertEqual(group.find("path").get(A + "fillColor"), color)

    def test_safe_area_and_circle_mask(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "foreground.png"
            subprocess.run(["magick", "-background", "none", str(ROOT / "assets/brand/app/adaptive-foreground.svg"), "-strip", str(output)], check=True)
            image = Image.open(output).convert("RGBA")
            x1, y1, x2, y2 = image.getchannel("A").getbbox()
            self.assertTrue(21 <= x1 < x2 <= 87 and 21 <= y1 < y2 <= 87)
            # Circle is the stricter regular launcher mask; square also preserves this mark.
            for y in range(image.height):
                for x in range(image.width):
                    if image.getpixel((x, y))[3]:
                        self.assertLessEqual((x + 0.5 - 54) ** 2 + (y + 0.5 - 54) ** 2, 36 ** 2)

    def test_legacy_density_and_round_icons(self):
        for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
            for name in ("ic_launcher", "ic_launcher_round"):
                image = Image.open(RES / f"mipmap-{density}/{name}.png").convert("RGBA")
                self.assertEqual(image.size, (size, size))
                pixel = image.getpixel((int(size * 0.36), int(size * 0.6)))
                self.assertTrue(all(abs(actual - expected) <= 1 for actual, expected in zip(pixel, (255, 253, 247, 255))))
                if name.endswith("round"):
                    self.assertEqual(image.getpixel((0, 0))[3], 0)
                else:
                    self.assertEqual(image.getpixel((0, 0)), (49, 92, 115, 255))

    def test_adaptive_references_by_api(self):
        for api in (26, 33):
            for name in ("ic_launcher", "ic_launcher_round"):
                icon = ET.parse(RES / f"mipmap-anydpi-v{api}/{name}.xml").getroot()
                self.assertEqual(icon.find("background").get(A + "drawable"), "@drawable/ic_launcher_background")
                self.assertEqual(icon.find("foreground").get(A + "drawable"), "@drawable/ic_launcher_foreground")
                if api == 33:
                    self.assertEqual(icon.find("monochrome").get(A + "drawable"), "@drawable/ic_launcher_monochrome")
                else:
                    self.assertIsNone(icon.find("monochrome"))

    def test_manifest_and_themes(self):
        application = ET.parse(ROOT / "androidApp/src/main/AndroidManifest.xml").getroot().find("application")
        self.assertEqual(application.get(A + "icon"), "@mipmap/ic_launcher")
        self.assertEqual(application.get(A + "roundIcon"), "@mipmap/ic_launcher_round")
        self.assertEqual(application.get(A + "theme"), "@style/Theme.RastrIO")
        activity = application.find("activity")
        self.assertEqual(activity.get(A + "theme"), "@style/Theme.RastrIO.Starting")
        self.assertEqual(len(application.findall("activity")), 1)
        for qualifier in ("values", "values-night"):
            styles = {style.get("name"): style for style in ET.parse(RES / qualifier / "themes.xml").getroot()}
            starting = styles["Theme.RastrIO.Starting"]
            self.assertEqual(starting.get("parent"), "Theme.SplashScreen")
            values = {item.get("name"): item.text for item in starting}
            self.assertEqual(values["windowSplashScreenAnimatedIcon"], "@drawable/ic_launcher_foreground")
            self.assertEqual(values["windowSplashScreenBackground"], "#315C73")
            self.assertEqual(values["postSplashScreenTheme"], "@style/Theme.RastrIO")

    def test_install_before_on_create_without_delay(self):
        source = (ROOT / "androidApp/src/main/kotlin/com/circuitnext/rastrio/MainActivity.kt").read_text()
        body = source.split("override fun onCreate(savedInstanceState: Bundle?) {", 1)[1]
        self.assertLess(body.index("installSplashScreen()"), body.index("super.onCreate(savedInstanceState)"))
        self.assertNotIn("setKeepOnScreenCondition", source)
        self.assertIn("implementation(libs.androidx.core.splashscreen)", (ROOT / "androidApp/build.gradle.kts").read_text())

    def test_contrast_in_light_and_dark_launcher_contexts(self):
        def luminance(rgb):
            channels = [v / 255 for v in rgb]
            linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in channels]
            return sum(v * weight for v, weight in zip(linear, (0.2126, 0.7152, 0.0722)))
        foreground = luminance((255, 253, 247))
        background = luminance((49, 92, 115))
        self.assertGreater((foreground + 0.05) / (background + 0.05), 4.5)
        # Solid adaptive background keeps mark contrast independent of launcher wallpaper.
        color = ET.parse(RES / "drawable/ic_launcher_background.xml").getroot().find("solid").get(A + "color")
        self.assertEqual(color, "#315C73")


    def test_apk_resource_packaging(self):
        if APK is None:
            self.skipTest("Pass --apk to verify compiled resource packaging")
        with zipfile.ZipFile(APK) as archive:
            names = archive.namelist()
            self.assertIn("AndroidManifest.xml", names)
            self.assertTrue(archive.read("resources.arsc"))
            for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
                for icon in ("ic_launcher", "ic_launcher_round"):
                    pattern = rf"res/mipmap-{density}(?:-v[0-9]+)?/{icon}\.png"
                    self.assertTrue(any(re.fullmatch(pattern, name) for name in names), pattern)
            for api in (26, 33):
                self.assertIn(f"res/mipmap-anydpi-v{api}/ic_launcher.xml", names)
            self.assertIn("res/drawable-v24/ic_launcher_foreground.xml", names)
            self.assertIn("res/drawable-v24/ic_launcher_monochrome.xml", names)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, help="Built debug APK for packaging verification")
    args, remaining = parser.parse_known_args()
    APK = args.apk
    unittest.main(argv=[__file__] + remaining)
