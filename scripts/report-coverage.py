"""保留 JaCoCo 原始计数和测试证据，不能将测量模式标成验收通过。"""

import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

destination = Path(".runtime/coverage")
modules = {}
for module in ("platform", "demo-client"):
    report = Path(module) / "target/site/jacoco"
    counters = {}
    if (report / "jacoco.xml").exists():
        root = ET.parse(report / "jacoco.xml").getroot()
        for counter in root.findall("counter"):
            missed, covered = int(counter.get("missed")), int(counter.get("covered"))
            counters[counter.get("type")] = {
                "missed": missed, "covered": covered,
                "percent": round(100 * covered / (missed + covered), 4)
                if missed + covered else 100,
            }
        shutil.copytree(report, destination / module, dirs_exist_ok=True)
    suites = []
    for test_report in sorted(Path(module).glob("target/surefire-reports/TEST-*.xml")):
        suite = ET.parse(test_report).getroot()
        suites.append({key: suite.get(key) for key in
                       ("name", "tests", "failures", "errors", "skipped", "time")})
    modules[module] = {"counters": counters, "tests": suites}
    print(f"{module}: " + ", ".join(
        f"{kind}={counters[kind]['percent']}% (未覆盖 {counters[kind]['missed']})"
        for kind in ("LINE", "BRANCH") if kind in counters))

aggregate = Path("coverage-report/target/site/jacoco-aggregate")
if aggregate.exists():
    shutil.copytree(aggregate, destination / "aggregate", dirs_exist_ok=True)
metadata = {
    "exitCode": int(sys.argv[1]), "measurementOnly": sys.argv[2] == "true",
    "sourceCommit": (destination / "source-commit.txt").read_text().strip(),
    "sourceDiffSha256": (destination / "source-diff-sha256.txt").read_text().split()[0],
    "startedAt": (destination / "started-at.txt").read_text().strip(),
    "jacocoVersion": "0.8.13", "modules": modules,
}
metadata["accepted"] = (
    metadata["exitCode"] == 0 and not metadata["measurementOnly"]
    and all(modules[module]["counters"].get(kind, {}).get("missed") == 0
            for module in modules for kind in ("LINE", "BRANCH"))
)
(destination / "verification.json").write_text(
    json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("覆盖率验收通过" if metadata["accepted"] else "覆盖率尚未验收通过")
