"""Check dependency boundaries against source imports without starting the application."""
from pathlib import Path
backend=Path(__file__).resolve().parents[1]/'backend'
root=backend/'src/main/java/cn/edu/xmu/rbac'
for path in root.rglob('*.java'):
    source=path.read_text()
    assert not any(api in source for api in (
        'import org.springframework.jdbc.', 'import java.sql.', 'import org.apache.ibatis.',
        'createNativeQuery(', 'nativeQuery = true', 'import com.baomidou.mybatisplus.',
    )),path
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
assert '<artifactId>spring-boot-starter-data-jpa</artifactId>' in (backend/'pom.xml').read_text()
print('Architecture boundaries: passed; Spring Data JPA persistence; five business ports each have two implementations')
