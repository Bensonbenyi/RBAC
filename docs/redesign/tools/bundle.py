"""Package the current design and optionally apply the course group filename."""
import argparse,shutil
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
p=argparse.ArgumentParser();p.add_argument('--group');a=p.parse_args()
R=Path(__file__).resolve().parents[1];version=(R/'VERSION').read_text().strip()
files=[R/n for n in ['README.md','VERSION','CHANGELOG.md','.gitignore','课件图例与绘图验收.md','课件图稿对应清单.md','绘图规范与参考.md','与v3.2.2对照修订.md','流程配图覆盖清单.md']]
files.append(R.parent/'OOAD_Grading_2026_V4.pdf')
for title in ['统一权限管理系统需求与详细设计说明书','统一权限管理系统概要设计说明书']:
 files += [R/(title+'.adoc'),R/(title+'-v'+version+'.pdf')]
for folder in ['assets/v33','sql','tools']:
 files.extend(p for p in (R/folder).glob('*') if p.is_file())
files.extend(source for source in (R.parent/'implementation').rglob('*') if source.is_file())
for name in ['package.json','package-lock.json','export.mjs','audit-v33.mjs','spec.css']:files.append(R/'tooling'/name)
with ZipFile(R/f'统一权限管理系统设计-v{version}.zip','w',ZIP_DEFLATED) as z:
 root=Path('RBAC设计-v'+version)
 z.writestr(str(root/'README.md'),f'# RBAC 设计 v{version}\n\n[设计文档与图稿](redesign/README.md)\n\n[本轮整改与验证](implementation/整改报告-v{version}.md)\n')
 for source in sorted(set(files)):
  target=root/source.relative_to(R.parent)
  if source==R/'README.md':
   text=source.read_text().replace(f'[设计源文件包](统一权限管理系统设计-v{version}.zip)','设计源文件包（当前解压目录）')
   z.writestr(str(target),text)
  else:z.write(source,target)
if a.group:
 if not a.group.isalnum():raise SystemExit('Group identifier must contain letters or digits')
 shutil.copyfile(R/f'统一权限管理系统概要设计说明书-v{version}.pdf',R/f'{a.group}_统一权限管理系统_概要设计说明书.pdf')
print(f'Packaged v{version}: {len(set(files))} files')
