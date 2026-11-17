"""Check dependency boundaries against source imports without starting the application."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'backend/src/main/java/cn/edu/xmu/rbac'
for path in root.rglob('*.java'):
    source=path.read_text()
    if '/api/' in str(path):
        assert '.infrastructure.persistence' not in source,path
        assert '.service.structured' not in source and '.service.ddd' not in source,path
    if '/service/ddd/domain/' in str(path):
        assert 'org.springframework' not in source and '.infrastructure' not in source,path
    if '/service/structured/' in str(path) or '/service/ddd/Ddd' in str(path):
        assert '.infrastructure' not in source,path
for module in ['User','Role','Department','System','Permission']:
    for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
        assert (root/f'service/{mode}/{prefix}{module}Service.java').exists()
print('Architecture boundaries: passed; five business ports each have two implementations')
