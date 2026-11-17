"""Package the current design and optionally apply the course group filename."""
import argparse,shutil
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
p=argparse.ArgumentParser();p.add_argument('--group');a=p.parse_args()
R=Path(__file__).resolve().parents[1];version=(R/'VERSION').read_text().strip()
files=[R/n for n in ['README.md','VERSION','CHANGELOG.md','.gitignore','课件图例与绘图验收.md','课件图稿对应清单.md','绘图规范与参考.md']]
for title in ['统一权限管理系统需求与详细设计说明书','统一权限管理系统概要设计说明书']:
 files += [R/(title+'.adoc'),R/(title+'-v'+version+'.pdf')]
for folder in ['assets/v33','sql','tools']:
 files.extend(p for p in (R/folder).glob('*') if p.is_file())
for name in ['package.json','package-lock.json','export.mjs','audit-v33.mjs','spec.css']:files.append(R/'tooling'/name)
with ZipFile(R/f'统一权限管理系统设计-v{version}.zip','w',ZIP_DEFLATED) as z:
 for source in sorted(set(files)):z.write(source,Path('RBAC设计-v'+version)/source.relative_to(R))
if a.group:
 if not a.group.isalnum():raise SystemExit('Group identifier must contain letters or digits')
 shutil.copyfile(R/f'统一权限管理系统概要设计说明书-v{version}.pdf',R/f'{a.group}_统一权限管理系统_概要设计说明书.pdf')
print(f'Packaged v{version}: {len(set(files))} files')
