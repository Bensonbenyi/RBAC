#!/usr/bin/env python3
"""从课程表格生成匿名组织、账号和两库课程名册 SQL。"""

import argparse
from collections import defaultdict
from pathlib import Path
from xml.etree import ElementTree
from zipfile import ZipFile

DOCS = Path(__file__).resolve().parents[1] / 'docs'
ROSTER_SQL = DOCS.parent / 'integrations/course-project-mgt/link-scale-roster.sql'
XML = {'x': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
ROOT = '规模演示组织'
PREFIX = 'ooad-load-2026-'


def rows(file, columns):
    with ZipFile(DOCS / file) as book:
        strings = [
            ''.join(node.itertext()).strip()
            for node in ElementTree.fromstring(book.read('xl/sharedStrings.xml'))
        ]
        sheet = next(name for name in book.namelist()
                     if name.startswith('xl/worksheets/sheet') and name.endswith('.xml'))
        data = ElementTree.fromstring(book.read(sheet)).findall('x:sheetData/x:row', XML)
    for row in data[1:]:
        selected = {}
        for cell in row.findall('x:c', XML):
            col = cell.attrib['r'].rstrip('0123456789')
            value = cell.findtext('x:v', namespaces=XML)
            if col in columns and value is not None:
                selected[col] = strings[int(value)] if cell.get('t') == 's' else value
        yield tuple(selected.get(col, '') for col in columns)


def literal(value):
    # UTF-8 十六进制字面量与 MySQL 的反斜线转义模式无关。
    return 'CONVERT(0x' + value.encode().hex() + ' USING utf8mb4) COLLATE utf8mb4_0900_as_cs'


def build(destination):
    hierarchy = dict(rows('市政公司部门信息表.xlsx', 'AC'))
    children = defaultdict(list)
    for name, parent in hierarchy.items():
        children[parent].append(name)

    accounts = defaultdict(list)
    roster = {'teacher': [], 'student': []}
    for number, (company, dept) in enumerate(rows('市政公司员工信息表.xlsx', 'CD'), 1):
        candidates = (f'{company}-{dept}', dept)
        target = next((name for name in candidates if name in hierarchy), None)
        if target is None:
            raise ValueError(f'无法匹配员工表第 {number + 1} 行的组织')
        accounts[target].append(number)
        roster['teacher' if company == '总公司' else 'student'].append(number)
    if len(hierarchy) != 111 or len(roster['teacher']) != 190 or len(roster['student']) != 9_810:
        raise ValueError('规模演示应包含 111 个组织节点、190 名教师及 9,810 名学生')

    statements = ['SET NAMES utf8mb4;', 'USE rbac;', 'START TRANSACTION;']

    def department(name, parent, variable):
        predicate = f'name = {literal(name)} AND parent_id <=> {parent}'
        statements.append(
            f'INSERT INTO department (name, parent_id) SELECT {literal(name)}, {parent} '
            f'WHERE NOT EXISTS (SELECT 1 FROM department WHERE {predicate});'
        )
        statements.append(f'SET {variable} = (SELECT id FROM department WHERE {predicate});')

    department(ROOT, 'NULL', '@org_root')
    queue = [(name, '@org_root') for name in children['']]
    for index, (name, parent) in enumerate(queue, 1):
        variable = f'@org_{index}'
        department(name, parent, variable)
        queue.extend((child, variable) for child in children[name])
        if accounts[name]:
            values = ',\n'.join(
                f"('{PREFIX}{n:05}', {literal(f'演示员工 {n:05}')}, '{19900000000+n}', {variable})"
                for n in accounts[name]
            )
            statements.append(
                'INSERT INTO app_user (username, name, phone, department_id) VALUES\n'
                + values + '\nON DUPLICATE KEY UPDATE id = app_user.id;'
            )
    if len(queue) != len(hierarchy):
        raise ValueError('部门表存在无法从根节点到达的记录')
    statements.append('''CREATE TEMPORARY TABLE demo_course_roster (
  username VARCHAR(64) PRIMARY KEY,
  role_name VARCHAR(10) NOT NULL
) COLLATE utf8mb4_0900_as_cs;''')
    for role, numbers in roster.items():
        values = ',\n'.join(f"('{PREFIX}{n:05}', '{role}')" for n in numbers)
        statements.append('INSERT INTO demo_course_roster (username, role_name) VALUES\n' + values + ';')
    statements.extend([ROSTER_SQL.read_text(encoding='utf-8').rstrip(), 'COMMIT;'])
    destination.write_text('\n'.join(statements) + '\n', encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    build(parser.parse_args().output)
