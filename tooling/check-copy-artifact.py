"""Verify that the executable JAR uses the compiled copier without its processor."""
from pathlib import Path
from xml.etree import ElementTree
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
pom = ElementTree.parse(root / "backend/pom.xml")
version = pom.findtext("{http://maven.apache.org/POM/4.0.0}version")
with ZipFile(root / f"backend/target/rbac-backend-{version}.jar") as jar:
    names = jar.namelist()
    assert "BOOT-INF/classes/cn/edu/xmu/clonefactory/util/CloneFactory.class" in names
    compiler_libraries = ("clonefactory-", "javapoet-", "auto-service-")
    assert not any(
        name.startswith("BOOT-INF/lib/")
        and any(library in name for library in compiler_libraries)
        for name in names
    ), "Compiler dependencies must stay outside the runtime JAR"
print("Compiled CloneFactory present; annotation processor excluded from runtime JAR")
