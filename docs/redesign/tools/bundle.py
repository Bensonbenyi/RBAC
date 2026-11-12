"""Package the current design source, rendered PDF, models and build tools."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
R=Path(__file__).resolve().parents[1]
version=(R/'VERSION').read_text().strip()
files=[R/n for n in ['README.md','VERSION','CHANGELOG.md','.gitignore','统一权限管理系统需求与详细设计说明书.adoc',f'统一权限管理系统需求与详细设计说明书-v{version}.pdf','课件图例与绘图验收.md']]
for folder in ['assets/spec','sql']:
    files.extend(p for p in (R/folder).glob('*') if p.is_file())
for stem in ['rbac-chen-v1','rbac-schema-v1']:files.extend((R/'assets').glob(stem+'.*'))
files.extend((R/'tooling'/n) for n in ['package.json','package-lock.json','export.mjs','audit-diagrams.mjs','spec.css'])
files.extend((R/'tools').glob('*.py'))
for name in ['流程配图覆盖清单.md','绘图规范与参考.md','课件图稿对应清单.md']:
    if (R/name).exists():files.append(R/name)
with ZipFile(R/f'统一权限管理系统设计-v{version}.zip','w',ZIP_DEFLATED) as z:
    for p in sorted(set(files)):z.write(p,Path(f'RBAC设计-v{version}')/p.relative_to(R))
print(f'Packaged v{version}: {len(set(files))} files')
